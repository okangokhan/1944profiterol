package com.okan.ajanda.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.data.repository.TaskRepository
import com.okan.ajanda.domain.VoiceCommandParser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TodayUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val overdue: List<TaskWithDetails> = emptyList(),
    val todayItems: List<TaskWithDetails> = emptyList(),
    val undated: List<TaskWithDetails> = emptyList(),
    val upcomingWeek: Int = 0,
) {
    val todayDone: Int get() = todayItems.count { it.task.isCompleted }
}

class TodayViewModel(private val repo: TaskRepository) : ViewModel() {
    private var lastDeleted: TaskWithDetails? = null
    private val _deleted = Channel<String>(Channel.BUFFERED)
    val deletedEvents = _deleted.receiveAsFlow()

    val state: StateFlow<TodayUiState> = repo.observeTasks().map { all ->
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun doneToday(t: TaskWithDetails) = t.task.completedAt?.atZone(zone)?.toLocalDate() == today
        TodayUiState(
            loading = false,
            today = today,
            overdue = all.filter { d ->
                val due = d.task.dueDate
                due != null && due < today && (!d.task.isCompleted || doneToday(d))
            }.sortedWith(compareBy({ it.task.isCompleted }, { it.task.dueDate })),
            todayItems = all.filter { it.task.dueDate == today }
                .sortedWith(compareBy({ it.task.isCompleted }, { it.task.dueTime ?: java.time.LocalTime.MAX }, { -it.task.priority.level })),
            undated = all.filter { it.task.dueDate == null && (!it.task.isCompleted || doneToday(it)) },
            upcomingWeek = all.count { d -> val due = d.task.dueDate; !d.task.isCompleted && due != null && due > today && due <= today.plusDays(6) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** "Yaz ve ekle": akıllı ayrıştırıcıdan geçer, tarih yoksa bugüne eklenir. */
    fun add(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        repo.addParsed(VoiceCommandParser.parse(text), LocalDate.now())
    }

    fun toggle(id: Long) = viewModelScope.launch { repo.toggleComplete(id) }

    fun delete(id: Long) = viewModelScope.launch {
        repo.deleteTask(id)?.let { lastDeleted = it; _deleted.send(it.task.title) }
    }

    fun undoDelete() = viewModelScope.launch {
        lastDeleted?.let { repo.restore(it) }
        lastDeleted = null
    }
}
