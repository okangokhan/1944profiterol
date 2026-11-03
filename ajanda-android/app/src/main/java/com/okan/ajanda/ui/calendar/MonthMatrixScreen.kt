package com.okan.ajanda.ui.calendar

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.data.local.TaskOccurrence
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.TR
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.TaskRow
import com.okan.ajanda.ui.components.lightHaptic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthMatrixScreen(
    haptics: Boolean,
    onOpenTask: (Long) -> Unit,
    onCreateForDate: (LocalDate) -> Unit,
    vm: CalendarViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedDay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }

    fun share(format: MonthExporter.Format) {
        vm.export(context, format) { uri, mime ->
            val send = Intent(Intent.ACTION_SEND).setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Aylık ajandayı paylaş / kaydet"))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Başlık
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        DateText.monthName(state.month).uppercase(TR),
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.month.year}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    "${state.monthTotal} görev · ${state.monthDone} tamamlandı",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = vm::previous) { Icon(Icons.Filled.ChevronLeft, "Önceki ay") }
            IconButton(onClick = vm::goToday) { Icon(Icons.Outlined.Today, "Bugün") }
            IconButton(onClick = vm::next) { Icon(Icons.Filled.ChevronRight, "Sonraki ay") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Diğer") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("PDF olarak dışa aktar") },
                        leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                        onClick = { menu = false; share(MonthExporter.Format.PDF) }
                    )
                    DropdownMenuItem(
                        text = { Text("Görsel (PNG) olarak dışa aktar") },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                        onClick = { menu = false; share(MonthExporter.Format.PNG) }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Haftanın günleri başlığı
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            MonthMatrix.weekdays(state.mondayFirst).forEach { d ->
                Text(
                    DateText.weekdayShort(d),
                    modifier = Modifier.weight(1f).padding(start = 6.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == DayOfWeek.SUNDAY || d == DayOfWeek.SATURDAY) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        var drag by remember { mutableFloatStateOf(0f) }
        val threshold = with(LocalDensity.current) { 64.dp.toPx() }
        AnimatedContent(
            targetState = state,
            contentKey = { it.month },
            transitionSpec = {
                val forward = targetState.month > initialState.month
                (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                    .using(SizeTransform(clip = false))
            },
            label = "month",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > threshold) vm.previous() else if (drag < -threshold) vm.next()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    ) { _, delta -> drag += delta }
                }
        ) { s ->
            MonthGrid(
                state = s,
                onDayClick = { if (haptics) view.lightHaptic(); vm.select(it) },
                onDayLongClick = { onCreateForDate(it) },
            )
        }
    }

    selected?.let { day ->
        DaySheet(
            day = day,
            items = state.byDate[day].orEmpty(),
            haptics = haptics,
            onDismiss = { vm.select(null) },
            onToggle = vm::toggle,
            onOpenTask = { vm.select(null); onOpenTask(it) },
            onQuickAdd = { vm.quickAdd(day, it) },
            onDetailedAdd = { vm.select(null); onCreateForDate(day) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthGrid(
    state: CalendarUiState,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, lineColor, RoundedCornerShape(6.dp))
    ) {
        val rows = (state.days.size / 7).coerceAtLeast(1)
        val cellHeight: Dp = maxHeight / rows
        val today = LocalDate.now()
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            userScrollEnabled = false,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(state.days, key = { _, d -> d.toEpochDay() }) { index, date ->
                DayCell(
                    date = date,
                    inMonth = YearMonth.from(date) == state.month,
                    isToday = date == today,
                    items = state.byDate[date].orEmpty(),
                    height = cellHeight,
                    drawRight = index % 7 != 6,
                    drawBottom = index / 7 != rows - 1,
                    lineColor = lineColor,
                    modifier = Modifier.combinedClickable(
                        onClick = { onDayClick(date) },
                        onLongClick = { onDayLongClick(date) }
                    )
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    items: List<TaskOccurrence>,
    height: Dp,
    drawRight: Boolean,
    drawBottom: Boolean,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .height(height)
            .fillMaxWidth()
            .drawBehind {
                val w = 1.dp.toPx() * 0.7f
                if (drawRight) drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), w)
                if (drawBottom) drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), w)
            }
            .background(if (inMonth) Color.Transparent else cs.onSurface.copy(alpha = 0.035f))
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isToday) cs.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 11.sp,
                fontWeight = if (isToday || inMonth) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> cs.onPrimary
                    inMonth -> cs.onSurface
                    else -> cs.onSurface.copy(alpha = 0.35f)
                }
            )
        }
        val labelHeight = 14.dp
        val capacity = ((height - 28.dp) / labelHeight).toInt().coerceAtLeast(0)
        if (items.isNotEmpty()) {
            if (capacity >= 1) {
                val shown = if (items.size > capacity) (capacity - 1).coerceAtLeast(0) else items.size
                items.take(shown).forEach { occ -> TaskLabel(occ, labelHeight) }
                if (items.size > shown) {
                    Text(
                        "+${items.size - shown}",
                        fontSize = 9.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            } else {
                // Çok küçük hücrelerde renkli noktalar
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(start = 3.dp, top = 2.dp)) {
                    items.take(4).forEach { occ ->
                        Box(Modifier.size(5.dp).clip(CircleShape).background(occ.details.primaryColor?.let { Color(it) } ?: cs.primary))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskLabel(occ: TaskOccurrence, height: Dp) {
    val cs = MaterialTheme.colorScheme
    val color = occ.details.primaryColor?.let { Color(it) } ?: cs.primary
    val done = occ.task.isCompleted
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = if (done) 0.08f else 0.16f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(2.5.dp).height(height).background(color.copy(alpha = if (done) 0.4f else 1f)))
        Text(
            occ.task.title,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = cs.onSurface.copy(alpha = if (done) 0.45f else if (occ.isVirtual) 0.7f else 1f),
            textDecoration = if (done) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    day: LocalDate,
    items: List<TaskOccurrence>,
    haptics: Boolean,
    onDismiss: () -> Unit,
    onToggle: (TaskOccurrence) -> Unit,
    onOpenTask: (Long) -> Unit,
    onQuickAdd: (String) -> Unit,
    onDetailedAdd: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(DateText.full(day), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                if (items.isEmpty()) "Bu gün için görev yok" else "${items.size} görev",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(items, key = { "${it.task.id}_${it.date}" }) { occ ->
                    TaskRow(
                        details = occ.details,
                        onToggle = { onToggle(occ) },
                        onClick = { onOpenTask(occ.task.id) },
                        haptics = haptics,
                        overrideDate = occ.date,
                        toggleEnabled = !occ.isVirtual,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            fun submit() { if (text.isNotBlank()) { onQuickAdd(text); text = "" } }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Bu güne hızlı görev ekle") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = { IconButton(onClick = { submit() }) { Icon(Icons.AutoMirrored.Filled.Send, "Ekle") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            TextButton(onClick = onDetailedAdd, modifier = Modifier.align(Alignment.End)) { Text("Detaylı görev oluştur") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
