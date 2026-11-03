package com.okan.ajanda.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.okan.ajanda.MainActivity
import com.okan.ajanda.container
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.data.repository.WeekData
import com.okan.ajanda.domain.DateText
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class WeeklyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WeeklyTaskWidget()
}

/** Haftalık liste widget'ı: gecikmiş + önümüzdeki 7 gün, checkbox ile tamamlama. */
class WeeklyTaskWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.container.taskRepository
        val flow = repo.observeWeek(LocalDate.now())
        repo.rollOverRecurring()
        val initial = flow.first()
        provideContent {
            val data by remember { flow }.collectAsState(initial)
            GlanceTheme(colors = WidgetTheme.colors) { WeeklyContent(data) }
        }
    }
}

private sealed interface Row_ {
    data class Header(val title: String, val warn: Boolean) : Row_
    data class Item(val d: TaskWithDetails, val date: LocalDate, val virtual: Boolean) : Row_
}

@Composable
private fun WeeklyContent(data: WeekData) {
    val context = LocalContext.current
    val compact = LocalSize.current.width < 200.dp
    val rows = buildList {
        if (data.overdue.isNotEmpty()) {
            add(Row_.Header("Gecikmiş", true))
            data.overdue.forEach { add(Row_.Item(it, it.task.dueDate ?: data.today, false)) }
        }
        data.days.forEach { (date, occ) ->
            if (occ.isNotEmpty()) {
                val title = when (date) {
                    data.today -> "Bugün"
                    data.today.plusDays(1) -> "Yarın"
                    else -> DateText.weekdayLong(date.dayOfWeek)
                }
                add(Row_.Header("$title · ${date.dayOfMonth}", false))
                occ.forEach { add(Row_.Item(it.details, it.date, it.isVirtual)) }
            }
        }
    }

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.openWeekIntent(context)))) {
                Text("Bu Hafta", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                if (!compact) {
                    val end = data.today.plusDays(6)
                    Text(
                        "${DateText.relative(data.today)} – ${DateText.relative(end)}",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }
            Box(
                GlanceModifier
                    .size(34.dp)
                    .cornerRadius(17.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity(MainActivity.quickAddIntent(context))),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (rows.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bu hafta için görev yok ✓", style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(rows) { row ->
                    when (row) {
                        is Row_.Header -> Text(
                            row.title.uppercase(com.okan.ajanda.domain.TR),
                            modifier = GlanceModifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
                            style = TextStyle(
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = if (row.warn) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                        is Row_.Item -> WidgetTaskRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetTaskRow(row: Row_.Item) {
    val context = LocalContext.current
    val task = row.d.task
    val done = task.isCompleted
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.virtual) {
            Spacer(GlanceModifier.width(48.dp))
        } else {
            CheckBox(
                checked = done,
                onCheckedChange = actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to task.id)),
            )
        }
        Column(
            GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.openTaskIntent(context, task.id)))
        ) {
            Text(
                task.title,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = if (done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                )
            )
            task.dueTime?.let {
                Text(DateText.time(it), style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
        }
        row.d.primaryColor?.let { c ->
            Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(Color(c)))) {}
            Spacer(GlanceModifier.width(4.dp))
        }
    }
}
