package com.okan.ajanda

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.okan.ajanda.data.prefs.AppSettings
import com.okan.ajanda.ui.AjandaRoot
import com.okan.ajanda.ui.LaunchAction
import com.okan.ajanda.ui.theme.AjandaTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val launchActions = MutableStateFlow<LaunchAction?>(null)

    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val settingsFlow = container.settingsRepository.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            AjandaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AjandaRoot(settings = settings, launchActions = launchActions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        launchActions.value = when {
            intent.action == ACTION_QUICK_ADD -> LaunchAction.QuickAdd(voice = false)
            intent.action == ACTION_VOICE_ADD -> LaunchAction.QuickAdd(voice = true)
            intent.action == ACTION_OPEN_CALENDAR -> LaunchAction.OpenCalendar
            intent.hasExtra(EXTRA_TASK_ID) -> LaunchAction.OpenTask(intent.getLongExtra(EXTRA_TASK_ID, 0L))
            else -> launchActions.value
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.okan.ajanda.QUICK_ADD"
        const val ACTION_VOICE_ADD = "com.okan.ajanda.VOICE_ADD"
        const val ACTION_OPEN_CALENDAR = "com.okan.ajanda.OPEN_CALENDAR"
        const val EXTRA_TASK_ID = "taskId"

        fun quickAddIntent(context: Context, voice: Boolean = false): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(if (voice) ACTION_VOICE_ADD else ACTION_QUICK_ADD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun openTaskIntent(context: Context, id: Long): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("ajanda://task/$id"))
                .putExtra(EXTRA_TASK_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun openCalendarIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
