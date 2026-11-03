package com.okan.ajanda.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun dateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun timeToInt(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun intToTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    // Öncelik sayısal tutulur ki SQL ORDER BY doğru sıralasın.
    @TypeConverter fun priorityToInt(value: Priority): Int = value.level
    @TypeConverter fun intToPriority(value: Int): Priority = Priority.fromLevel(value)
}
