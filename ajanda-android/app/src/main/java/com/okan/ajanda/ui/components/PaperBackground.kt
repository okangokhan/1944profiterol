package com.okan.ajanda.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.okan.ajanda.data.prefs.PaperStyle

/** Çizgili / kareli / noktalı kağıt dokusu. */
fun Modifier.paperBackground(style: PaperStyle, lineColor: Color): Modifier =
    if (style == PaperStyle.PLAIN) this else drawBehind {
        val step = 24.dp.toPx()
        val stroke = 1.dp.toPx() * 0.7f
        when (style) {
            PaperStyle.LINED -> {
                var y = step * 2
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                    y += step
                }
                // kenar boşluğu çizgisi
                val margin = 28.dp.toPx()
                drawLine(lineColor.copy(alpha = lineColor.alpha * 1.6f), Offset(margin, 0f), Offset(margin, size.height), stroke)
            }
            PaperStyle.GRID -> {
                var x = 0f
                while (x < size.width) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke); x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke); y += step
                }
            }
            PaperStyle.DOTTED -> {
                val r = 1.2.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(lineColor.copy(alpha = (lineColor.alpha * 2.2f).coerceAtMost(1f)), r, Offset(x, y)); x += step
                    }
                    y += step
                }
            }
            PaperStyle.PLAIN -> Unit
        }
    }
