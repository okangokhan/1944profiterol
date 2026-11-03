package com.okan.ajanda.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.okan.ajanda.data.prefs.PaperStyle
import com.okan.ajanda.data.prefs.ThemeMode
import com.okan.ajanda.ui.AppViewModelProvider
import com.okan.ajanda.ui.components.TagDot
import com.okan.ajanda.ui.components.paperBackground
import com.okan.ajanda.ui.edit.NewTagDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newTag by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 130.dp)
        ) {
            com.okan.ajanda.ui.components.ScreenHeader(overline = "Görünüm, etiketler, bildirimler", title = "Ayarlar")
            Section("Tema")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.themeMode == m, onClick = { vm.setTheme(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)
                    ) { Text(m.label) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ToggleRow("Dinamik renkler (Material You)", "Duvar kağıdından renk al", s.dynamicColor, vm::setDynamic)
            }

            Section("Kağıt dokusu")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperStyle.entries.forEach { p ->
                    val selected = s.paperStyle == p
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.setPaper(p) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .paperBackground(p, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp)
                                )
                        )
                        Text(p.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            Section("Takvim ve his")
            ToggleRow("Hafta pazartesi başlasın", "Kapalıyken Pazar–Cumartesi düzeni", s.weekStartsMonday, vm::setMonday)
            ToggleRow("Dokunsal geri bildirim", "Görev tamamlanınca hafif titreşim", s.haptics, vm::setHaptics)

            Section("Etiketler")
            tags.forEach { tag ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TagDot(tag.color, size = 12)
                    Text(tag.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { vm.deleteTag(tag.id) }) { Icon(Icons.Outlined.Delete, "Etiketi sil") }
                }
            }
            TextButton(onClick = { newTag = true }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text(" Etiket ekle")
            }

            Section("Bildirimler")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:${context.packageName}")))
                    }
                }) { Text("Tam zamanlı hatırlatıcı iznini yönet") }
            }
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }) { Text("Bildirim ayarlarını aç") }

            Section("İpuçları")
            Text(
                "• Hızlı ayarlar paneline “Hızlı Görev” kutucuğunu ekleyerek tek dokunuşla görev oluşturun.\n" +
                    "• Ana ekrana “Haftalık Görevler”, “Son Tarihler” (eriyen çubuklar) ve “Aylık Özet” widget'larını ekleyin.\n" +
                    "• Uygulama simgesine uzun basarak sesli görev kısayolunu kullanın.\n" +
                    "• Sesli/yazılı komut örnekleri: “her pazartesi 9:30 haftalık toplantı #iş”, “15 kasım fatura öde önemli”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (newTag) NewTagDialog(onDismiss = { newTag = false }) { n, c -> vm.createTag(n, c); newTag = false }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title.uppercase(com.okan.ajanda.domain.TR), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
