package com.okan.ajanda.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.okan.ajanda.MainActivity
import com.okan.ajanda.container
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.Deadline
import com.okan.ajanda.domain.Urgency
import com.okan.ajanda.ui.theme.urgencyColor
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

class DeadlineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DeadlineWidget()
}

/** "Son Tarihler" widget'ı: önümüzdeki 2 haftanın görevleri eriyen çubuklarla. */
class DeadlineWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.container.taskRepository
        repo.rollOverRecurring()
        val flow = repo.observeDeadlines(LocalDate.now())
        val initial = flow.first()
        provideContent {
            val list by remember { flow }.collectAsState(initial)
            GlanceTheme(colors = WidgetTheme.colors) { DeadlineContent(list) }
        }
    }
}

@Composable
private fun DeadlineContent(list: List<TaskWithDetails>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = LocalDateTime.now()
    val barWidth = size.width - 24.dp - 28.dp

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.openWeekIntent(context)))) {
                Text("Son Tarihler", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
                Text(
                    if (list.isEmpty()) "Yaklaşan son tarih yok" else "${list.size} görev · 2 hafta",
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
            Box(
                GlanceModifier.size(34.dp).cornerRadius(17.dp).background(GlanceTheme.colors.primary)
                    .clickable(actionStartActivity(MainActivity.quickAddIntent(context))),
                contentAlignment = Alignment.Center
            ) { Text("+", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary)) }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (list.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Rahat bir hafta ✓", style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize().cornerRadius(18.dp).background(GlanceTheme.colors.surface)) {
                items(list) { d -> DeadlineItem(d, now, barWidth) }
            }
        }
    }
}

@Composable
private fun DeadlineItem(d: TaskWithDetails, now: LocalDateTime, barWidth: Dp) {
    val context = LocalContext.current
    val info = Deadline.info(d.task, now) ?: return
    val calm = d.primaryColor?.let { Color(it) } ?: Color(0xFF2D54D0)
    val color = urgencyColor(info.urgency, calm)
    val fill = (barWidth * info.fraction).coerceAtLeast(if (info.fraction > 0f) 8.dp else 0.dp)
    Column(
        GlanceModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(actionStartActivity(MainActivity.openTaskIntent(context, d.task.id)))
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            d.primaryColor?.let {
                Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(Color(it)))) {}
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(
                d.task.title, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface)
            )
            Text(info.label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(color)))
        }
        Spacer(GlanceModifier.height(5.dp))
        // Eriyen çubuk: iz + dolgu
        Box(GlanceModifier.width(barWidth).height(8.dp).cornerRadius(4.dp).background(ColorProvider(color.copy(alpha = 0.14f)))) {
            if (fill > 0.dp) Box(GlanceModifier.width(fill).height(8.dp).cornerRadius(4.dp).background(ColorProvider(color))) {}
        }
        // Damla
        if (info.fraction in 0.02f..0.98f && info.urgency != Urgency.OVERDUE) {
            Row {
                Spacer(GlanceModifier.width((fill - 7.dp).coerceAtLeast(0.dp)))
                Box(GlanceModifier.width(4.dp).height(5.dp).cornerRadius(2.dp).background(ColorProvider(color))) {}
            }
        }
        Text(
            DateText.due(d.task.dueDate, d.task.dueTime) ?: "",
            style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
        )
    }
}
