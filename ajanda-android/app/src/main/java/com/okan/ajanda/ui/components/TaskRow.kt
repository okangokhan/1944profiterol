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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.okan.ajanda.ui.theme.priorityColor
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
    val lineColor = color.copy(alpha = 0.75f)
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
                drawLine(lineColor, Offset(left, y), Offset(left + w, y), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                remaining -= w
            }
        }
    )
}

/** Yuvarlak, yaylı animasyonlu onay kutusu. */
@Composable
fun RoundCheck(
    checked: Boolean,
    onToggle: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 22,
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
            .size((size + 14).dp)
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
                .border(1.6.dp, if (enabled) color else color.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check, contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size((size - 6).dp).scale(scale)
            )
        }
    }
}

@Composable
fun TagDot(color: Int, modifier: Modifier = Modifier, size: Int = 8) {
    Box(modifier.size(size.dp).clip(CircleShape).background(Color(color)))
}

@Composable
fun TaskRow(
    details: TaskWithDetails,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: Boolean = true,
    today: LocalDate = LocalDate.now(),
    overrideDate: LocalDate? = null,
    toggleEnabled: Boolean = true,
) {
    val task = details.task
    val accent = details.primaryColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val date = overrideDate ?: task.dueDate
    val overdue = !task.isCompleted && date != null && date < today
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Öncelik çubuğu
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (task.priority >= Priority.HIGH) priorityColor(task.priority) else Color.Transparent)
        )
        RoundCheck(task.isCompleted, onToggle, accent, Modifier.padding(start = 4.dp), haptics = haptics, enabled = toggleEnabled)
        Column(
            Modifier
                .weight(1f)
                .padding(top = 10.dp, bottom = 10.dp, end = 12.dp)
        ) {
            StrikeThroughText(task.title, task.isCompleted, MaterialTheme.typography.bodyLarge)
            val meta = buildList {
                DateText.due(date, task.dueTime, today)?.let { add(it) }
            }
            val subDone = details.subtasks.count { it.isDone }
            val hasMeta = meta.isNotEmpty() || details.subtasks.isNotEmpty() || details.tags.isNotEmpty() ||
                task.recurrence != Recurrence.NONE || task.reminderMinutesBefore != null
            if (hasMeta) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meta.forEach {
                        Text(
                            it, style = MaterialTheme.typography.labelMedium,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (task.reminderMinutesBefore != null && !task.isCompleted) {
                        Icon(Icons.Outlined.Notifications, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.recurrence != Recurrence.NONE) {
                        Icon(Icons.Filled.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (details.subtasks.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Checklist, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                " $subDone/${details.subtasks.size}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    details.tags.take(4).forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TagDot(tag.color)
                            Text(
                                " ${tag.name}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
