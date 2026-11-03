package com.okan.ajanda.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.SubtaskEntity
import com.okan.ajanda.data.local.TagEntity
import com.okan.ajanda.data.local.TaskEntity
import com.okan.ajanda.data.repository.TaskRepository
import com.okan.ajanda.domain.RecurrenceCalculator
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
    val recurrenceDays: Int = 0,
    val tagIds: Set<Long> = emptySet(),
    val subtasks: List<SubtaskDraft> = emptyList(),
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val titleError: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
}

class TaskEditViewModel(private val repo: TaskRepository) : ViewModel() {
    private var keySeq = 1L
    private var requested = false

    var state by mutableStateOf(EditState())
        private set

    val tags: StateFlow<List<TagEntity>> =
        repo.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Düzenleme sayfası açıldığında bir kez çağrılır. */
    fun load(taskId: Long, date: LocalDate?) {
        if (requested) return
        requested = true
        viewModelScope.launch {
            state = if (taskId != 0L) {
                repo.getTask(taskId)?.let { d ->
                    val t = d.task
                    EditState(
                        loaded = true, id = t.id, title = t.title, notes = t.notes, priority = t.priority,
                        dueDate = t.dueDate, dueTime = t.dueTime, reminder = t.reminderMinutesBefore,
                        recurrence = t.recurrence, recurrenceInterval = t.recurrenceInterval,
                        recurrenceEnd = t.recurrenceEndDate, recurrenceDays = t.recurrenceDays,
                        tagIds = d.tags.map { it.id }.toSet(),
                        subtasks = d.sortedSubtasks.map { SubtaskDraft(keySeq++, it.title, it.isDone) },
                        isCompleted = t.isCompleted, completedAt = t.completedAt, createdAt = t.createdAt,
                    )
                } ?: EditState(loaded = true)
            } else {
                EditState(loaded = true, dueDate = date)
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
    fun setRecurrence(r: Recurrence) = update {
        val date = dueDate ?: if (r != Recurrence.NONE) LocalDate.now() else null
        copy(
            recurrence = r, dueDate = date,
            recurrenceDays = if (r == Recurrence.WEEKLY && recurrenceDays == 0 && date != null) RecurrenceCalculator.bit(date.dayOfWeek) else recurrenceDays,
            recurrenceEnd = if (r == Recurrence.NONE) null else recurrenceEnd,
        )
    }
    fun toggleWeekday(day: java.time.DayOfWeek) = update {
        val bit = RecurrenceCalculator.bit(day)
        val next = recurrenceDays xor bit
        copy(recurrenceDays = if (next == 0) recurrenceDays else next)
    }
    fun setPriorityFlag(on: Boolean) = update {
        copy(priority = if (on) (if (priority >= Priority.HIGH) priority else Priority.HIGH) else Priority.MEDIUM)
    }
    /** Tek etiket seçimi (aynısına dokununca kaldırılır). */
    fun selectTag(id: Long) = update { copy(tagIds = if (id in tagIds) emptySet() else setOf(id)) }
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
        if (id > 0) update { copy(tagIds = setOf(id)) }
    }

    fun save(onSaved: () -> Unit) {
        val s = state
        if (s.title.isBlank()) { update { copy(titleError = true) }; return }
        viewModelScope.launch {
            var task = TaskEntity(
                id = s.id, title = s.title.trim(), notes = s.notes.trim(), priority = s.priority,
                dueDate = s.dueDate,
                recurrenceDays = if (s.recurrence == Recurrence.WEEKLY) s.recurrenceDays else 0, dueTime = s.dueTime, reminderMinutesBefore = s.reminder,
                recurrence = s.recurrence, recurrenceInterval = s.recurrenceInterval.coerceAtLeast(1),
                recurrenceEndDate = s.recurrenceEnd, isCompleted = s.isCompleted, completedAt = s.completedAt,
                createdAt = s.createdAt,
            )
            // Haftalık gün seçimine uymayan başlangıç tarihini ilk uygun güne kaydır
            task.dueDate?.let { task = task.copy(dueDate = RecurrenceCalculator.firstOnOrAfter(it, task)) }
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
