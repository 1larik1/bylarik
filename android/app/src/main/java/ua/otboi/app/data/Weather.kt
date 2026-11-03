package ua.otboi.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.otboi.app.i18n.T
import ua.otboi.app.net.Http
import java.util.Locale

/**
 * Погода на сегодня с open-meteo.com (без ключа и без геолокации): берём центр выбранной области.
 * Запрос делается, только когда открыт главный экран (не чаще раза в 30 минут) и когда звонит будильник.
 */
object WeatherRepo {
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    private val mutex = Mutex()

    fun fresh(stateId: Int, maxAgeMs: Long): Boolean {
        val w = Store.weather.value ?: return false
        return w.stateId == stateId && System.currentTimeMillis() - w.at < maxAgeMs
    }

    suspend fun refresh(maxAgeMs: Long = 30 * 60_000L) = mutex.withLock {
        val s = Store.settings.value
        if (!s.weather || s.stateId <= 0) return@withLock
        if (fresh(s.stateId, maxAgeMs)) return@withLock
        val city = Oblasts.byId(s.stateId)?.city ?: return@withLock
        loading.value = true
        try {
            val w = withContext(Dispatchers.IO) { fetch(city.lat, city.lon, s.stateId) }
            if (w != null) {
                Store.setWeather(w)
                failed.value = false
            } else {
                failed.value = true
            }
        } finally {
            loading.value = false
        }
    }

    private fun fetch(lat: Double, lon: Double, stateId: Int): WeatherData? = try {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&hourly=apparent_temperature,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,uv_index_max,wind_speed_10m_max" +
                "&wind_speed_unit=ms&timezone=auto&forecast_days=1",
            lat, lon,
        )
        val res = Http.get(url, "bylarik-android", "application/json")
        if (res.code == 200) parse(res.body, stateId, System.currentTimeMillis()) else null
    } catch (_: Exception) {
        null
    }

    fun parse(body: String, stateId: Int, now: Long): WeatherData? {
        val o = JSONObject(body)
        val cur = o.optJSONObject("current") ?: return null
        val daily = o.optJSONObject("daily") ?: return null
        val hourly = o.optJSONObject("hourly")
        fun d(name: String): Double = daily.optJSONArray(name)?.optDouble(0) ?: Double.NaN
        fun di(name: String): Int = daily.optJSONArray(name)?.optInt(0) ?: 0

        // Средняя «ощущаемая» температура утром (7–9) и днём (10–18).
        var morning = Double.NaN
        var day = Double.NaN
        if (hourly != null) {
            val times = hourly.optJSONArray("time")
            val feels = hourly.optJSONArray("apparent_temperature")
            if (times != null && feels != null) {
                val m = ArrayList<Double>()
                val dd = ArrayList<Double>()
                for (i in 0 until minOf(times.length(), feels.length())) {
                    val hour = times.optString(i).substringAfter('T').take(2).toIntOrNull() ?: continue
                    val f = feels.optDouble(i)
                    if (f.isNaN()) continue
                    if (hour in 7..9) m += f
                    if (hour in 10..18) dd += f
                }
                if (m.isNotEmpty()) morning = m.average()
                if (dd.isNotEmpty()) day = dd.average()
            }
        }
        val feelsNow = cur.optDouble("apparent_temperature")
        if (feelsNow.isNaN()) return null
        return WeatherData(
            at = now,
            stateId = stateId,
            temp = cur.optDouble("temperature_2m"),
            feels = feelsNow,
            code = cur.optInt("weather_code"),
            wind = cur.optDouble("wind_speed_10m"),
            min = d("temperature_2m_min"),
            max = d("temperature_2m_max"),
            precipProb = di("precipitation_probability_max"),
            uvMax = d("uv_index_max"),
            windMax = d("wind_speed_10m_max"),
            morningFeels = if (morning.isNaN()) feelsNow else morning,
            dayFeels = if (day.isNaN()) feelsNow else day,
            dayCode = di("weather_code"),
        )
    }
}

/** Совет, что надеть, по погоде на сегодня. */
object Wear {
    private val rainCodes = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
    private val snowCodes = setOf(71, 73, 75, 77, 85, 86)

    /** 0 — сильный мороз … 7 — жара. */
    fun band(feels: Double): Int = when {
        feels <= -15 -> 0
        feels <= -5 -> 1
        feels <= 3 -> 2
        feels <= 10 -> 3
        feels <= 16 -> 4
        feels <= 22 -> 5
        feels <= 28 -> 6
        else -> 7
    }

    /** На что ориентироваться: до полудня — на утро и день, потом — на «сейчас». */
    fun baseFeels(w: WeatherData, hourNow: Int): Double =
        if (hourNow < 12) w.morningFeels * 0.6 + w.dayFeels * 0.4 else minOf(w.feels, w.dayFeels)

    fun advice(w: WeatherData, hourNow: Int): String {
        val base = baseFeels(w, hourNow)
        val b = band(base)
        val parts = arrayListOf(T.wearBand(b))
        val snowy = w.dayCode in snowCodes || w.code in snowCodes
        val rainy = w.dayCode in rainCodes || w.code in rainCodes || w.precipProb >= 50
        if (snowy) parts += T.wearWaterproof
        else if (rainy) parts += T.wearUmbrella
        if (!w.windMax.isNaN() && w.windMax >= 10) parts += T.wearWind
        if (!w.uvMax.isNaN() && w.uvMax >= 6 && b >= 5) parts += T.wearSun
        if (hourNow < 12 && w.dayFeels - w.morningFeels >= 7) parts += T.wearLayers
        return parts.joinToString(", ")
    }
}
