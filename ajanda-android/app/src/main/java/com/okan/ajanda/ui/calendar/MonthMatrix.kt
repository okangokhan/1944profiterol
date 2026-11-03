package com.okan.ajanda.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Ajanda matrisi: ayın ilk haftasının başından son haftanın sonuna kadar tüm günler (4-6 satır x 7 sütun). */
object MonthMatrix {
    fun firstDayOfWeek(mondayFirst: Boolean): DayOfWeek = if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    fun weekdays(mondayFirst: Boolean): List<DayOfWeek> {
        val first = firstDayOfWeek(mondayFirst)
        return (0L..6L).map { first.plus(it) }
    }

    fun days(month: YearMonth, mondayFirst: Boolean): List<LocalDate> {
        val first = month.atDay(1)
        val offset = (first.dayOfWeek.value - firstDayOfWeek(mondayFirst).value + 7) % 7
        val start = first.minusDays(offset.toLong())
        val weeks = (offset + month.lengthOfMonth() + 6) / 7
        return (0 until weeks * 7).map { start.plusDays(it.toLong()) }
    }
}
