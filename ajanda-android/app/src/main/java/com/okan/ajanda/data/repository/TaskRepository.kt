package com.okan.ajanda.data.repository

import android.content.Context
import com.okan.ajanda.data.local.AppDatabase
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.SubtaskEntity
import com.okan.ajanda.data.local.TagEntity
import com.okan.ajanda.data.local.TaskEntity
import com.okan.ajanda.data.local.TaskOccurrence
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.domain.ParsedCommand
import com.okan.ajanda.domain.RecurrenceCalculator
import com.okan.ajanda.reminder.ReminderScheduler
import com.okan.ajanda.ui.theme.TagPalette
import com.okan.ajanda.widget.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate

data class WeekData(
    val today: LocalDate,
    val overdue: List<TaskWithDetails>,
    val days: List<Pair<LocalDate, List<TaskOccurrence>>>,
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && days.all { it.second.isEmpty() }
}

/** Tek veri kaynağı (offline-first). Tüm yazma işlemleri hatırlatıcıları ve widget'ları da günceller. */
class TaskRepository(
    private val context: Context,
    db: AppDatabase,
    private val scheduler: ReminderScheduler,
) {
    private val taskDao = db.taskDao()
    private val tagDao = db.tagDao()

    fun observeTasks(): Flow<List<TaskWithDetails>> = taskDao.observeAll()
    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()
    suspend fun getTask(id: Long): TaskWithDetails? = taskDao.getById(id)

    /** Aralıktaki görevler + tekrarlayan görevlerin sanal tekrarları. */
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<TaskOccurrence>> =
        combine(taskDao.observeBetween(start, end), taskDao.observeRecurring(end)) { direct, recurring ->
            val real = direct.mapNotNull { d -> d.task.dueDate?.let { TaskOccurrence(d, it, false) } }
            val virtual = recurring.flatMap { d ->
                RecurrenceCalculator.occurrencesBetween(d.task, start, end)
                    .filter { it != d.task.dueDate }
                    .map { TaskOccurrence(d, it, true) }
            }
            (real + virtual).sortedWith(
                compareBy<TaskOccurrence>({ it.date }, { it.task.isCompleted }, { it.task.dueTime }, { -it.task.priority.level })
            )
        }

    fun observeWeek(today: LocalDate = LocalDate.now()): Flow<WeekData> {
        val end = today.plusDays(6)
        return combine(taskDao.observeOverdue(today), observeRange(today, end)) { overdue, occ ->
            val grouped = occ.groupBy { it.date }
            WeekData(today, overdue, (0L..6L).map { today.plusDays(it) }.map { it to grouped[it].orEmpty() })
        }
    }

    suspend fun saveTask(task: TaskEntity, subtasks: List<SubtaskEntity>, tagIds: Collection<Long>): Long {
        val id = taskDao.saveFull(task, subtasks, tagIds)
        scheduler.schedule(task.copy(id = id))
        refreshWidgets()
        return id
    }

    suspend fun addParsed(cmd: ParsedCommand, fallbackDate: LocalDate?): Long {
        val existing = tagDao.getAll()
        val tagIds = cmd.tags.map { name ->
            existing.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: tagDao.insert(TagEntity(name = name, color = TagPalette.forName(name)))
        }.filter { it > 0 }
        val task = TaskEntity(
            title = cmd.title,
            priority = cmd.priority,
            dueDate = cmd.date ?: fallbackDate,
            dueTime = cmd.time,
            reminderMinutesBefore = if (cmd.reminder && (cmd.date ?: fallbackDate) != null) 0 else null,
            recurrence = cmd.recurrence,
        )
        return saveTask(task, emptyList(), tagIds)
    }

    suspend fun quickAdd(title: String, date: LocalDate?): Long =
        saveTask(TaskEntity(title = title.trim(), dueDate = date), emptyList(), emptyList())

    /** Tamamla / geri al. Tekrarlanan görev tamamlanınca bir sonraki tekrar otomatik oluşturulur. */
    suspend fun toggleComplete(id: Long) {
        val d = taskDao.getById(id) ?: return
        setCompleted(d, !d.task.isCompleted)
    }

    suspend fun complete(id: Long) {
        val d = taskDao.getById(id) ?: return
        if (!d.task.isCompleted) setCompleted(d, true)
    }

    private suspend fun setCompleted(d: TaskWithDetails, done: Boolean) {
        val task = d.task
        if (done) {
            scheduler.cancel(task.id)
            val due = task.dueDate
            if (task.recurrence != Recurrence.NONE && due != null) {
                // Tamamlanan kopya artık tekrar etmez; döngü yeni kayıtla devam eder.
                taskDao.saveFull(
                    task.copy(isCompleted = true, completedAt = Instant.now(), recurrence = Recurrence.NONE),
                    d.sortedSubtasks, d.tags.map { it.id }
                )
                var next = RecurrenceCalculator.next(due, task.recurrence, task.recurrenceInterval)
                // Gecikmiş döngüleri bugüne yetiştir
                val today = LocalDate.now()
                var guard = 0
                while (next < today && guard++ < 1000) next = RecurrenceCalculator.next(next, task.recurrence, task.recurrenceInterval)
                if (task.recurrenceEndDate == null || next <= task.recurrenceEndDate) {
                    val nextTask = task.copy(id = 0, dueDate = next, isCompleted = false, completedAt = null, createdAt = Instant.now())
                    val newId = taskDao.saveFull(nextTask, d.sortedSubtasks.map { it.copy(isDone = false) }, d.tags.map { it.id })
                    scheduler.schedule(nextTask.copy(id = newId))
                }
            } else {
                taskDao.setCompleted(task.id, true, Instant.now())
            }
        } else {
            taskDao.setCompleted(task.id, false, null)
            scheduler.schedule(task.copy(isCompleted = false))
        }
        refreshWidgets()
    }

    suspend fun setSubtaskDone(subtaskId: Long, done: Boolean) {
        taskDao.setSubtaskDone(subtaskId, done)
    }

    suspend fun deleteTask(id: Long): TaskWithDetails? {
        val d = taskDao.getById(id) ?: return null
        scheduler.cancel(id)
        taskDao.deleteTask(id)
        refreshWidgets()
        return d
    }

    suspend fun restore(d: TaskWithDetails) {
        saveTask(d.task, d.sortedSubtasks, d.tags.map { it.id })
    }

    suspend fun createTag(name: String, color: Int): Long = tagDao.insert(TagEntity(name = name.trim(), color = color))
    suspend fun updateTag(tag: TagEntity) { tagDao.update(tag); refreshWidgets() }
    suspend fun deleteTag(id: Long) { tagDao.delete(id); refreshWidgets() }

    suspend fun rescheduleAllReminders() {
        taskDao.getWithReminders().forEach { scheduler.schedule(it) }
    }

    private suspend fun refreshWidgets() = WidgetRefresher.refreshAll(context)
}
