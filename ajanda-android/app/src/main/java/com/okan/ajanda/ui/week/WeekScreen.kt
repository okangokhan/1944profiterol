package com.okan.ajanda.ui.week

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.Deadline
import com.okan.ajanda.domain.TR
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.CardDivider
import com.okan.ajanda.ui.components.MeltingBar
import com.okan.ajanda.ui.components.SectionHeader
import com.okan.ajanda.ui.components.SoftCard
import com.okan.ajanda.ui.components.TagDot
import com.okan.ajanda.ui.components.TaskLine
import com.okan.ajanda.ui.theme.urgencyColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun WeekScreen(
    haptics: Boolean,
    onOpenTask: (Long) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    vm: WeekViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Çubuklar dakikada bir erir
    val now by produceState(LocalDateTime.now()) {
        while (true) { delay(60_000); value = LocalDateTime.now() }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        state = list,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp)
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(rangeText(s.start, s.end), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (s.isCurrent) "Bu Hafta" else "Hafta", style = MaterialTheme.typography.headlineLarge)
                }
                IconButton(onClick = vm::previous) { Icon(Icons.Filled.ChevronLeft, "Önceki hafta") }
                if (!s.isCurrent) {
                    Text("Bugün", Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.current() }.padding(8.dp),
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = vm::next) { Icon(Icons.Filled.ChevronRight, "Sonraki hafta") }
            }
        }
        item(key = "strip") {
            WeekStrip(s) { index -> scope.launch { list.animateScrollToItem(4 + index) } }
        }
        item(key = "h_deadlines") { SectionHeader("Son tarihler", trailing = if (s.deadlines.isEmpty()) null else "${s.deadlines.size}") }
        item(key = "c_deadlines") {
            SoftCard(Modifier.animateContentSize()) {
                if (s.deadlines.isEmpty()) {
                    Text(
                        "Bu hafta son tarihi olan görev yok",
                        Modifier.padding(22.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                s.deadlines.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DeadlineRow(d, now) { onOpenTask(d.task.id) }
                }
            }
        }
        s.days.forEach { (date, items) ->
            item(key = "d_$date") {
                val isToday = date == s.today
                val title = (if (isToday) "Bugün · " else "") + DateText.weekdayLong(date.dayOfWeek) + " " + date.dayOfMonth
                SectionHeader(
                    title,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = if (items.isEmpty()) null else "${items.count { it.task.isCompleted }}/${items.size}",
                    action = {
                        Icon(
                            Icons.Filled.Add, "Bu güne ekle",
                            Modifier.padding(start = 10.dp).size(22.dp).clip(CircleShape).clickable { onAddForDate(date) },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                SoftCard(Modifier.animateContentSize()) {
                    if (items.isEmpty()) {
                        Text("—", Modifier.padding(horizontal = 22.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.outline)
                    }
                    items.forEachIndexed { i, occ ->
                        androidx.compose.runtime.key("${occ.task.id}_${occ.date}") {
                            if (i > 0) CardDivider()
                            TaskLine(
                                details = occ.details,
                                onToggle = { vm.toggle(occ) },
                                onClick = { onOpenTask(occ.task.id) },
                                haptics = haptics,
                                date = occ.date,
                                toggleEnabled = !occ.isVirtual,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rangeText(start: LocalDate, end: LocalDate): String {
    val d = DateTimeFormatter.ofPattern("d MMMM", TR)
    return if (start.month == end.month) "${start.dayOfMonth} – ${d.format(end)}" else "${d.format(start)} – ${d.format(end)}"
}

@Composable
private fun WeekStrip(s: WeekUiState, onDay: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        s.days.forEachIndexed { index, (date, items) ->
            val isToday = date == s.today
            val open = items.count { !it.task.isCompleted }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isToday) cs.primary else cs.surface)
                    .clickable { onDay(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    DateText.weekdayShort(date.dayOfWeek).lowercase(TR).replaceFirstChar { it.titlecase(TR) },
                    fontSize = 12.sp, color = if (isToday) cs.onPrimary.copy(alpha = 0.8f) else cs.onSurfaceVariant
                )
                Text("${date.dayOfMonth}", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = if (isToday) cs.onPrimary else cs.onSurface)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                    items.filter { !it.task.isCompleted }.take(3).forEach { occ ->
                        val c = occ.details.primaryColor?.let { Color(it) } ?: cs.onSurfaceVariant
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (isToday) cs.onPrimary else c))
                    }
                    if (open == 0 && items.isNotEmpty()) Text("✓", fontSize = 9.sp, color = if (isToday) cs.onPrimary else cs.tertiary)
                }
            }
        }
    }
}

@Composable
private fun DeadlineRow(d: TaskWithDetails, now: LocalDateTime, onClick: () -> Unit) {
    val info = Deadline.info(d.task, now) ?: return
    val cs = MaterialTheme.colorScheme
    val tag = d.tags.firstOrNull()
    val calm = tag?.let { Color(it.color) } ?: cs.primary
    val color = urgencyColor(info.urgency, calm)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tag?.let { TagDot(it.color); Spacer(Modifier.width(8.dp)) }
            Text(d.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(info.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
        }
        Spacer(Modifier.height(8.dp))
        MeltingBar(info.fraction, color)
        Text(
            DateText.due(d.task.dueDate, d.task.dueTime) ?: "",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}
