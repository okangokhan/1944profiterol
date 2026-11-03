package com.okan.ajanda.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Eriyen çubuk: kalan süre azaldıkça kısalır, ucundan damlalar sarkar.
 * [fraction] 1 = süre yeni başladı, 0 = son tarih geldi/geçti.
 */
@Composable
fun MeltingBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "melt")
    val drip by rememberInfiniteTransition(label = "drip").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dripLen"
    )
    Canvas(modifier.fillMaxWidth().height(barHeight + 9.dp)) {
        val h = barHeight.toPx()
        val r = h / 2f
        drawRoundRect(color.copy(alpha = 0.13f), size = Size(size.width, h), cornerRadius = CornerRadius(r))
        if (animated <= 0f) return@Canvas
        val w = (size.width * animated).coerceAtLeast(h)
        drawRoundRect(color, size = Size(w, h), cornerRadius = CornerRadius(r))
        if (animated < 0.995f) {
            // Uçtaki büyük damla
            val dw = h * 0.55f
            val maxLen = 8.dp.toPx()
            val x1 = w - r - dw / 2f
            val len1 = maxLen * drip
            drawRoundRect(color, Offset(x1, r), Size(dw, r + len1 - dw / 2f), CornerRadius(dw / 2f))
            drawCircle(color, dw * 0.62f, Offset(x1 + dw / 2f, h + len1 - dw * 0.62f))
            // Ortadaki küçük damla
            if (w > 60.dp.toPx()) {
                val x2 = w * 0.58f
                val dw2 = h * 0.38f
                val len2 = maxLen * 0.45f * (1.35f - drip)
                drawRoundRect(color, Offset(x2, r), Size(dw2, r + len2), CornerRadius(dw2 / 2f))
            }
        }
    }
}
