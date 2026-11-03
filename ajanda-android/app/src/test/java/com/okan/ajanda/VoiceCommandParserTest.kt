package com.okan.ajanda

import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.TaskEntity
import com.okan.ajanda.domain.RecurrenceCalculator
import com.okan.ajanda.domain.VoiceCommandParser
import com.okan.ajanda.ui.calendar.MonthMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class VoiceCommandParserTest {
    private val today = LocalDate.of(2026, 11, 3) // Salı
    private val now = LocalTime.of(10, 0)

    @Test fun tomorrowWithTimeTagAndPriority() {
        val p = VoiceCommandParser.parse("yarın saat 15 müşteri toplantısı #iş acil", today, now)
        assertEquals("Müşteri toplantısı", p.title)
        assertEquals(today.plusDays(1), p.date)
        assertEquals(LocalTime.of(15, 0), p.time)
        assertEquals(Priority.URGENT, p.priority)
        assertEquals(listOf("iş"), p.tags)
        assertTrue(p.reminder)
    }

    @Test fun weeklyRecurrenceOnWeekdayEvening() {
        val p = VoiceCommandParser.parse("her pazartesi spor salonu akşam 7", today, now)
        assertEquals(Recurrence.WEEKLY, p.recurrence)
        assertEquals(LocalDate.of(2026, 11, 9), p.date)
        assertEquals(LocalTime.of(19, 0), p.time)
        assertEquals("Spor salonu", p.title)
    }

    @Test fun absoluteMonthName() {
        val p = VoiceCommandParser.parse("15 kasım fatura öde önemli", today, now)
        assertEquals(LocalDate.of(2026, 11, 15), p.date)
        assertEquals(Priority.HIGH, p.priority)
        assertEquals("Fatura öde", p.title)
    }

    @Test fun pastDateRollsToNextYear() {
        val p = VoiceCommandParser.parse("1 ocak yılbaşı", today, now)
        assertEquals(LocalDate.of(2027, 1, 1), p.date)
    }

    @Test fun relativeDaysAndWeekdaySuffix() {
        assertEquals(today.plusDays(3), VoiceCommandParser.parse("3 gün sonra rapor teslimi", today, now).date)
        val fri = VoiceCommandParser.parse("cumaya sunum hazırla", today, now)
        assertEquals(LocalDate.of(2026, 11, 6), fri.date)
        assertEquals("Sunum hazırla", fri.title)
        val sat = VoiceCommandParser.parse("cumartesi günü market", today, now)
        assertEquals(LocalDate.of(2026, 11, 7), sat.date)
        assertEquals("Market", sat.title)
    }

    @Test fun clockTimeOnlyMeansTodayOrTomorrow() {
        assertEquals(today, VoiceCommandParser.parse("14:30 doktor", today, now).date)
        assertEquals(today.plusDays(1), VoiceCommandParser.parse("08:00 koşu", today, now).date)
    }

    @Test fun plainTextStaysPlain() {
        val p = VoiceCommandParser.parse("süt al", today, now)
        assertEquals("Süt al", p.title)
        assertEquals(null, p.date)
        assertEquals(Priority.MEDIUM, p.priority)
    }

    @Test fun monthlyRecurrenceDoesNotDrift() {
        val t = TaskEntity(title = "kira", dueDate = LocalDate.of(2026, 1, 31), recurrence = Recurrence.MONTHLY)
        val occ = RecurrenceCalculator.occurrencesBetween(t, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30))
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), occ)
    }

    @Test fun monthMatrixSundayFirst() {
        val days = MonthMatrix.days(YearMonth.of(2026, 11), mondayFirst = false)
        assertEquals(DayOfWeek.SUNDAY, days.first().dayOfWeek)
        assertEquals(0, days.size % 7)
        assertTrue(days.contains(LocalDate.of(2026, 11, 30)))
    }
}
