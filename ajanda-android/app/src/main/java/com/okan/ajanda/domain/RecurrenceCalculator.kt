package com.okan.ajanda.domain

import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object RecurrenceCalculator {

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun nth(base: LocalDate, recurrence: Recurrence, interval: Int, k: Long): LocalDate {
        val step = interval.coerceAtLeast(1) * k
        return when (recurrence) {
            Recurrence.NONE -> base
            Recurrence.DAILY -> base.plusDays(step)
            Recurrence.WEEKLY -> base.plusWeeks(step)
            Recurrence.MONTHLY -> base.plusMonths(step)
            Recurrence.YEARLY -> base.plusYears(step)
        }
    }

    private fun usesDayMask(task: TaskEntity) = task.recurrence == Recurrence.WEEKLY && task.recurrenceDays != 0

    /** Haftalık + gün seçimi: [d] günü, [base]'in haftasından itibaren her N haftada bir seçili günlerden biri mi? */
    fun matchesWeekly(base: LocalDate, d: LocalDate, interval: Int, mask: Int): Boolean {
        if (d < base) return false
        val bw = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dw = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ChronoUnit.WEEKS.between(bw, dw)
        return weeks % interval.coerceAtLeast(1) == 0L && (mask and bit(d.dayOfWeek)) != 0
    }

    /** [date]'ten sonraki ilk tekrar. */
    fun next(date: LocalDate, task: TaskEntity): LocalDate {
        if (usesDayMask(task)) {
            val base = task.dueDate ?: date
            var d = date.plusDays(1)
            repeat(7 * (task.recurrenceInterval.coerceAtLeast(1) + 1)) {
                if (matchesWeekly(base, d, task.recurrenceInterval, task.recurrenceDays)) return d
                d = d.plusDays(1)
            }
            return date.plusWeeks(task.recurrenceInterval.coerceAtLeast(1).toLong())
        }
        return nth(date, task.recurrence, task.recurrenceInterval, 1)
    }

    /** [date] veya sonrasındaki ilk geçerli gün (gün seçimine uymayan başlangıç tarihini düzeltmek için). */
    fun firstOnOrAfter(date: LocalDate, task: TaskEntity): LocalDate {
        if (!usesDayMask(task)) return date
        if ((task.recurrenceDays and bit(date.dayOfWeek)) != 0) return date
        var d = date
        repeat(7) {
            d = d.plusDays(1)
            if ((task.recurrenceDays and bit(d.dayOfWeek)) != 0) return d
        }
        return date
    }

    fun occurrencesBetween(task: TaskEntity, start: LocalDate, end: LocalDate): List<LocalDate> {
        val base = task.dueDate ?: return emptyList()
        if (task.recurrence == Recurrence.NONE) {
            return if (base in start..end) listOf(base) else emptyList()
        }
        val limit = task.recurrenceEndDate?.let { minOf(it, end) } ?: end
        val result = ArrayList<LocalDate>()
        if (usesDayMask(task)) {
            var d = maxOf(base, start)
            while (d <= limit) {
                if (d == base || matchesWeekly(base, d, task.recurrenceInterval, task.recurrenceDays)) result += d
                d = d.plusDays(1)
            }
            return result
        }
        var k = if (task.recurrence == Recurrence.DAILY && base < start) {
            (start.toEpochDay() - base.toEpochDay()) / task.recurrenceInterval.coerceAtLeast(1)
        } else 0L
        var guard = 0
        while (guard++ < 2000) {
            val d = nth(base, task.recurrence, task.recurrenceInterval, k)
            if (d > limit) break
            if (d >= start) result += d
            k++
        }
        return result
    }
}
