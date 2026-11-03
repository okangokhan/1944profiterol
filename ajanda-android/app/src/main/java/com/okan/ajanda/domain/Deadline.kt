package com.okan.ajanda.domain

import com.okan.ajanda.data.local.TaskEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

enum class Urgency { CALM, SOON, URGENT, OVERDUE }

/** Eriyen çubuk bilgisi: [fraction] = kalan süre / 7 gün (1 → bir hafta+ var, 0 → süre doldu). */
data class DeadlineInfo(
    val deadline: LocalDateTime,
    val fraction: Float,
    val label: String,
    val urgency: Urgency,
)

object Deadline {
    /** Sabit ölçek: çubuk bir haftalık zaman çizelgesi gibi erir; tüm görevler aynı ölçekte karşılaştırılır. */
    val SCALE: Duration = Duration.ofDays(7)

    fun deadlineOf(task: TaskEntity): LocalDateTime? =
        task.dueDate?.let { LocalDateTime.of(it, task.dueTime ?: LocalTime.of(23, 59)) }

    fun info(task: TaskEntity, now: LocalDateTime = LocalDateTime.now()): DeadlineInfo? {
        val end = deadlineOf(task) ?: return null
        val remaining = Duration.between(now, end)
        val fraction = (remaining.toMinutes().toFloat() / SCALE.toMinutes()).coerceIn(0f, 1f)
        val urgency = when {
            remaining.isNegative -> Urgency.OVERDUE
            remaining < Duration.ofHours(24) -> Urgency.URGENT
            remaining < Duration.ofHours(72) -> Urgency.SOON
            else -> Urgency.CALM
        }
        return DeadlineInfo(end, fraction, label(remaining), urgency)
    }

    fun label(remaining: Duration): String {
        if (remaining.isNegative) {
            val late = remaining.negated()
            return if (late.toDays() >= 1) "${late.toDays()} gün gecikti" else "${late.toHours().coerceAtLeast(1)} sa gecikti"
        }
        val days = remaining.toDays()
        val hours = remaining.toHours() % 24
        return when {
            days >= 7 -> "$days gün"
            days >= 1 -> if (hours > 0) "${days}g ${hours}sa" else "$days gün"
            remaining.toHours() >= 1 -> "${remaining.toHours()} sa"
            else -> "${remaining.toMinutes().coerceAtLeast(1)} dk"
        }
    }
}
