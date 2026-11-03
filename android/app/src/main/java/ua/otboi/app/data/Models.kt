package ua.otboi.app.data

/** Что делать будильнику, если в момент звонка идёт тревога. */
enum class AlertRule(val id: String, val title: String, val hint: String, val description: String) {
    AFTER_CLEAR(
        "after", "После отбоя", "при тревоге — после отбоя",
        "Если тревога — подождёт отбоя и разбудит, как только он будет"
    ),
    SKIP(
        "skip", "Не будить", "при тревоге — не будить",
        "Если тревога — в этот день вообще не зазвонит"
    ),
    ALWAYS(
        "always", "Будить всегда", "звонит даже при тревоге",
        "Обычный будильник — тревога не важна"
    );

    companion object {
        fun from(id: String?): AlertRule = entries.firstOrNull { it.id == id } ?: AFTER_CLEAR
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
) {
    val timeText: String get() = Fmt.two(hour) + ":" + Fmt.two(minute)
    val daysText: String get() = Days.summary(days)
}

object Days {
    val short = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val weekdays = setOf(1, 2, 3, 4, 5)
    val weekend = setOf(6, 7)
    val all = setOf(1, 2, 3, 4, 5, 6, 7)

    fun summary(days: Set<Int>): String = when {
        days.isEmpty() -> "Один раз"
        days.size == 7 -> "Каждый день"
        days == weekdays -> "Пн–Пт"
        days == weekend -> "Сб, Вс"
        else -> days.sorted().joinToString(", ") { short[it - 1] }
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
    val title: String get() = (if (test) "Тест " else "") + time
}

data class LogEntry(val time: Long, val text: String)

data class AppSettings(
    val stateId: Int = 0,
    val districtId: Int = 0,
    val communityId: Int = 0,
    val placeShort: String = "",
    val placeFull: String = "",
    /** Если выбран район: тревога в соседних районах тоже считается тревогой. */
    val neighborsCount: Boolean = false,
    /** 0 — звонить сразу после отбоя. */
    val delayAfterClearMin: Int = 0,
    val maxWaitHours: Int = 4,
    val snoozeMin: Int = 5,
    val digitStyle: String = "elegant",
    val accent: Long = 0xFFA855F7,
    val channels: List<String> = listOf("deraketaua"),
    val alertsOnly: Boolean = true,
    /** Экономия заряда: пока ждём отбоя, проверять тревогу раз в минуту, а не каждые 20 с. */
    val economy: Boolean = false,
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
