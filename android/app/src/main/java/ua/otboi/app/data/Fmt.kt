package ua.otboi.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    private val ru: Locale = Locale.forLanguageTag("ru")
    private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm", ru)

    fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

    fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun hm(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val z = zoned(ms)
        return two(z.hour) + ":" + two(z.minute)
    }

    fun dateTime(ms: Long): String = dateTime.format(zoned(ms))

    /** «13 ч 17 мин», «45 мин», «2 дн 3 ч». */
    fun duration(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val days = totalMin / (60 * 24)
        val hours = (totalMin / 60) % 24
        val minutes = totalMin % 60
        return when {
            days > 0 -> "$days дн $hours ч"
            hours > 0 -> "$hours ч $minutes мин"
            else -> "$minutes мин"
        }
    }

    fun until(ms: Long): String = if (ms < 60_000L) "меньше минуты" else "через " + duration(ms + 59_999L)

    fun ago(ms: Long, now: Long): String {
        if (ms <= 0L) return ""
        val min = (now - ms) / 60_000L
        return when {
            min < 1 -> "только что"
            min < 60 -> "$min мин назад"
            min < 24 * 60 -> "${min / 60} ч назад"
            else -> dateTime(ms)
        }
    }

    /** «Сегодня, понедельник», «Завтра, вторник», «Четверг, 5 ноября». */
    fun dayWord(target: ZonedDateTime, now: ZonedDateTime): String {
        val diff = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
        val weekday = target.dayOfWeek.getDisplayName(TextStyle.FULL, ru)
        return when (diff) {
            0L -> "Сегодня, $weekday"
            1L -> "Завтра, $weekday"
            else -> weekday.replaceFirstChar { it.titlecase(ru) } + ", " + dayMonth.format(target)
        }
    }
}
