package com.okan.ajanda.ui.quickadd

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.VoiceCommandParser
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.confirmHaptic
import java.time.LocalDate

data class QuickAddRequest(val date: LocalDate? = LocalDate.now(), val startVoice: Boolean = false)

fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Görevi söyleyin… (ör. “yarın saat 10 toplantı”)")

/**
 * Hızlı ekleme alt sayfası. Yazılan veya söylenen metin akıllı komut ayrıştırıcıdan geçer:
 * tarih, saat, öncelik, tekrar ve #etiketler otomatik algılanır.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    request: QuickAddRequest,
    haptics: Boolean,
    onDismiss: () -> Unit,
    onOpenEditor: (LocalDate?) -> Unit,
    vm: QuickAddViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    var baseDate by remember { mutableStateOf(request.date) }
    val parsed = remember(text) { VoiceCommandParser.parse(text) }
    val focus = remember { FocusRequester() }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
                text = if (text.isBlank()) spoken else "$text $spoken"
            }
        }
    }
    fun listen() {
        try {
            speech.launch(speechIntent())
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Bu cihazda konuşma tanıma bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }

    fun submit() {
        if (text.isBlank()) return
        if (haptics) view.confirmHaptic()
        vm.add(parsed, baseDate)
        text = ""
        onDismiss()
    }

    LaunchedEffect(Unit) {
        if (request.startVoice) listen() else runCatching { focus.requestFocus() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp)
        ) {
            Text("Hızlı görev", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("ör. yarın saat 15 müşteri toplantısı #iş acil") },
                trailingIcon = {
                    IconButton(onClick = { listen() }) { Icon(Icons.Filled.Mic, "Sesle söyle") }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                maxLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                FilterChip(baseDate == today, { baseDate = today }, { Text("Bugün") })
                FilterChip(baseDate == today.plusDays(1), { baseDate = today.plusDays(1) }, { Text("Yarın") })
                FilterChip(baseDate == null, { baseDate = null }, { Text("Tarihsiz") })
                if (baseDate != null && baseDate != today && baseDate != today.plusDays(1)) {
                    FilterChip(true, {}, { Text(DateText.relative(baseDate!!)) })
                }
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Algılanan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text(parsed.title, maxLines = 1) })
                    (parsed.date ?: baseDate)?.let { d ->
                        AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Outlined.Event, null, Modifier.size(16.dp)) },
                            label = { Text(DateText.due(d, parsed.time) ?: "") })
                    }
                    if (parsed.priority != Priority.MEDIUM) {
                        AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp)) },
                            label = { Text(parsed.priority.label) })
                    }
                    if (parsed.recurrence != Recurrence.NONE) {
                        AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Filled.Repeat, null, Modifier.size(16.dp)) },
                            label = { Text(parsed.recurrence.label) })
                    }
                    if (parsed.reminder) {
                        AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Outlined.Notifications, null, Modifier.size(16.dp)) },
                            label = { Text("Hatırlat") })
                    }
                    parsed.tags.forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpenEditor(parsed.date ?: baseDate) }) { Text("Detaylı düzenle") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { submit() }, enabled = text.isNotBlank()) { Text("Ekle") }
            }
        }
    }
}
