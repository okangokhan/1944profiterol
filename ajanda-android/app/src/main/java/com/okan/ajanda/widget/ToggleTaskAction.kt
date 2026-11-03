package com.okan.ajanda.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.okan.ajanda.container

val TaskIdKey = ActionParameters.Key<Long>("taskId")

/** Widget'taki checkbox ile görevi doğrudan tamamlar / geri alır. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        context.container.taskRepository.toggleComplete(id)
    }
}
