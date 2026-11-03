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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.prefs.ThemeMode
import com.okan.ajanda.domain.Urgency

// Yumuşak, açık tonlu palet
val LightColors = lightColorScheme(
    primary = Color(0xFF2D54D0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6ECFC),
    onPrimaryContainer = Color(0xFF1B3594),
    secondary = Color(0xFF6B7280),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEFF3),
    onSecondaryContainer = Color(0xFF1F2328),
    tertiary = Color(0xFF12908E),
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF16181C),
    surface = Color.White,
    onSurface = Color(0xFF16181C),
    surfaceVariant = Color(0xFFEFF0F3),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF1F2F5),
    surfaceContainerHigh = Color(0xFFEBEDF0),
    surfaceContainerHighest = Color(0xFFE4E6EA),
    inverseSurface = Color(0xFF16181C),
    inverseOnSurface = Color.White,
    outline = Color(0xFFD5D8DE),
    outlineVariant = Color(0xFFE8EAEE),
    error = Color(0xFFC2412D),
    errorContainer = Color(0xFFFBE4DF),
    onErrorContainer = Color(0xFF8C2A1A),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8EA8FF),
    onPrimary = Color(0xFF0E1A44),
    primaryContainer = Color(0xFF233366),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFF9AA0A8),
    onSecondary = Color(0xFF111317),
    secondaryContainer = Color(0xFF2A2E35),
    onSecondaryContainer = Color(0xFFE8EAED),
    tertiary = Color(0xFF4FC3C1),
    background = Color(0xFF111317),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1B1E23),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF252930),
    onSurfaceVariant = Color(0xFF9AA0A8),
    surfaceContainerLowest = Color(0xFF15171B),
    surfaceContainerLow = Color(0xFF1B1E23),
    surfaceContainer = Color(0xFF22262C),
    surfaceContainerHigh = Color(0xFF2A2E35),
    surfaceContainerHighest = Color(0xFF33373F),
    inverseSurface = Color(0xFFE8EAED),
    inverseOnSurface = Color(0xFF111317),
    outline = Color(0xFF3A3F47),
    outlineVariant = Color(0xFF2A2E35),
    error = Color(0xFFFF8A73),
    errorContainer = Color(0xFF5C2219),
    onErrorContainer = Color(0xFFFFDAD3),
)

private val base = Typography()
val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
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
        0xFF2F4FD8, 0xFF12908E, 0xFF8A55D4, 0xFFD2691E, 0xFF2E8B57,
        0xFF6B7280, 0xFFD6457A, 0xFFD9A21B, 0xFFD2453B, 0xFF3E5C76,
    ).map { it.toInt() }

    fun forName(name: String): Int = colors[(name.lowercase().hashCode() and 0x7fffffff) % colors.size]
}

fun priorityColor(p: Priority): Color = when (p) {
    Priority.LOW -> Color(0xFF9AA0A8)
    Priority.MEDIUM -> Color(0xFF2D54D0)
    Priority.HIGH -> Color(0xFFE0892B)
    Priority.URGENT -> Color(0xFFD2453B)
}

/** Eriyen çubuk renkleri. */
fun urgencyColor(u: Urgency, calm: Color): Color = when (u) {
    Urgency.CALM -> calm
    Urgency.SOON -> Color(0xFFE0A030)
    Urgency.URGENT -> Color(0xFFE0603F)
    Urgency.OVERDUE -> Color(0xFFC2412D)
}
