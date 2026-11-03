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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.TagDot
import com.okan.ajanda.ui.theme.TagPalette
import com.okan.ajanda.ui.theme.priorityColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val reminderOptions = listOf<Int?>(null, 0, 10, 30, 60, 1440)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    onClose: () -> Unit,
    vm: TaskEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s = vm.state
    val tags by vm.tags.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                title = { Text(if (s.isNew) "Yeni Görev" else "Görevi Düzenle") },
                actions = {
                    if (!s.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Sil") }
                    TextButton(onClick = { vm.save(onClose) }) { Text("Kaydet") }
                }
            )
        }
    ) { padding ->
        if (!s.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            // Başlık
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                if (s.title.isEmpty()) {
                    Text(
                        "Ne yapılacak?", style = MaterialTheme.typography.headlineSmall,
                        color = if (s.titleError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
                BasicTextField(
                    value = s.title,
                    onValueChange = vm::setTitle,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = s.notes,
                onValueChange = { v -> vm.update { copy(notes = v) } },
                label = { Text("Notlar") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            SectionTitle("Öncelik")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = s.priority == p,
                        onClick = { vm.update { copy(priority = p) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                        icon = { TagDot(priorityColorInt(p), size = 8) },
                    ) { Text(p.label, maxLines = 1) }
                }
            }

            SectionTitle("Son tarih")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showDate = true },
                    leadingIcon = { Icon(Icons.Outlined.Event, null, Modifier.size(18.dp)) },
                    label = { Text(s.dueDate?.let { DateText.relative(it) } ?: "Tarih seç") }
                )
                AssistChip(
                    onClick = { showTime = true },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                    label = { Text(s.dueTime?.let { DateText.time(it) } ?: "Saat") }
                )
                if (s.dueDate != null) {
                    IconButton(onClick = { vm.setDate(null) }) { Icon(Icons.Filled.Close, "Tarihi temizle") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                listOf("Bugün" to today, "Yarın" to today.plusDays(1), "Gelecek hafta" to today.plusWeeks(1)).forEach { (l, d) ->
                    FilterChip(s.dueDate == d, { vm.setDate(d) }, { Text(l) })
                }
            }

            SectionTitle("Hatırlatıcı")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminderOptions.forEach { m ->
                    FilterChip(s.reminder == m, { vm.setReminder(m) }, { Text(DateText.reminderLabel(m)) })
                }
            }

            SectionTitle("Tekrar")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Recurrence.entries.forEach { r ->
                    FilterChip(s.recurrence == r, { vm.setRecurrence(r) }, { Text(if (r == Recurrence.NONE) "Yok" else r.label) })
                }
            }
            if (s.recurrence != Recurrence.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Her", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { vm.update { copy(recurrenceInterval = (recurrenceInterval - 1).coerceAtLeast(1)) } }) { Text("−") }
                    Text("${s.recurrenceInterval}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.update { copy(recurrenceInterval = (recurrenceInterval + 1).coerceAtMost(99)) } }) { Text("+") }
                    Text("${s.recurrence.unitLabel}da bir".replace("günda", "günde"), style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionTitle("Etiketler")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in s.tagIds,
                        onClick = { vm.toggleTag(tag.id) },
                        label = { Text(tag.name) },
                        leadingIcon = { TagDot(tag.color) }
                    )
                }
                AssistChip(onClick = { showTagDialog = true }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) }, label = { Text("Yeni etiket") })
            }

            SectionTitle("Alt görevler")
            s.subtasks.forEach { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = st.done, onCheckedChange = { vm.toggleSubtask(st.key) })
                    BasicTextField(
                        value = st.title,
                        onValueChange = { vm.editSubtask(st.key, it) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (st.done) 0.5f else 1f)
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.removeSubtask(st.key) }) { Icon(Icons.Filled.Close, "Kaldır", Modifier.size(18.dp)) }
                }
            }
            var newSub by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = newSub,
                onValueChange = { newSub = it },
                placeholder = { Text("Alt görev ekle") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { vm.addSubtask(newSub); newSub = "" }) { Icon(Icons.Filled.Add, "Ekle") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onDone = { vm.addSubtask(newSub); newSub = "" })
            )
        }
    }

    if (showDate) {
        val dp = rememberDatePickerState(
            initialSelectedDateMillis = (s.dueDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDate = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Vazgeç") } }
        ) { DatePicker(dp) }
    }

    if (showTime) {
        val now = s.dueTime ?: LocalTime.of(9, 0)
        val tp = rememberTimePickerState(now.hour, now.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { vm.setTime(LocalTime.of(tp.hour, tp.minute)); showTime = false }) { Text("Tamam") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.setTime(null); showTime = false }) { Text("Saatsiz") }
                    TextButton(onClick = { showTime = false }) { Text("Vazgeç") }
                }
            },
            text = { TimePicker(tp) }
        )
    }

    if (showTagDialog) {
        NewTagDialog(onDismiss = { showTagDialog = false }) { name, color ->
            vm.createTag(name, color); showTagDialog = false
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Görev silinsin mi?") },
            text = { Text("Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onClose) }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }
}

private fun priorityColorInt(p: Priority): Int {
    val c = priorityColor(p)
    return android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text.uppercase(com.okan.ajanda.domain.TR), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
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
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
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
