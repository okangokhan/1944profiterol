package com.okan.ajanda.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.local.TagEntity
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.data.repository.TaskRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TaskFilter(val label: String) {
    ALL("Tümü"), TODAY("Bugün"), WEEK("Bu Hafta"), OVERDUE("Gecikmiş"), DONE("Tamamlanan")
}

data class TaskSection(val key: String, val title: String, val items: List<TaskWithDetails>)

data class TaskListUiState(
    val loading: Boolean = true,
    val filter: TaskFilter = TaskFilter.ALL,
    val tagFilter: Long? = null,
    val tags: List<TagEntity> = emptyList(),
    val sections: List<TaskSection> = emptyList(),
    val openCount: Int = 0,
    val todayCount: Int = 0,
)

sealed interface TaskListEvent {
    data class Deleted(val title: String) : TaskListEvent
}

class TaskListViewModel(private val repo: TaskRepository) : ViewModel() {
    private val filter = MutableStateFlow(TaskFilter.ALL)
    private val tagFilter = MutableStateFlow<Long?>(null)
    private var lastDeleted: TaskWithDetails? = null

    private val _events = Channel<TaskListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<TaskListUiState> =
        combine(repo.observeTasks(), repo.observeTags(), filter, tagFilter) { tasks, tags, f, tag ->
            val today = LocalDate.now()
            val byTag = if (tag == null) tasks else tasks.filter { t -> t.tags.any { it.id == tag } }
            val filtered = byTag.filter { d ->
                val t = d.task
                when (f) {
                    TaskFilter.ALL -> true
                    TaskFilter.TODAY -> !t.isCompleted && t.dueDate != null && t.dueDate <= today
                    TaskFilter.WEEK -> !t.isCompleted && t.dueDate != null && t.dueDate <= today.plusDays(6)
                    TaskFilter.OVERDUE -> !t.isCompleted && t.dueDate != null && t.dueDate < today
                    TaskFilter.DONE -> t.isCompleted
                }
            }
            TaskListUiState(
                loading = false,
                filter = f,
                tagFilter = tag,
                tags = tags,
                sections = group(filtered, today),
                openCount = tasks.count { !it.task.isCompleted },
                todayCount = tasks.count { !it.task.isCompleted && it.task.dueDate == today },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    private fun group(list: List<TaskWithDetails>, today: LocalDate): List<TaskSection> {
        val open = list.filter { !it.task.isCompleted }
        val done = list.filter { it.task.isCompleted }.sortedByDescending { it.task.completedAt }.take(100)
        val buckets = linkedMapOf(
            "overdue" to "Gecikmiş", "today" to "Bugün", "tomorrow" to "Yarın",
            "week" to "Bu Hafta", "later" to "Daha Sonra", "nodate" to "Tarihsiz",
        )
        val grouped = open.groupBy { d ->
            val date = d.task.dueDate
            when {
                date == null -> "nodate"
                date < today -> "overdue"
                date == today -> "today"
                date == today.plusDays(1) -> "tomorrow"
                date <= today.plusDays(6) -> "week"
                else -> "later"
            }
        }
        val sections = buckets.mapNotNull { (key, title) ->
            grouped[key]?.takeIf { it.isNotEmpty() }?.let { TaskSection(key, title, it) }
        }
        return if (done.isEmpty()) sections else sections + TaskSection("done", "Tamamlanan", done)
    }

    fun setFilter(f: TaskFilter) { filter.value = f }
    fun setTagFilter(id: Long?) { tagFilter.value = if (tagFilter.value == id) null else id }

    fun toggle(id: Long) = viewModelScope.launch { repo.toggleComplete(id) }

    fun delete(id: Long) = viewModelScope.launch {
        repo.deleteTask(id)?.let {
            lastDeleted = it
            _events.send(TaskListEvent.Deleted(it.task.title))
        }
    }

    fun undoDelete() = viewModelScope.launch {
        lastDeleted?.let { repo.restore(it) }
        lastDeleted = null
    }
}
