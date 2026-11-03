package com.okan.ajanda.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import com.okan.ajanda.domain.ParsedCommand
import com.okan.ajanda.ui.components.confirmHaptic
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.RecurrenceCalculator
import com.okan.ajanda.domain.TR
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.FieldBox
import com.okan.ajanda.ui.components.FieldLabel
import com.okan.ajanda.ui.theme.TagPalette
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class EditRequest(
    val taskId: Long = 0,
    val date: LocalDate? = null,
    val prefill: ParsedCommand? = null,
    val nonce: Long = System.nanoTime(),
)

private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", TR)
private val reminderOptions = listOf<Int?>(null, 0, 10, 30, 60, 1440)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditSheet(request: EditRequest, onClose: () -> Unit) {
    val vm: TaskEditViewModel = viewModel(key = "edit_${request.nonce}", factory = AppViewModelProvider.Factory)
    LaunchedEffect(request) { vm.load(request.taskId, request.date, request.prefill) }
    val titleFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    fun trySave() {
        if (vm.save(onClose)) {
            view.confirmHaptic()
        } else {
            scope.launch { scroll.animateScrollTo(0) }
            runCatching { titleFocus.requestFocus() }
        }
    }
    val s = vm.state
    val tags by vm.tags.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pickDate by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var recurrenceMenu by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newSub by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        if (!s.loaded) { Box(Modifier.height(300.dp)); return@ModalBottomSheet }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Kapat", Modifier.size(28.dp), tint = cs.onSurfaceVariant) }
            }
            // Başlık
            Box(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                if (s.title.isEmpty()) Text(
                    "Görev başlığı", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (s.titleError) cs.error else cs.outline
                )
                BasicTextField(
                    value = s.title,
                    onValueChange = vm::setTitle,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus)
                )
            }
            if (s.titleError) {
                Text(
                    "Kaydetmek için bir başlık yaz",
                    color = cs.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            // Yeni görevde başlık alanına odaklan
            LaunchedEffect(s.loaded) {
                if (s.loaded && s.isNew && s.title.isEmpty()) runCatching { titleFocus.requestFocus() }
            }

            // Etiketler (tek seçim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tags.forEach { tag ->
                    val selected = tag.id in s.tagIds
                    val c = Color(tag.color)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (selected) c.copy(alpha = 0.14f) else cs.surfaceContainer)
                            .border(2.dp, if (selected) c else Color.Transparent, RoundedCornerShape(24.dp))
                            .clickable { vm.selectTag(tag.id) }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(10.dp))
                        Text(tag.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer)
                        .clickable { newTag = true }.padding(horizontal = 16.dp, vertical = 13.dp)
                ) { Icon(Icons.Filled.Add, "Yeni etiket", tint = cs.onSurfaceVariant) }
            }

            // Tarih + Tekrar
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Tarih")
                    FieldBox(s.dueDate?.let(dateFmt::format) ?: "Tarih yok", { pickDate = true }, muted = s.dueDate == null)
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Tekrar")
                    Box {
                        FieldBox(if (s.recurrence == Recurrence.NONE) "Tekrar yok" else s.recurrence.label, { recurrenceMenu = true }, muted = s.recurrence == Recurrence.NONE)
                        DropdownMenu(expanded = recurrenceMenu, onDismissRequest = { recurrenceMenu = false }) {
                            Recurrence.entries.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(if (r == Recurrence.NONE) "Tekrar yok" else r.label) },
                                    onClick = { vm.setRecurrence(r); recurrenceMenu = false }
                                )
                            }
                        }
                    }
                }
            }

            if (s.recurrence == Recurrence.WEEKLY) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = (s.recurrenceDays and RecurrenceCalculator.bit(d)) != 0
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (on) cs.primary else cs.surfaceContainer)
                                .clickable { vm.toggleWeekday(d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                DateText.weekdayShort(d).lowercase(TR).replaceFirstChar { it.titlecase(TR) },
                                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                color = if (on) cs.onPrimary else cs.onSurface
                            )
                        }
                    }
                }
            }
            if (s.recurrence != Recurrence.NONE) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1.3f)) {
                        FieldLabel("Bitiş")
                        FieldBox(s.recurrenceEnd?.let(dateFmt::format) ?: "Süresiz", { pickEnd = true }, muted = s.recurrenceEnd == null)
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Sıklık")
                        Row(
                            Modifier.height(56.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { vm.update { copy(recurrenceInterval = (recurrenceInterval - 1).coerceAtLeast(1)) } }) { Text("−", fontSize = 20.sp) }
                            Text("${s.recurrenceInterval}", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            TextButton(onClick = { vm.update { copy(recurrenceInterval = (recurrenceInterval + 1).coerceAtMost(99)) } }) { Text("+", fontSize = 20.sp) }
                        }
                    }
                }
            }

            // Saat + Hatırlatma
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Saat")
                    FieldBox(s.dueTime?.let { DateText.time(it) } ?: "Gün boyu", { pickTime = true }, muted = s.dueTime == null)
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Hatırlatma")
                    Box {
                        FieldBox(DateText.reminderLabel(s.reminder), { reminderMenu = true }, muted = s.reminder == null)
                        DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                            reminderOptions.forEach { m ->
                                DropdownMenuItem(text = { Text(DateText.reminderLabel(m)) }, onClick = { vm.setReminder(m); reminderMenu = false })
                            }
                        }
                    }
                }
            }

            FieldLabel("Not")
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainer)
                    .padding(18.dp)
            ) {
                if (s.notes.isEmpty()) Text("Not ekle…", color = cs.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 16.sp)
                BasicTextField(
                    value = s.notes,
                    onValueChange = { v -> vm.update { copy(notes = v) } },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FieldLabel("Alt görevler")
            s.subtasks.forEach { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = st.done, onCheckedChange = { vm.toggleSubtask(st.key) })
                    BasicTextField(
                        value = st.title,
                        onValueChange = { vm.editSubtask(st.key, it) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Normal,
                            color = cs.onSurface.copy(alpha = if (st.done) 0.5f else 1f)
                        ),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.removeSubtask(st.key) }) { Icon(Icons.Filled.Close, "Kaldır", Modifier.size(18.dp), tint = cs.onSurfaceVariant) }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer).padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (newSub.isEmpty()) Text("Alt görev ekle", color = cs.onSurfaceVariant.copy(alpha = 0.6f))
                    BasicTextField(
                        value = newSub, onValueChange = { newSub = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal, color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                        keyboardActions = KeyboardActions(onDone = { vm.addSubtask(newSub); newSub = "" }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(onClick = { vm.addSubtask(newSub); newSub = "" }) { Icon(Icons.Filled.Add, "Ekle") }
            }

            Row(
                Modifier.padding(top = 14.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.setPriorityFlag(s.priority < Priority.HIGH) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = s.priority >= Priority.HIGH, onCheckedChange = { vm.setPriorityFlag(it) })
                Text("Öncelikli", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
            }

            if (s.titleError) {
                Text(
                    "Başlık boş — yukarıdaki “Görev başlığı” alanını doldur",
                    color = cs.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!s.isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Sil", color = cs.error, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { trySave() },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    modifier = Modifier.height(60.dp)
                ) { Text("Kaydet", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp)) }
            }
        }
    }

    if (pickDate || pickEnd) {
        val initial = (if (pickEnd) s.recurrenceEnd ?: s.dueDate?.plusMonths(3) else s.dueDate) ?: LocalDate.now()
        val dp = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val close = { pickDate = false; pickEnd = false }
        DatePickerDialog(
            onDismissRequest = close,
            confirmButton = {
                TextButton(onClick = {
                    val d = dp.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    if (pickEnd) vm.update { copy(recurrenceEnd = d) } else vm.setDate(d)
                    close()
                }) { Text("Tamam") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { if (pickEnd) vm.update { copy(recurrenceEnd = null) } else vm.setDate(null); close() }) {
                        Text(if (pickEnd) "Süresiz" else "Tarih yok")
                    }
                    TextButton(onClick = close) { Text("Vazgeç") }
                }
            }
        ) { DatePicker(dp) }
    }

    if (pickTime) {
        val t = s.dueTime ?: LocalTime.of(9, 0)
        val tp = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { vm.setTime(LocalTime.of(tp.hour, tp.minute)); pickTime = false }) { Text("Tamam") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.setTime(null); pickTime = false }) { Text("Gün boyu") }
                    TextButton(onClick = { pickTime = false }) { Text("Vazgeç") }
                }
            },
            text = { TimePicker(tp) }
        )
    }

    if (newTag) NewTagDialog(onDismiss = { newTag = false }) { n, c -> vm.createTag(n, c); newTag = false }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Görev silinsin mi?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onClose) }) { Text("Sil", color = cs.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTagDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(TagPalette.colors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni etiket") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("İsim") }, singleLine = true)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TagPalette.colors.forEach { c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, color) }) { Text("Oluştur") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
