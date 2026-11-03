package com.okan.ajanda.domain

import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.TaskEntity
import java.time.LocalDate

object RecurrenceCalculator {

    /** [base] tarihinden itibaren k'inci tekrarın tarihi (ay sonu kaymasını önlemek için hep base'den hesaplanır). */
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

    fun next(date: LocalDate, recurrence: Recurrence, interval: Int): LocalDate =
        nth(date, recurrence, interval, 1)

    /** Görevin [start, end] aralığına düşen tüm tekrar tarihleri. */
    fun occurrencesBetween(task: TaskEntity, start: LocalDate, end: LocalDate): List<LocalDate> {
        val base = task.dueDate ?: return emptyList()
        if (task.recurrence == Recurrence.NONE) {
            return if (base in start..end) listOf(base) else emptyList()
        }
        val limit = task.recurrenceEndDate?.let { minOf(it, end) } ?: end
        val result = ArrayList<LocalDate>()
        // Günlük tekrarda başlangıca hızlıca atla
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
