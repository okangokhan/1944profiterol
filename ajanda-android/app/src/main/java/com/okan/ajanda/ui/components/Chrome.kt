package com.okan.ajanda.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okan.ajanda.ui.quickadd.speechIntent

/** Ekran başlığı: küçük gri üst satır + büyük kalın başlık. */
@Composable
fun ScreenHeader(overline: String, title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(overline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        actions()
    }
}

/** "Yaz ve ekle" satırı; mikrofon ile sesli giriş. */
@Composable
fun InlineAddField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Yaz ve ekle",
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                onValueChange(if (value.isBlank()) it else "$value $it")
            }
        }
    }
    val enabled = value.isNotBlank()
    val btnBg by animateColorAsState(if (enabled) cs.primary else cs.surfaceContainer, label = "btn")
    val btnFg by animateColorAsState(if (enabled) cs.onPrimary else cs.onSurfaceVariant.copy(alpha = 0.6f), label = "btnFg")
    Row(
        modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(26.dp), ambientColor = cs.outline, spotColor = cs.outline)
            .clip(RoundedCornerShape(26.dp))
            .background(cs.surface)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).padding(start = 18.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Normal), color = cs.onSurfaceVariant.copy(alpha = 0.6f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Normal, color = cs.onSurface),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable {
                try { speech.launch(speechIntent()) } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Bu cihazda konuşma tanıma bulunamadı", Toast.LENGTH_SHORT).show()
                }
            },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Mic, "Sesle ekle", tint = cs.onSurfaceVariant) }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(btnBg)
                .clickable(enabled = enabled, onClick = onSubmit)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) { Text("Ekle", color = btnFg, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}

data class NavItem(val route: String, val label: String?, val icon: ImageVector? = null)

/** Yüzen hap şeklinde alt menü: Bugün · Hafta · Ay · ⚙ */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .shadow(10.dp, RoundedCornerShape(30.dp), ambientColor = cs.outline, spotColor = cs.outline)
            .clip(RoundedCornerShape(30.dp))
            .background(cs.surface)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSel = item.route == selected
            val bg by animateColorAsState(if (isSel) cs.inverseSurface else cs.surface, label = "navBg")
            val fg by animateColorAsState(if (isSel) cs.inverseOnSurface else cs.onSurfaceVariant, label = "navFg")
            Box(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bg)
                    .clickable { onSelect(item.route) }
                    .padding(horizontal = if (item.label != null) 20.dp else 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (item.label != null) Text(item.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                else item.icon?.let { Icon(it, "Ayarlar", tint = fg) }
            }
        }
    }
}

/** Açılır seçim kutusu görünümü (Tarih, Tekrar, Bitiş…). */
@Composable
fun FieldBox(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, muted: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, modifier = Modifier.weight(1f), fontSize = 17.sp, maxLines = 1,
            color = if (muted) cs.onSurfaceVariant else cs.onSurface
        )
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(start = 2.dp, bottom = 8.dp, top = 18.dp),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
