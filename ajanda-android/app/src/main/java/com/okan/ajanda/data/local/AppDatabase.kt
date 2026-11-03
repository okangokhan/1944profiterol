package com.okan.ajanda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, SubtaskEntity::class, TagEntity::class, TaskTagCrossRef::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao

    companion object {
        /** İlk kurulumda gelen varsayılan etiketler. */
        val DEFAULT_TAGS = listOf(
            "Okul" to 0xFF2F4FD8.toInt(),
            "LGS Koçluk" to 0xFF12908E.toInt(),
            "Doktora" to 0xFF8A55D4.toInt(),
            "1944" to 0xFFD2691E.toInt(),
            "Kişisel & Aile" to 0xFF2E8B57.toInt(),
            "Diğer" to 0xFF6B7280.toInt(),
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ajanda.db")
                .addMigrations(MIGRATION_1_2)
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
