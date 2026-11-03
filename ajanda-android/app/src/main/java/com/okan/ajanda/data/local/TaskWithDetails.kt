package com.okan.ajanda.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import java.time.LocalDate

/** Görev + alt görevleri + etiketleri. */
data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subtasks: List<SubtaskEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TaskTagCrossRef::class, parentColumn = "taskId", entityColumn = "tagId")
    )
    val tags: List<TagEntity>,
) {
    val sortedSubtasks: List<SubtaskEntity> get() = subtasks.sortedBy { it.position }
    val primaryColor: Int? get() = tags.firstOrNull()?.color
}

/**
 * Takvimde bir günde görünen görev. Tekrarlanan görevlerin gelecekteki
 * tekrarları [isVirtual] = true olarak (veritabanına yazılmadan) hesaplanır.
 */
data class TaskOccurrence(
    val details: TaskWithDetails,
    val date: LocalDate,
    val isVirtual: Boolean,
) {
    val task: TaskEntity get() = details.task
}
