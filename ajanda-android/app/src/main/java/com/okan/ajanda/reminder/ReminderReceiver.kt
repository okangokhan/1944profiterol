package com.okan.ajanda.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.okan.ajanda.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = context.container.taskRepository
                when (intent.action) {
                    ACTION_REMIND -> repo.getTask(id)?.let { d ->
                        if (!d.task.isCompleted) NotificationHelper.showReminder(context, d.task)
                    }
                    ACTION_COMPLETE -> {
                        repo.complete(id)
                        NotificationHelper.cancel(context, id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.okan.ajanda.REMIND"
        const val ACTION_COMPLETE = "com.okan.ajanda.COMPLETE"
        const val EXTRA_TASK_ID = "taskId"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                context.container.taskRepository.rescheduleAllReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
