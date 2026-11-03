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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.okan.ajanda.MainActivity
import com.okan.ajanda.container
import com.okan.ajanda.data.local.TaskOccurrence
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.TR
import com.okan.ajanda.ui.calendar.MonthMatrix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

/**
 * Aylık ajanda widget'ı: matris hücrelerinde görev başlıkları etiket renginde görünür
 * (yer kalmazsa renkli noktalar). Küçük boyutta bugünün listesine düşer.
 */
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
                c.taskRepository.observeOverdue(LocalDate.now()).map { it.size }
            ) { occ, overdue -> MonthWidgetData(month, monday, days, occ.groupBy { it.date }, overdue) }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        context.container.taskRepository.rollOverRecurring()
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
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.background)
            .padding(10.dp)
            .clickable(actionStartActivity(MainActivity.openCalendarIntent(context)))
    ) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                DateText.monthName(data.month),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
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

        if (size.height < 150.dp) {
            val todays = data.byDate[today].orEmpty()
            Text(
                if (todays.isEmpty()) "Bugün boş" else "Bugün: " + todays.take(3).joinToString(", ") { it.task.title },
                maxLines = 2,
                modifier = GlanceModifier.padding(horizontal = 4.dp),
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface)
            )
            return@Column
        }

        // Matris kartı
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(18.dp).background(GlanceTheme.colors.surface).padding(4.dp)
        ) {
            Row(GlanceModifier.fillMaxWidth()) {
                MonthMatrix.weekdays(data.mondayFirst).forEach { d ->
                    Text(
                        DateText.weekdayShort(d).take(3),
                        modifier = GlanceModifier.defaultWeight().padding(vertical = 2.dp),
                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    )
                }
            }
            val rows = data.days.size / 7
            val cellH: Dp = ((size.height - 66.dp) / rows).coerceAtLeast(18.dp)
            val cellW: Dp = (size.width - 28.dp) / 7
            data.days.chunked(7).forEach { week ->
                Row(GlanceModifier.fillMaxWidth().height(cellH)) {
                    week.forEach { date -> DayCell(date, data, today, cellH, cellW) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(date: LocalDate, data: MonthWidgetData, today: LocalDate, cellH: Dp, cellW: Dp) {
    val inMonth = YearMonth.from(date) == data.month
    val items = data.byDate[date].orEmpty()
    val isToday = date == today
    val labelH = 12.dp
    // Etiket sığacak satır sayısı (gün numarası ~17dp)
    val capacity = if (cellW >= 34.dp) ((cellH - 18.dp) / labelH).toInt().coerceIn(0, 4) else 0
    Column(GlanceModifier.defaultWeight().height(cellH).padding(horizontal = 1.dp)) {
        Box(
            GlanceModifier
                .size(17.dp)
                .cornerRadius(9.dp)
                .background(if (isToday) GlanceTheme.colors.primary else ColorProvider(Color.Transparent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${date.dayOfMonth}",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = if (isToday || inMonth) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isToday -> GlanceTheme.colors.onPrimary
                        inMonth -> GlanceTheme.colors.onSurface
                        else -> GlanceTheme.colors.outline
                    },
                    textAlign = TextAlign.Center
                )
            )
        }
        if (items.isEmpty()) return@Column
        if (capacity >= 1) {
            val shown = if (items.size > capacity) (capacity - 1).coerceAtLeast(1) else items.size
            items.take(shown).forEach { occ -> NoteLabel(occ, labelH) }
            if (items.size > shown) {
                Text(
                    "+${items.size - shown}",
                    style = TextStyle(fontSize = 8.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(start = 2.dp)
                )
            }
        } else {
            Row(GlanceModifier.padding(start = 2.dp, top = 1.dp)) {
                items.take(3).forEach { occ ->
                    val c = occ.details.primaryColor?.let { Color(it) } ?: Color(0xFF6B7280)
                    Box(GlanceModifier.size(4.dp).cornerRadius(2.dp).background(ColorProvider(if (occ.task.isCompleted) c.copy(alpha = 0.35f) else c))) {}
                    Spacer(GlanceModifier.width(2.dp))
                }
            }
        }
    }
}

/** Hücre içi not etiketi: renkli zemin + görev başlığı. */
@Composable
private fun NoteLabel(occ: TaskOccurrence, height: Dp) {
    val c = occ.details.primaryColor?.let { Color(it) } ?: Color(0xFF6B7280)
    val done = occ.task.isCompleted
    Box(GlanceModifier.fillMaxWidth().height(height).padding(top = 1.dp)) {
        Text(
            // Bölünmez boşluk: kelime yerine harf bazında kesilsin ("LGS koç…")
            occ.task.title.replace(' ', '\u00A0'),
            maxLines = 1,
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(3.dp)
                .background(ColorProvider(c.copy(alpha = if (done) 0.08f else 0.18f)))
                .padding(horizontal = 2.dp),
            style = TextStyle(
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(if (done) c.copy(alpha = 0.5f) else c),
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            )
        )
    }
}
