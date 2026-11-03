package com.okan.ajanda

import android.app.Application
import android.content.Context
import com.okan.ajanda.data.local.AppDatabase
import com.okan.ajanda.data.prefs.SettingsRepository
import com.okan.ajanda.data.repository.TaskRepository
import com.okan.ajanda.reminder.NotificationHelper
import com.okan.ajanda.reminder.ReminderScheduler

class AjandaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
    }
}

/** Basit manuel bağımlılık enjeksiyonu. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val reminderScheduler by lazy { ReminderScheduler(appContext) }
    val taskRepository by lazy { TaskRepository(appContext, database, reminderScheduler) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
}

val Context.container: AppContainer get() = (applicationContext as AjandaApp).container
