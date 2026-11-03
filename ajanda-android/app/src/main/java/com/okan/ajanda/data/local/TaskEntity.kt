package com.okan.ajanda.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class Priority(val level: Int, val label: String) {
    LOW(0, "Düşük"),
    MEDIUM(1, "Normal"),
    HIGH(2, "Yüksek"),
    URGENT(3, "Acil");

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}

enum class Recurrence(val label: String, val unitLabel: String) {
    NONE("Tekrar yok", ""),
    DAILY("Her gün", "gün"),
    WEEKLY("Her hafta", "hafta"),
    MONTHLY("Her ay", "ay"),
    YEARLY("Her yıl", "yıl"),
}

/**
 * Bir görev / not. Deadline [dueDate] + isteğe bağlı [dueTime] ile tutulur.
 * [reminderMinutesBefore] null ise hatırlatıcı yoktur; 0 ise tam vaktinde.
 * Tekrarlanan görevlerde [recurrence] + [recurrenceInterval] ("her 2 haftada bir") kullanılır.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("dueDate"), Index("isCompleted")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val reminderMinutesBefore: Int? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val recurrenceInterval: Int = 1,
    val recurrenceEndDate: LocalDate? = null,
    /** Haftalık tekrarda seçili günler: bit0 = Pazartesi … bit6 = Pazar. 0 = başlangıç günü. */
    @ColumnInfo(defaultValue = "0") val recurrenceDays: Int = 0,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)
