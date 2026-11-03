package com.okan.ajanda.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Akıllı etiket: isim + ARGB renk. */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
)
