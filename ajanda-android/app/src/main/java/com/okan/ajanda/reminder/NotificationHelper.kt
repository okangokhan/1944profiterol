package com.okan.ajanda.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.okan.ajanda.MainActivity
import com.okan.ajanda.R
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.TaskEntity
import com.okan.ajanda.domain.DateText

object NotificationHelper {
    private const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Görev son tarih hatırlatıcıları" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReminder(context: Context, task: TaskEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, task.id.toInt(),
            MainActivity.openTaskIntent(context, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val complete = PendingIntent.getBroadcast(
            context, task.id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_COMPLETE)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val due = DateText.due(task.dueDate, task.dueTime) ?: ""
        val text = listOf(due, task.notes.take(80)).filter { it.isNotBlank() }.joinToString(" · ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(task.title)
            .setContentText(text)
            .setPriority(if (task.priority >= Priority.HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_task, "Tamamlandı", complete)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }
}
