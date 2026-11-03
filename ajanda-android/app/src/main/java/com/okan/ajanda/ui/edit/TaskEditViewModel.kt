package com.okan.ajanda.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.SubtaskEntity
import com.okan.ajanda.data.local.TagEntity
import com.okan.ajanda.data.local.TaskEntity
import com.okan.ajanda.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class SubtaskDraft(val key: Long, val title: String, val done: Boolean)

data class EditState(
    val loaded: Boolean = false,
    val id: Long = 0,
    val title: String = "",
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val reminder: Int? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val recurrenceInterval: Int = 1,
    val recurrenceEnd: LocalDate? = null,
    val tagIds: Set<Long> = emptySet(),
    val subtasks: List<SubtaskDraft> = emptyList(),
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val titleError: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
}

class TaskEditViewModel(handle: SavedStateHandle, private val repo: TaskRepository) : ViewModel() {
    private val taskId: Long = handle.get<Long>("taskId") ?: 0L
    private val initialDate: Long = handle.get<Long>("date") ?: Long.MIN_VALUE
    private var keySeq = 1L

    var state by mutableStateOf(EditState())
        private set

    val tags: StateFlow<List<TagEntity>> =
        repo.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            state = if (taskId != 0L) {
                repo.getTask(taskId)?.let { d ->
                    val t = d.task
                    EditState(
                        loaded = true, id = t.id, title = t.title, notes = t.notes, priority = t.priority,
                        dueDate = t.dueDate, dueTime = t.dueTime, reminder = t.reminderMinutesBefore,
                        recurrence = t.recurrence, recurrenceInterval = t.recurrenceInterval,
                        recurrenceEnd = t.recurrenceEndDate, tagIds = d.tags.map { it.id }.toSet(),
                        subtasks = d.sortedSubtasks.map { SubtaskDraft(keySeq++, it.title, it.isDone) },
                        isCompleted = t.isCompleted, completedAt = t.completedAt, createdAt = t.createdAt,
                    )
                } ?: EditState(loaded = true)
            } else {
                EditState(
                    loaded = true,
                    dueDate = if (initialDate != Long.MIN_VALUE) LocalDate.ofEpochDay(initialDate) else null,
                )
            }
        }
    }

    fun update(transform: EditState.() -> EditState) { state = state.transform() }

    fun setTitle(v: String) = update { copy(title = v, titleError = false) }
    fun setDate(d: LocalDate?) = update {
        copy(dueDate = d, dueTime = if (d == null) null else dueTime, reminder = if (d == null) null else reminder,
            recurrence = if (d == null) Recurrence.NONE else recurrence)
    }
    fun setTime(t: LocalTime?) = update { copy(dueTime = t, dueDate = dueDate ?: if (t != null) LocalDate.now() else null) }
    fun setReminder(m: Int?) = update { copy(reminder = m, dueDate = dueDate ?: if (m != null) LocalDate.now() else null) }
    fun setRecurrence(r: Recurrence) = update { copy(recurrence = r, dueDate = dueDate ?: if (r != Recurrence.NONE) LocalDate.now() else null) }
    fun toggleTag(id: Long) = update { copy(tagIds = if (id in tagIds) tagIds - id else tagIds + id) }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        update { copy(subtasks = subtasks + SubtaskDraft(keySeq++, title.trim(), false)) }
    }
    fun editSubtask(key: Long, title: String) = update { copy(subtasks = subtasks.map { if (it.key == key) it.copy(title = title) else it }) }
    fun toggleSubtask(key: Long) = update { copy(subtasks = subtasks.map { if (it.key == key) it.copy(done = !it.done) else it }) }
    fun removeSubtask(key: Long) = update { copy(subtasks = subtasks.filterNot { it.key == key }) }

    fun createTag(name: String, color: Int) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = repo.createTag(name, color)
        if (id > 0) update { copy(tagIds = tagIds + id) }
    }

    fun save(onSaved: () -> Unit) {
        val s = state
        if (s.title.isBlank()) { update { copy(titleError = true) }; return }
        viewModelScope.launch {
            val task = TaskEntity(
                id = s.id, title = s.title.trim(), notes = s.notes.trim(), priority = s.priority,
                dueDate = s.dueDate, dueTime = s.dueTime, reminderMinutesBefore = s.reminder,
                recurrence = s.recurrence, recurrenceInterval = s.recurrenceInterval.coerceAtLeast(1),
                recurrenceEndDate = s.recurrenceEnd, isCompleted = s.isCompleted, completedAt = s.completedAt,
                createdAt = s.createdAt,
            )
            repo.saveTask(
                task,
                s.subtasks.filter { it.title.isNotBlank() }.map { SubtaskEntity(title = it.title.trim(), isDone = it.done) },
                s.tagIds
            )
            onSaved()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (state.id != 0L) repo.deleteTask(state.id)
            onDone()
        }
    }
}
