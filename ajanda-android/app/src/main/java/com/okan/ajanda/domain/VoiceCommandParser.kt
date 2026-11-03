package com.okan.ajanda.domain

import com.okan.ajanda.data.local.Priority
import com.okan.ajanda.data.local.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class ParsedCommand(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val priority: Priority = Priority.MEDIUM,
    val recurrence: Recurrence = Recurrence.NONE,
    val tags: List<String> = emptyList(),
    val reminder: Boolean = false,
)

/**
 * Sesli veya yazılı doğal dil komutlarını göreve dönüştürür (Türkçe).
 *
 * Örnekler:
 *  - "yarın saat 15 müşteri toplantısı #iş acil"
 *  - "her pazartesi spor salonu akşam 7"
 *  - "15 kasım fatura öde hatırlat"
 *  - "3 gün sonra rapor teslimi önemli"
 */
object VoiceCommandParser {

    private val weekdays = listOf(
        "cumartesi" to DayOfWeek.SATURDAY,
        "pazartesi" to DayOfWeek.MONDAY,
        "çarşamba" to DayOfWeek.WEDNESDAY,
        "perşembe" to DayOfWeek.THURSDAY,
        "pazar" to DayOfWeek.SUNDAY,
        "salı" to DayOfWeek.TUESDAY,
        "cuma" to DayOfWeek.FRIDAY,
    )
    private val months = listOf(
        "ocak", "şubat", "mart", "nisan", "mayıs", "haziran",
        "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık",
    )
    private val weekdayAlt = weekdays.joinToString("|") { it.first }
    private val monthAlt = months.joinToString("|")

    private const val L = "(?<=^|\\s)"           // kelime başı
    private const val R = "(?=\\s|$|[.,!?])"      // kelime sonu
    private const val SUFFIX = "(?:'?(?:[dt][ae]|[yn]?[ae]|[yn]?[ıiuü]))?(?: günü)?"

    private fun rx(p: String) = Regex("$L(?:$p)$R")

    private class Work(input: String) {
        var orig: String = " ${input.trim()} "
        var lower: String = orig.lowercase(TR)

        fun take(regex: Regex): MatchResult? {
            val m = regex.find(lower) ?: return null
            orig = orig.replaceRange(m.range, " ")
            lower = lower.replaceRange(m.range, " ")
            return m
        }

        fun takeAll(regex: Regex, onEach: (MatchResult, String) -> Unit) {
            while (true) {
                val m = regex.find(lower) ?: return
                onEach(m, orig.substring(m.range))
                orig = orig.replaceRange(m.range, " ")
                lower = lower.replaceRange(m.range, " ")
            }
        }
    }

    fun parse(input: String, today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): ParsedCommand {
        val w = Work(input)
        var date: LocalDate? = null
        var time: LocalTime? = null
        var priority = Priority.MEDIUM
        var recurrence = Recurrence.NONE
        var reminder = false
        val tags = mutableListOf<String>()

        // 1) #etiketler
        w.takeAll(Regex("$L#([\\p{L}\\p{N}_-]+)")) { _, original ->
            tags += original.trim().removePrefix("#")
        }

        // 2) Tekrar
        w.take(rx("her ($weekdayAlt)$SUFFIX"))?.let { m ->
            recurrence = Recurrence.WEEKLY
            date = nextWeekday(today, dayFor(m.groupValues[1]), includeToday = true)
        }
        if (recurrence == Recurrence.NONE) {
            when {
                w.take(rx("her gün|her sabah|her akşam|günlük")) != null -> recurrence = Recurrence.DAILY
                w.take(rx("her hafta|haftalık|haftada bir")) != null -> recurrence = Recurrence.WEEKLY
                w.take(rx("her ay|aylık|ayda bir")) != null -> recurrence = Recurrence.MONTHLY
                w.take(rx("her yıl|yıllık|yılda bir")) != null -> recurrence = Recurrence.YEARLY
            }
        }

        // 3) Öncelik
        when {
            w.take(rx("çok acil|acil|acilen|ivedi")) != null -> priority = Priority.URGENT
            w.take(rx("çok önemli|önemli|öncelikli|yüksek öncelik(?:li)?")) != null -> priority = Priority.HIGH
            w.take(rx("düşük öncelik(?:li)?|önemsiz|acelesi yok")) != null -> priority = Priority.LOW
        }
        w.take(Regex("$L(!{1,3})$R"))?.let { m ->
            priority = when (m.groupValues[1].length) { 1 -> Priority.HIGH; else -> Priority.URGENT }
        }

        // 4) Hatırlatıcı
        if (w.take(rx("bana hatırlat|hatırlat(?:ıcı kur|ır mısın|ma)?")) != null) reminder = true

        // 5) Günün bölümü (saat yorumunu etkiler)
        var pod: String? = null
        var podHour: String? = null
        var podMinute = ""
        w.take(rx("(öğleden sonra|sabah(?:ı)?|öğle(?:n|yin)?|akşam(?:ı|leyin)?|gece(?:si)?)(?: (?:saat )?(\\d{1,2})(?:[:.](\\d{2}))?$SUFFIX)?"))?.let {
            pod = it.groupValues[1]
            podHour = it.groupValues[2].ifEmpty { null }
            podMinute = it.groupValues[3]
        }
        podHour?.let { time = buildTime(it, podMinute, pod) }

        // 6) Saat
        if (time == null) w.take(rx("saat (\\d{1,2})(?:[:.](\\d{2}))?$SUFFIX"))?.let { m ->
            time = buildTime(m.groupValues[1], m.groupValues[2], pod)
        } ?: w.take(rx("(\\d{1,2}):(\\d{2})$SUFFIX"))?.let { m ->
            time = buildTime(m.groupValues[1], m.groupValues[2], pod)
        }
        if (time == null && pod != null) {
            time = when {
                pod!!.startsWith("sabah") -> LocalTime.of(9, 0)
                pod!!.startsWith("öğleden") -> LocalTime.of(15, 0)
                pod!!.startsWith("öğle") -> LocalTime.of(12, 0)
                pod!!.startsWith("akşam") -> LocalTime.of(19, 0)
                else -> LocalTime.of(22, 0)
            }
        }

        // 7) Tarih
        if (date == null) {
            date = when {
                w.take(rx("bugün$SUFFIX|bu akşam")) != null -> today
                w.take(rx("yarından sonra|öbür gün$SUFFIX|ertesi gün")) != null -> today.plusDays(2)
                w.take(rx("yarın$SUFFIX")) != null -> today.plusDays(1)
                else -> null
            }
        }
        if (date == null) {
            w.take(rx("(\\d{1,3}) gün sonra"))?.let { date = today.plusDays(it.groupValues[1].toLong()) }
        }
        if (date == null) {
            w.take(rx("(\\d{1,2}) hafta sonra"))?.let { date = today.plusWeeks(it.groupValues[1].toLong()) }
        }
        if (date == null) {
            w.take(rx("(?:gelecek|önümüzdeki|haftaya) ($weekdayAlt)$SUFFIX"))?.let { m ->
                date = nextWeekday(today.plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), dayFor(m.groupValues[1]), true)
            }
        }
        if (date == null) {
            w.take(rx("($weekdayAlt)$SUFFIX"))?.let { m ->
                date = nextWeekday(today, dayFor(m.groupValues[1]), includeToday = false)
            }
        }
        if (date == null) {
            w.take(rx("haftaya|gelecek hafta|önümüzdeki hafta"))?.let { date = today.plusWeeks(1) }
        }
        if (date == null) {
            w.take(rx("ay sonu(?:na)?|ayın sonu(?:na)?"))?.let { date = today.withDayOfMonth(today.lengthOfMonth()) }
        }
        if (date == null) {
            w.take(rx("(\\d{1,2}) ($monthAlt)(?:\\S*)?(?: (\\d{4}))?"))?.let { m ->
                val month = months.indexOf(m.groupValues[2]) + 1
                date = safeDate(m.groupValues[3].toIntOrNull(), month, m.groupValues[1].toInt(), today)
            }
        }
        if (date == null) {
            w.take(rx("(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?"))?.let { m ->
                val y = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                date = safeDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt(), today)
            }
        }

        // Saat verilip tarih verilmediyse: bugün (saat geçtiyse yarın)
        if (date == null && time != null) {
            date = if (time!! > now) today else today.plusDays(1)
        }
        if (date == null && recurrence != Recurrence.NONE) date = today
        if (time != null) reminder = true

        // 8) Başlığı temizle
        var title = w.orig
            .replace(Regex("(?i)(?<=^|\\s)(görev ekle|not al|ekle|görevi|için)(?=\\s|$)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(',', '.', '-', ' ')
        if (title.isBlank()) title = "Yeni görev"
        title = title.replaceFirstChar { it.titlecase(TR) }

        return ParsedCommand(title, date, time, priority, recurrence, tags.distinct(), reminder)
    }

    private fun dayFor(name: String): DayOfWeek = weekdays.first { it.first == name }.second

    private fun nextWeekday(from: LocalDate, day: DayOfWeek, includeToday: Boolean): LocalDate =
        if (includeToday) from.with(TemporalAdjusters.nextOrSame(day)) else from.with(TemporalAdjusters.next(day))

    private fun buildTime(h: String, m: String, pod: String?): LocalTime? {
        var hour = h.toIntOrNull() ?: return null
        val minute = m.toIntOrNull() ?: 0
        if (pod != null && hour < 12 &&
            (pod.startsWith("akşam") || pod.startsWith("öğleden") || (pod.startsWith("gece") && hour >= 6))
        ) hour += 12
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun safeDate(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? = runCatching {
        val d = LocalDate.of(year ?: today.year, month, day)
        if (year == null && d < today) d.plusYears(1) else d
    }.getOrNull()
}
