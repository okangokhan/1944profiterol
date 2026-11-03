package com.okan.ajanda.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.okan.ajanda.data.local.TaskEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(task: TaskEntity) {
        cancel(task.id)
        val minutes = task.reminderMinutesBefore ?: return
        val date = task.dueDate ?: return
        if (task.isCompleted) return
        val at = LocalDateTime.of(date, task.dueTime ?: DEFAULT_TIME).minusMinutes(minutes.toLong())
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) return
        val pi = pendingIntent(task.id)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId))
    }

    private fun pendingIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
