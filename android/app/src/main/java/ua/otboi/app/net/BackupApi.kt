package ua.otboi.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.otboi.app.data.Reading
import ua.otboi.app.data.Status

/**
 * Запасной источник тревог: карта vadimklimenko.com (без ключа).
 * Данные по областям и районам (без громад). Используется, только если основной
 * источник (siren.pp.ua) не ответил, — чтобы не будить «на всякий случай» без нужды.
 */
object BackupApi {
    const val NAME = "vadimklimenko.com"
    private const val URL = "https://vadimklimenko.com/map/statuses.json"

    suspend fun status(stateName: String, districtName: String?): Reading = withContext(Dispatchers.IO) {
        try {
            val res = Http.get(URL, "Otboi-Android/1.0", "application/json")
            if (res.code != 200) {
                Reading(Status.UNKNOWN, "запасной источник ответил ${res.code}")
            } else {
                evaluate(res.body, stateName, districtName)
            }
        } catch (e: Exception) {
            Reading(Status.UNKNOWN, "запасной источник: " + Http.shortError(e))
        }
    }

    /** «Кам’янський район» и «Камянський район» — одно и то же. */
    fun norm(name: String): String = name.lowercase()
        .replace("м.", "м ")
        .filter { it.isLetter() || it == ' ' }
        .split(' ').filter { it.isNotBlank() }.joinToString(" ")

    /**
     * stateName — «Вінницька область» / «м. Київ»; districtName — «Вінницький район» или null (вся область).
     */
    fun evaluate(body: String, stateName: String, districtName: String?): Reading {
        val states = JSONObject(body).optJSONObject("states")
            ?: return Reading(Status.UNKNOWN, "запасной источник: непонятный ответ")
        val key = states.keys().asSequence().firstOrNull { norm(it) == norm(stateName) }
            ?: return Reading(Status.UNKNOWN, "запасной источник не знает «$stateName»")
        val st = states.getJSONObject(key)
        if (st.optBoolean("enabled")) {
            return Reading(Status.ALERT, backup = true, since = parseTime(st.optString("enabled_at")))
        }
        val districts = st.optJSONObject("districts")
        val on = HashMap<String, Long>()
        val all = HashSet<String>()
        if (districts != null) {
            for (k in districts.keys()) {
                val d = districts.optJSONObject(k) ?: continue
                all += norm(k)
                if (d.optBoolean("enabled")) on[norm(k)] = parseTime(d.optString("enabled_at"))
            }
        }
        fun sinceOf(v: Collection<Long>): Long = v.filter { it > 0L }.minOrNull() ?: 0L
        if (districtName.isNullOrBlank()) {
            return if (on.isEmpty()) {
                Reading(Status.CLEAR, backup = true)
            } else {
                Reading(Status.PARTIAL, backup = true, since = sinceOf(on.values))
            }
        }
        val mine = norm(districtName)
        return when {
            on.containsKey(mine) -> Reading(Status.ALERT, backup = true, since = on[mine] ?: 0L)
            on.isEmpty() -> Reading(Status.CLEAR, backup = true)
            // Тревога где-то в области, а своего района в списке нет — не угадываем.
            mine !in all -> Reading(Status.UNKNOWN, "запасной источник не знает «$districtName»")
            else -> Reading(Status.PARTIAL, backup = true, since = sinceOf(on.values))
        }
    }
}
