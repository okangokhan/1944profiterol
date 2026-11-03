package com.okan.ajanda.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val TR: Locale = Locale.forLanguageTag("tr-TR")

object DateText {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", TR)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", TR)
    private val full = DateTimeFormatter.ofPattern("d MMMM, EEEE", TR)
    private val time = DateTimeFormatter.ofPattern("HH:mm", TR)

    fun time(t: LocalTime): String = time.format(t)
    fun full(d: LocalDate): String = full.format(d)

    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Bugün"
        today.plusDays(1) -> "Yarın"
        today.minusDays(1) -> "Dün"
        else -> if (date.year == today.year) dayMonth.format(date) else dayMonthYear.format(date)
    }

    fun due(date: LocalDate?, t: LocalTime?, today: LocalDate = LocalDate.now()): String? {
        if (date == null) return null
        return if (t != null) "${relative(date, today)} ${time(t)}" else relative(date, today)
    }

    fun monthName(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, TR).replaceFirstChar { it.titlecase(TR) }

    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, TR).uppercase(TR)

    fun weekdayLong(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, TR).replaceFirstChar { it.titlecase(TR) }

    fun reminderLabel(minutes: Int?): String = when (minutes) {
        null -> "Yok"
        0 -> "Zamanında"
        in 1..59 -> "$minutes dk önce"
        in 60..1439 -> "${minutes / 60} saat önce"
        else -> "${minutes / 1440} gün önce"
    }
}
