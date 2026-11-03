package ua.otboi.app.data

import ua.otboi.app.i18n.T

/** Что делать будильнику, если в момент звонка идёт тревога. */
enum class AlertRule(val id: String) {
    AFTER_CLEAR("after"),
    SKIP("skip"),
    ALWAYS("always");

    val title: String
        get() = when (this) {
            AFTER_CLEAR -> T.ruleAfterTitle
            SKIP -> T.ruleSkipTitle
            ALWAYS -> T.ruleAlwaysTitle
        }
    val hint: String
        get() = when (this) {
            AFTER_CLEAR -> T.ruleAfterHint
            SKIP -> T.ruleSkipHint
            ALWAYS -> T.ruleAlwaysHint
        }
    val description: String
        get() = when (this) {
            AFTER_CLEAR -> T.ruleAfterDesc
            SKIP -> T.ruleSkipDesc
            ALWAYS -> T.ruleAlwaysDesc
        }

    companion object {
        fun from(id: String?): AlertRule = entries.firstOrNull { it.id == id } ?: AFTER_CLEAR
    }
}

/** Звук будильника. uri: "" — мелодия будильника телефона, "none" — только вибрация, иначе адрес файла/мелодии. */
data class Sound(
    val uri: String = "",
    val name: String = "",
    val gradual: Boolean = true,
    val vibrate: Boolean = true,
    /** Свой файл (а не мелодия телефона). */
    val file: Boolean = false,
) {
    val vibrationOnly: Boolean get() = uri == NONE
    val displayName: String
        get() = when {
            vibrationOnly -> T.soundVibrationOnly
            uri.isBlank() -> T.soundDefault
            name.isNotBlank() -> name
            else -> T.soundDefault
        }

    companion object {
        const val NONE = "none"
    }
}

/** Будильник. days: 1 = понедельник … 7 = воскресенье; пусто — один раз. */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val enabled: Boolean,
    val rule: AlertRule,
    val sound: Sound = Sound(),
) {
    val timeText: String get() = Fmt.two(hour) + ":" + Fmt.two(minute)
    val daysText: String get() = Days.summary(days)
}

object Days {
    val weekdays = setOf(1, 2, 3, 4, 5)
    val weekend = setOf(6, 7)
    val all = setOf(1, 2, 3, 4, 5, 6, 7)

    fun short(day: Int): String = T.daysShort[(day - 1).coerceIn(0, 6)]

    fun summary(days: Set<Int>): String = when {
        days.isEmpty() -> T.once
        days.size == 7 -> T.everyDay
        days == weekdays -> T.weekdaysRange
        days == weekend -> T.weekendShort
        else -> days.sorted().joinToString(", ") { short(it) }
    }
}

/** Будильник сработал во время тревоги и ждёт отбоя. */
data class Waiting(
    val alarmId: Long,
    val time: String,
    val planned: Long,
    val deadline: Long,
    val clearSince: Long = 0L,
    val test: Boolean = false,
) {
    val title: String get() = (if (test) T.testWord + " " else "") + time
}

data class LogEntry(val time: Long, val text: String)

data class AppSettings(
    val stateId: Int = 0,
    val districtId: Int = 0,
    val communityId: Int = 0,
    /** Названия выбранного района и громады (по-украински, как в источнике). */
    val districtName: String = "",
    val communityName: String = "",
    /** Старые сохранённые подписи места (до версии 1.3) — запасной вариант для показа. */
    val placeShort: String = "",
    val placeFull: String = "",
    /** Если выбран район: тревога в соседних районах тоже считается тревогой. */
    val neighborsCount: Boolean = false,
    /** 0 — звонить сразу после отбоя. */
    val delayAfterClearMin: Int = 0,
    val maxWaitHours: Int = 4,
    val snoozeMin: Int = 5,
    val digitStyle: String = "elegant",
    val themeId: String = "night",
    val channels: List<String> = listOf("deraketaua"),
    val alertsOnly: Boolean = true,
    /** Экономия заряда: пока ждём отбоя, проверять тревогу раз в минуту, а не каждые 20 с. */
    val economy: Boolean = false,
    /** Показывать погоду и совет, что надеть. */
    val weather: Boolean = true,
)

enum class Status { CLEAR, PARTIAL, ALERT, UNKNOWN }

/** Результат проверки тревоги. since — когда началась (если известно). */
data class Reading(
    val status: Status,
    val reason: String = "",
    val at: Long = System.currentTimeMillis(),
    val since: Long = 0L,
    /** Ответ запасного источника (основной не ответил). */
    val backup: Boolean = false,
)

/** Погода на сегодня для выбранной области. Температуры в °C. */
data class WeatherData(
    val at: Long,
    val stateId: Int,
    val temp: Double,
    val feels: Double,
    val code: Int,
    val wind: Double,
    val min: Double,
    val max: Double,
    val precipProb: Int,
    val uvMax: Double,
    val windMax: Double,
    val morningFeels: Double,
    val dayFeels: Double,
    val dayCode: Int,
)
