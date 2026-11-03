package com.okan.ajanda.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetRefresher {
    suspend fun refreshAll(context: Context) {
        runCatching { WeeklyTaskWidget().updateAll(context) }
        runCatching { MonthSummaryWidget().updateAll(context) }
        runCatching { DeadlineWidget().updateAll(context) }
    }
}
