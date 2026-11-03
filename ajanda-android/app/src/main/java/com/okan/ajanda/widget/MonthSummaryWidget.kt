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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.okan.ajanda.MainActivity
import com.okan.ajanda.container
import com.okan.ajanda.data.local.TaskOccurrence
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.TR
import com.okan.ajanda.ui.calendar.MonthMatrix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.time.YearMonth

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MonthSummaryWidget()
}

data class MonthWidgetData(
    val month: YearMonth,
    val mondayFirst: Boolean,
    val days: List<LocalDate>,
    val byDate: Map<LocalDate, List<TaskOccurrence>>,
    val overdue: Int,
)

/** Aylık özet widget'ı: mini ajanda matrisi + açık/tamamlanan/gecikmiş sayaçları. */
class MonthSummaryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun dataFlow(context: Context): Flow<MonthWidgetData> {
        val c = context.container
        val month = YearMonth.now()
        return c.settingsRepository.settings.map { it.weekStartsMonday }.flatMapLatest { monday ->
            val days = MonthMatrix.days(month, monday)
            combine(
                c.taskRepository.observeRange(days.first(), days.last()),
                c.taskRepository.observeWeek(LocalDate.now()).map { it.overdue.size }
            ) { occ, overdue ->
                MonthWidgetData(month, monday, days, occ.groupBy { it.date }, overdue)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = dataFlow(context)
        val initial = flow.first()
        provideContent {
            val data by remember { flow }.collectAsState(initial)
            GlanceTheme(colors = WidgetTheme.colors) { MonthContent(data) }
        }
    }
}

@Composable
private fun MonthContent(data: MonthWidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val today = LocalDate.now()
    val inMonth = data.byDate.filterKeys { YearMonth.from(it) == data.month }.values.flatten()
    val open = inMonth.count { !it.task.isCompleted }
    val done = inMonth.count { it.task.isCompleted && !it.isVirtual }

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
            .clickable(actionStartActivity(MainActivity.openCalendarIntent(context)))
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                DateText.monthName(data.month).uppercase(TR),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
            )
            Spacer(GlanceModifier.width(6.dp))
            Text("${data.month.year}", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "$open açık · $done bitti" + if (data.overdue > 0) " · ${data.overdue} gecikmiş" else "",
                style = TextStyle(fontSize = 11.sp, color = if (data.overdue > 0) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant)
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        val small = size.height < 150.dp
        if (small) {
            // Küçük boyut: sadece bugünün özeti
            val todays = data.byDate[today].orEmpty()
            Text(
                if (todays.isEmpty()) "Bugün boş" else "Bugün: " + todays.take(3).joinToString(", ") { it.task.title },
                maxLines = 2,
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface)
            )
            return@Column
        }

        Row(GlanceModifier.fillMaxWidth()) {
            MonthMatrix.weekdays(data.mondayFirst).forEach { d ->
                Text(
                    DateText.weekdayShort(d).take(2),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
                )
            }
        }
        val rows = data.days.size / 7
        val cellH: Dp = ((size.height - 70.dp) / rows).coerceAtLeast(18.dp)
        data.days.chunked(7).forEach { week ->
            Row(GlanceModifier.fillMaxWidth().height(cellH)) {
                week.forEach { date -> DayCell(date, data, today, cellH) }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.DayCell(date: LocalDate, data: MonthWidgetData, today: LocalDate, cellH: Dp) {
    val inMonth = YearMonth.from(date) == data.month
    val items = data.byDate[date].orEmpty()
    val isToday = date == today
    Column(
        GlanceModifier.defaultWeight().height(cellH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            GlanceModifier
                .size(20.dp)
                .cornerRadius(10.dp)
                .background(if (isToday) GlanceTheme.colors.primary else ColorProvider(Color.Transparent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${date.dayOfMonth}",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isToday -> GlanceTheme.colors.onPrimary
                        inMonth -> GlanceTheme.colors.onSurface
                        else -> GlanceTheme.colors.outline
                    },
                    textAlign = TextAlign.Center
                )
            )
        }
        if (items.isNotEmpty() && cellH >= 26.dp) {
            Row(GlanceModifier.padding(top = 1.dp)) {
                items.take(3).forEach { occ ->
                    val c = occ.details.primaryColor?.let { Color(it) } ?: Color(0xFF7A7568)
                    Box(
                        GlanceModifier.size(4.dp).cornerRadius(2.dp)
                            .background(ColorProvider(if (occ.task.isCompleted) c.copy(alpha = 0.35f) else c))
                    ) {}
                    Spacer(GlanceModifier.width(2.dp))
                }
            }
        }
    }
}
