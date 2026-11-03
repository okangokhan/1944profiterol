package com.okan.ajanda.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
abstract class TaskDao {

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        ORDER BY isCompleted ASC,
                 CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC,
                 CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END, dueTime ASC,
                 priority DESC, createdAt DESC
        """
    )
    abstract fun observeAll(): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end ORDER BY dueDate, isCompleted, dueTime, priority DESC")
    abstract fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<TaskWithDetails>>

    /** Bitmemiş, tarihli ve tekrarlayan görevler (takvimde sanal tekrarları üretmek için). */
    @Transaction
    @Query("SELECT * FROM tasks WHERE recurrence != 'NONE' AND isCompleted = 0 AND dueDate IS NOT NULL AND dueDate <= :end")
    abstract fun observeRecurring(end: LocalDate): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDate < :today ORDER BY dueDate, dueTime")
    abstract fun observeOverdue(today: LocalDate): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun getById(id: Long): TaskWithDetails?

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND reminderMinutesBefore IS NOT NULL AND dueDate IS NOT NULL")
    abstract suspend fun getWithReminders(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE recurrence != 'NONE' AND isCompleted = 0 AND dueDate < :today")
    abstract suspend fun getRecurringBefore(today: LocalDate): List<TaskEntity>

    @Update
    abstract suspend fun updateTask(task: TaskEntity)

    @Upsert
    abstract suspend fun upsertTask(task: TaskEntity): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    abstract suspend fun deleteTask(id: Long)

    @Query("UPDATE tasks SET isCompleted = :done, completedAt = :at WHERE id = :id")
    abstract suspend fun setCompleted(id: Long, done: Boolean, at: Instant?)

    @Insert
    abstract suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    abstract suspend fun deleteSubtasks(taskId: Long)

    @Query("UPDATE subtasks SET isDone = :done WHERE id = :id")
    abstract suspend fun setSubtaskDone(id: Long, done: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCrossRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    abstract suspend fun deleteCrossRefs(taskId: Long)

    /** Görevi, alt görevlerini ve etiket bağlantılarını tek transaction'da kaydeder. */
    @Transaction
    open suspend fun saveFull(task: TaskEntity, subtasks: List<SubtaskEntity>, tagIds: Collection<Long>): Long {
        val result = upsertTask(task)
        val id = if (result == -1L) task.id else result
        deleteSubtasks(id)
        insertSubtasks(subtasks.mapIndexed { index, s -> s.copy(id = 0, taskId = id, position = index) })
        deleteCrossRefs(id)
        insertCrossRefs(tagIds.map { TaskTagCrossRef(id, it) })
        return id
    }
}
