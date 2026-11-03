package ua.otboi.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Всё, что приложение хранит: будильники, настройки, ожидание отбоя, журнал. */
object Store {
    private const val SETTINGS_VERSION = 2
    private lateinit var prefs: SharedPreferences

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _waiting = MutableStateFlow<Waiting?>(null)
    val waiting: StateFlow<Waiting?> = _waiting.asStateFlow()

    private val _reading = MutableStateFlow<Reading?>(null)
    val reading: StateFlow<Reading?> = _reading.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("otboi", Context.MODE_PRIVATE)
        _settings.value = runCatching { parseSettings(prefs.getString("settings", null)) }.getOrDefault(AppSettings())
        _waiting.value = runCatching { parseWaiting(prefs.getString("waiting", null)) }.getOrNull()
        _reading.value = runCatching { parseReading(prefs.getString("reading", null)) }.getOrNull()
        _log.value = runCatching { parseLog(prefs.getString("log", null)) }.getOrDefault(emptyList())
        if (prefs.contains("alarms")) {
            _alarms.value = runCatching { parseAlarms(prefs.getString("alarms", null)) }.getOrDefault(emptyList())
        } else {
            // Первый запуск: пример будильника, выключенный — включишь сам.
            saveAlarms(listOf(Alarm(1, 7, 30, Days.weekdays, false, AlertRule.AFTER_CLEAR)))
        }
    }

    // MARK: будильники

    @Synchronized
    fun saveAlarms(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy<Alarm>({ it.hour }, { it.minute }, { it.id }))
        _alarms.value = sorted
        prefs.edit().putString("alarms", alarmsJson(sorted)).apply()
    }

    fun upsertAlarm(alarm: Alarm) = saveAlarms(_alarms.value.filter { it.id != alarm.id } + alarm)

    fun deleteAlarm(id: Long) = saveAlarms(_alarms.value.filter { it.id != id })

    fun newAlarmId(): Long = (_alarms.value.maxOfOrNull { it.id } ?: 0L) + 1L

    // MARK: настройки

    @Synchronized
    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val s = change(_settings.value)
        _settings.value = s
        prefs.edit().putString("settings", settingsJson(s)).apply()
    }

    // MARK: ожидание отбоя

    @Synchronized
    fun setWaiting(w: Waiting?) {
        _waiting.value = w
        val e = prefs.edit()
        if (w == null) e.remove("waiting") else e.putString("waiting", waitingJson(w))
        e.apply()
    }

    // MARK: последняя проверка тревоги

    @Synchronized
    fun setReading(r: Reading?) {
        _reading.value = r
        val e = prefs.edit()
        if (r == null) e.remove("reading") else e.putString("reading", readingJson(r))
        e.apply()
    }

    // MARK: журнал

    @Synchronized
    fun addLog(text: String) {
        val list = (listOf(LogEntry(System.currentTimeMillis(), text)) + _log.value).take(50)
        _log.value = list
        if (!::prefs.isInitialized) return
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.time).put("s", it.text)) }
        prefs.edit().putString("log", arr.toString()).apply()
    }

    // MARK: JSON

    private fun alarmsJson(list: List<Alarm>): String {
        val arr = JSONArray()
        for (a in list) {
            val days = JSONArray()
            a.days.sorted().forEach { days.put(it) }
            arr.put(
                JSONObject()
                    .put("id", a.id).put("h", a.hour).put("m", a.minute)
                    .put("days", days).put("on", a.enabled).put("rule", a.rule.id)
            )
        }
        return arr.toString()
    }

    private fun parseAlarms(text: String?): List<Alarm> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = JSONArray(text)
        val out = ArrayList<Alarm>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val daysArr = o.optJSONArray("days")
            val days = HashSet<Int>()
            if (daysArr != null) for (j in 0 until daysArr.length()) days += daysArr.optInt(j)
            out += Alarm(
                id = o.optLong("id"),
                hour = o.optInt("h").coerceIn(0, 23),
                minute = o.optInt("m").coerceIn(0, 59),
                days = days.filter { it in 1..7 }.toSet(),
                enabled = o.optBoolean("on", true),
                rule = AlertRule.from(o.optString("rule")),
            )
        }
        return out
    }

    private fun settingsJson(s: AppSettings): String {
        val ch = JSONArray()
        s.channels.forEach { ch.put(it) }
        return JSONObject()
            .put("stateId", s.stateId).put("districtId", s.districtId).put("communityId", s.communityId)
            .put("placeShort", s.placeShort).put("placeFull", s.placeFull)
            .put("neighborsCount", s.neighborsCount)
            .put("delay", s.delayAfterClearMin).put("maxWait", s.maxWaitHours).put("snooze", s.snoozeMin)
            .put("digits", s.digitStyle).put("accent", s.accent)
            .put("channels", ch).put("alertsOnly", s.alertsOnly)
            .put("economy", s.economy)
            .put("v", SETTINGS_VERSION)
            .toString()
    }

    private fun parseSettings(text: String?): AppSettings {
        if (text.isNullOrBlank()) return AppSettings()
        val o = JSONObject(text)
        val d = AppSettings()
        val chArr = o.optJSONArray("channels")
        val channels = ArrayList<String>()
        if (chArr != null) for (i in 0 until chArr.length()) chArr.optString(i).takeIf { it.isNotBlank() }?.let { channels += it }
        return AppSettings(
            stateId = o.optInt("stateId", d.stateId),
            districtId = o.optInt("districtId", d.districtId),
            communityId = o.optInt("communityId", d.communityId),
            placeShort = o.optString("placeShort", d.placeShort),
            placeFull = o.optString("placeFull", d.placeFull),
            neighborsCount = o.optBoolean("neighborsCount", d.neighborsCount),
            // До версии 2 по умолчанию было «через 10 мин после отбоя», теперь — сразу.
            delayAfterClearMin = o.optInt("delay", d.delayAfterClearMin)
                .let { if (o.optInt("v", 1) < 2 && it == 10) 0 else it },
            maxWaitHours = o.optInt("maxWait", d.maxWaitHours),
            snoozeMin = o.optInt("snooze", d.snoozeMin),
            digitStyle = o.optString("digits", d.digitStyle),
            accent = o.optLong("accent", d.accent),
            channels = if (chArr == null) d.channels else channels,
            alertsOnly = o.optBoolean("alertsOnly", d.alertsOnly),
            economy = o.optBoolean("economy", d.economy),
        )
    }

    private fun waitingJson(w: Waiting): String = JSONObject()
        .put("alarmId", w.alarmId).put("time", w.time).put("planned", w.planned)
        .put("deadline", w.deadline).put("clearSince", w.clearSince).put("test", w.test)
        .toString()

    private fun parseWaiting(text: String?): Waiting? {
        if (text.isNullOrBlank()) return null
        val o = JSONObject(text)
        return Waiting(
            alarmId = o.optLong("alarmId"),
            time = o.optString("time"),
            planned = o.optLong("planned"),
            deadline = o.optLong("deadline"),
            clearSince = o.optLong("clearSince"),
            test = o.optBoolean("test"),
        )
    }

    private fun readingJson(r: Reading): String = JSONObject()
        .put("status", r.status.name).put("reason", r.reason).put("at", r.at).put("since", r.since)
        .put("backup", r.backup)
        .toString()

    private fun parseReading(text: String?): Reading? {
        if (text.isNullOrBlank()) return null
        val o = JSONObject(text)
        val status = runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.UNKNOWN)
        return Reading(status, o.optString("reason"), o.optLong("at"), o.optLong("since"), o.optBoolean("backup"))
    }

    private fun parseLog(text: String?): List<LogEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = JSONArray(text)
        val out = ArrayList<LogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += LogEntry(o.optLong("t"), o.optString("s"))
        }
        return out
    }
}
