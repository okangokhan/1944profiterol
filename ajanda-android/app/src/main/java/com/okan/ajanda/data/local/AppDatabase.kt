package com.okan.ajanda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, SubtaskEntity::class, TagEntity::class, TaskTagCrossRef::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao

    companion object {
        /** İlk kurulumda gelen varsayılan etiketler. */
        val DEFAULT_TAGS = listOf(
            "İş" to 0xFF4A6FA5.toInt(),
            "Kişisel" to 0xFF5B8C5A.toInt(),
            "Öncelikli" to 0xFFC8553D.toInt(),
        )

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ajanda.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_TAGS.forEach { (name, color) ->
                            db.execSQL("INSERT INTO tags(name, color) VALUES(?, ?)", arrayOf<Any>(name, color))
                        }
                    }
                })
                .build()
    }
}
