package com.okan.ajanda.ui.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.okan.ajanda.domain.DateText
import com.okan.ajanda.domain.TR
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Aylık matris şablonunu A4 yatay PDF veya PNG olarak dışa aktarır. */
object MonthExporter {
    enum class Format(val ext: String, val mime: String) { PDF("pdf", "application/pdf"), PNG("png", "image/png") }

    private const val PAGE_W = 842f   // A4 yatay (pt)
    private const val PAGE_H = 595f

    fun export(context: Context, state: CalendarUiState, format: Format): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ajanda-${state.month}.${format.ext}")
        when (format) {
            Format.PDF -> {
                val doc = PdfDocument()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
                draw(page.canvas, state)
                doc.finishPage(page)
                file.outputStream().use { doc.writeTo(it) }
                doc.close()
            }
            Format.PNG -> {
                val scale = 2.5f
                val bmp = Bitmap.createBitmap((PAGE_W * scale).toInt(), (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.scale(scale, scale)
                draw(c, state)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun draw(canvas: Canvas, state: CalendarUiState) {
        val ink = Color.rgb(0x2F, 0x2E, 0x2B)
        val faint = Color.rgb(0xB8, 0xB3, 0xA7)
        val line = Color.rgb(0xCF, 0xCA, 0xBE)
        canvas.drawColor(Color.WHITE)

        val margin = 32f
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 30f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = faint; textSize = 14f; typeface = Typeface.SERIF }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 9f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.15f
        }
        val dayNum = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val taskText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = ink }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = line }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        val monthName = DateText.monthName(state.month).uppercase(TR)
        canvas.drawText(monthName, margin, margin + 24f, title)
        canvas.drawText(state.month.year.toString(), margin + title.measureText(monthName) + 12f, margin + 24f, sub)

        val gridTop = margin + 48f
        val gridLeft = margin
        val gridW = PAGE_W - margin * 2
        val headerH = 18f
        val rows = state.days.size / 7
        val cellW = gridW / 7f
        val cellH = (PAGE_H - gridTop - headerH - margin) / rows

        MonthMatrix.weekdays(state.mondayFirst).forEachIndexed { i, d ->
            canvas.drawText(DateText.weekdayShort(d), gridLeft + i * cellW + 6f, gridTop + 12f, head)
        }
        val top = gridTop + headerH
        canvas.drawRect(RectF(gridLeft, top, gridLeft + gridW, top + rows * cellH), stroke)
        for (c in 1 until 7) canvas.drawLine(gridLeft + c * cellW, top, gridLeft + c * cellW, top + rows * cellH, stroke)
        for (r in 1 until rows) canvas.drawLine(gridLeft, top + r * cellH, gridLeft + gridW, top + r * cellH, stroke)

        val today = LocalDate.now()
        state.days.forEachIndexed { index, date ->
            val x = gridLeft + (index % 7) * cellW
            val y = top + (index / 7) * cellH
            val inMonth = YearMonth.from(date) == state.month
            dayNum.color = if (inMonth) ink else faint
            if (date == today) {
                fill.color = ink
                canvas.drawCircle(x + 12f, y + 11f, 8f, fill)
                dayNum.color = Color.WHITE
            }
            val n = date.dayOfMonth.toString()
            canvas.drawText(n, x + 12f - dayNum.measureText(n) / 2f, y + 14.5f, dayNum)

            val items = state.byDate[date].orEmpty()
            val lineH = 10f
            val maxLines = ((cellH - 24f) / lineH).toInt().coerceAtLeast(0)
            val shown = if (items.size > maxLines) maxLines - 1 else items.size
            items.take(shown.coerceAtLeast(0)).forEachIndexed { i, occ ->
                val ly = y + 24f + i * lineH
                fill.color = occ.details.primaryColor ?: ink
                canvas.drawRoundRect(RectF(x + 5f, ly, x + 7.5f, ly + 7.5f), 1f, 1f, fill)
                taskText.color = if (occ.task.isCompleted) faint else ink
                taskText.isStrikeThruText = occ.task.isCompleted
                val t = TextUtils.ellipsize(occ.task.title, taskText, cellW - 16f, TextUtils.TruncateAt.END).toString()
                canvas.drawText(t, x + 10f, ly + 7f, taskText)
            }
            if (items.size > shown && maxLines > 0) {
                taskText.color = faint; taskText.isStrikeThruText = false
                canvas.drawText("+${items.size - shown} daha", x + 10f, y + 24f + shown * lineH + 7f, taskText)
            }
        }
        sub.textSize = 8f
        canvas.drawText("Ajanda ile oluşturuldu", PAGE_W - margin - sub.measureText("Ajanda ile oluşturuldu"), PAGE_H - 14f, sub)
    }
}
