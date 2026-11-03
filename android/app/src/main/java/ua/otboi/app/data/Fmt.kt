package ua.otboi.app.data

import ua.otboi.app.i18n.T
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object Fmt {
    fun two(n: Int): String = if (n < 10) "0$n" else n.toString()

    fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun hm(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val z = zoned(ms)
        return two(z.hour) + ":" + two(z.minute)
    }

    fun dateTime(ms: Long): String = DateTimeFormatter.ofPattern("dd.MM HH:mm", T.locale).format(zoned(ms))

    /** «13 год 17 хв», «45 мин», «2d 3h» — на языке телефона. */
    fun duration(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        return T.dur(totalMin / (60 * 24), (totalMin / 60) % 24, totalMin % 60)
    }

    fun until(ms: Long): String = if (ms < 60_000L) T.lessThanMinute else T.inTime(duration(ms + 59_999L))

    fun ago(ms: Long, now: Long): String {
        if (ms <= 0L) return ""
        val min = (now - ms) / 60_000L
        return when {
            min < 1 -> T.justNow
            min < 60 -> T.minAgo(min)
            min < 24 * 60 -> T.hoursAgo(min / 60)
            else -> dateTime(ms)
        }
    }

    /** «Сегодня, понедельник», «Tomorrow, Tuesday», «Четвер, 5 листопада». */
    fun dayWord(target: ZonedDateTime, now: ZonedDateTime): String {
        val diff = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
        val loc = T.locale
        val weekday = target.dayOfWeek.getDisplayName(TextStyle.FULL, loc)
        return when (diff) {
            0L -> "${T.today}, $weekday"
            1L -> "${T.tomorrow}, $weekday"
            else -> weekday.replaceFirstChar { it.titlecase(loc) } + ", " +
                DateTimeFormatter.ofPattern(T.dayMonthPattern, loc).format(target)
        }
    }

    /** «+3°», «−1°», «0°». plus = false — без плюса (для крупных цифр). */
    fun temp(c: Double, plus: Boolean = true): String {
        val r = c.roundToInt()
        return when {
            r > 0 -> if (plus) "+$r°" else "$r°"
            r < 0 -> "−${abs(r)}°"
            else -> "0°"
        }
    }
}
