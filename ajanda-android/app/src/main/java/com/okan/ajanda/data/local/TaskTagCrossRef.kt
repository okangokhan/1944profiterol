package com.okan.ajanda.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Görev <-> Etiket çoka-çok ilişki tablosu. */
@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")]
)
data class TaskTagCrossRef(
    val taskId: Long,
    val tagId: Long,
)
