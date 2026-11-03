package com.okan.ajanda.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.data.local.TaskWithDetails
import com.okan.ajanda.domain.DateText
import java.time.LocalDate

fun View.confirmHaptic() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    else performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

fun View.lightHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/** Üstü soldan sağa çizilerek kapanan metin. */
@Composable
fun StrikeThroughText(
    text: String,
    done: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 2,
) {
    val progress by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "strike"
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val lineColor = color.copy(alpha = 0.7f)
    Text(
        text = text,
        style = style,
        color = color.copy(alpha = 1f - 0.55f * progress),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            if (progress <= 0f) return@drawWithContent
            val widths = (0 until l.lineCount).map { l.getLineRight(it) - l.getLineLeft(it) }
            var remaining = widths.sum() * progress
            for (i in 0 until l.lineCount) {
                if (remaining <= 0f) break
                val left = l.getLineLeft(i)
                val w = minOf(widths[i], remaining)
                val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f
                drawLine(lineColor, Offset(left, y), Offset(left + w, y), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                remaining -= w
            }
        }
    )
}

/** Yuvarlak, yaylı animasyonlu onay halkası (etiket renginde). */
@Composable
fun RoundCheck(
    checked: Boolean,
    onToggle: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 26,
    haptics: Boolean = true,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "fill")
    val scale by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )
    Box(
        modifier = modifier
            .size((size + 16).dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) {
                if (haptics) { if (!checked) view.confirmHaptic() else view.lightHaptic() }
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, if (enabled) color else color.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check, contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size - 8).dp).scale(scale)
            )
        }
    }
}

@Composable
fun TagDot(color: Int, modifier: Modifier = Modifier, size: Int = 10) {
    Box(modifier.size(size.dp).clip(CircleShape).background(Color(color)))
}

/** Bölüm başlığı: "GECİKEN", "BUGÜN 0/1" … */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title.uppercase(com.okan.ajanda.domain.TR), style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
        action?.invoke()
    }
}

/** Beyaz, yuvarlatılmış kart; içindeki satırlar ince çizgilerle ayrılır. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

@Composable
fun CardDivider() {
    HorizontalDivider(Modifier.padding(start = 72.dp, end = 20.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Kart içi görev satırı: [onay] Başlık ↻ ……… 31 Eki ● Etiket
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskLine(
    details: TaskWithDetails,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: Boolean = true,
    showDate: Boolean = false,
    date: LocalDate? = details.task.dueDate,
    today: LocalDate = LocalDate.now(),
    toggleEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
) {
    val row: @Composable () -> Unit = {
        TaskLineContent(details, onToggle, onClick, haptics, showDate, date, today, toggleEnabled)
    }
    if (onDelete == null) {
        Box(modifier) { row() }
        return
    }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) { Icon(Icons.Outlined.Delete, "Sil", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    ) { row() }
}

@Composable
private fun TaskLineContent(
    details: TaskWithDetails,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    haptics: Boolean,
    showDate: Boolean,
    date: LocalDate?,
    today: LocalDate,
    toggleEnabled: Boolean,
) {
    val task = details.task
    val cs = MaterialTheme.colorScheme
    val tag = details.tags.firstOrNull()
    val accent = tag?.let { Color(it.color) } ?: cs.onSurfaceVariant
    val overdue = !task.isCompleted && date != null && date < today
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundCheck(task.isCompleted, onToggle, accent, haptics = haptics, enabled = toggleEnabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.priority >= Priority.HIGH && !task.isCompleted) {
                    Icon(Icons.Filled.Flag, "Öncelikli", Modifier.size(16.dp), tint = Color(0xFFD2453B))
                    Spacer(Modifier.width(4.dp))
                }
                StrikeThroughText(task.title, task.isCompleted, MaterialTheme.typography.bodyLarge, Modifier.weight(1f, fill = false))
                if (task.recurrence != Recurrence.NONE) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Repeat, null, Modifier.size(15.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            val sub = buildList {
                task.dueTime?.let { add(DateText.time(it)) }
                if (details.subtasks.isNotEmpty()) add("${details.subtasks.count { it.isDone }}/${details.subtasks.size} alt görev")
                if (task.notes.isNotBlank()) add(task.notes.lineSequence().first().take(40))
            }
            if (sub.isNotEmpty() || (task.reminderMinutesBefore != null && !task.isCompleted)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (task.reminderMinutesBefore != null && !task.isCompleted) {
                        Icon(Icons.Outlined.Notifications, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
                    }
                    Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (showDate && date != null) {
            Text(
                DateText.relative(date, today),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = if (overdue) cs.error else cs.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
        }
        if (tag != null) {
            TagDot(tag.color)
            Text(
                " ${tag.name}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 0.dp)
            )
        }
    }
}

@Composable
fun EmptyLine(text: String) {
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
