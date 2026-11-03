package com.okan.ajanda.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.prefs.ThemeMode

// Mürekkep & kağıt paleti
val LightColors = lightColorScheme(
    primary = Color(0xFF2F2E2B),
    onPrimary = Color(0xFFF7F5F0),
    primaryContainer = Color(0xFFE9E6DE),
    onPrimaryContainer = Color(0xFF1F1E1C),
    secondary = Color(0xFF7A7568),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8DF),
    onSecondaryContainer = Color(0xFF2F2E2B),
    tertiary = Color(0xFFC8553D),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1F1E1C),
    surface = Color(0xFFFBFAF7),
    onSurface = Color(0xFF1F1E1C),
    surfaceVariant = Color(0xFFEFECE5),
    onSurfaceVariant = Color(0xFF6B675E),
    surfaceContainerLow = Color(0xFFF4F2EC),
    surfaceContainer = Color(0xFFF0EDE6),
    surfaceContainerHigh = Color(0xFFEAE7DF),
    outline = Color(0xFFCBC6BA),
    outlineVariant = Color(0xFFE2DED4),
    error = Color(0xFFB3261E),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFECEAE4),
    onPrimary = Color(0xFF151514),
    primaryContainer = Color(0xFF2E2D2A),
    onPrimaryContainer = Color(0xFFECEAE4),
    secondary = Color(0xFFA9A497),
    onSecondary = Color(0xFF151514),
    secondaryContainer = Color(0xFF2A2926),
    onSecondaryContainer = Color(0xFFECEAE4),
    tertiary = Color(0xFFE07A5F),
    background = Color(0xFF131312),
    onBackground = Color(0xFFECEAE4),
    surface = Color(0xFF1A1A18),
    onSurface = Color(0xFFECEAE4),
    surfaceVariant = Color(0xFF2A2926),
    onSurfaceVariant = Color(0xFFA9A497),
    surfaceContainerLow = Color(0xFF1C1C1A),
    surfaceContainer = Color(0xFF21211F),
    surfaceContainerHigh = Color(0xFF2A2A27),
    outline = Color(0xFF4A4843),
    outlineVariant = Color(0xFF34332F),
    error = Color(0xFFF2B8B5),
)

private val base = Typography()
val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AjandaTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(themeMode)
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

object TagPalette {
    val colors: List<Int> = listOf(
        0xFF4A6FA5, 0xFF5B8C5A, 0xFFC8553D, 0xFFD4A24C, 0xFF8E6C8A,
        0xFF3D8A8A, 0xFFB5657A, 0xFF6C757D, 0xFF7B9E3F, 0xFF2E5266,
    ).map { it.toInt() }

    fun forName(name: String): Int = colors[(name.lowercase().hashCode() and 0x7fffffff) % colors.size]
}

fun priorityColor(p: Priority): Color = when (p) {
    Priority.LOW -> Color(0xFF8C8C8C)
    Priority.MEDIUM -> Color(0xFF4A6FA5)
    Priority.HIGH -> Color(0xFFD48A2C)
    Priority.URGENT -> Color(0xFFC8553D)
}
