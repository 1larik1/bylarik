package ua.otboi.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.otboi.app.data.Status
import ua.otboi.app.net.AlertApi
import ua.otboi.app.net.RegionNode
import ua.otboi.app.net.Telegram

/** Логика без сети: дерево регионов, статус, фильтр новостей, имена каналов. */
class LogicTest {
    private val tree = listOf(
        RegionNode(
            4, "Вінницька область", "State",
            listOf(
                RegionNode(36, "Вінницький район", "District", listOf(RegionNode(155, "Вінницька громада", "Community", emptyList()))),
                RegionNode(37, "Гайсинський район", "District", emptyList()),
            )
        )
    )

    @Test
    fun evaluate() {
        val state = tree[0]
        assertEquals(Status.CLEAR, AlertApi.evaluate(emptyMap(), state, 0, 0).status)
        assertEquals(Status.ALERT, AlertApi.evaluate(mapOf(4 to 0L), state, 36, 155).status)
        assertEquals(Status.PARTIAL, AlertApi.evaluate(mapOf(37 to 0L), state, 0, 0).status)
        assertEquals(Status.PARTIAL, AlertApi.evaluate(mapOf(37 to 0L), state, 36, 0).status)
        assertEquals(Status.ALERT, AlertApi.evaluate(mapOf(36 to 0L), state, 36, 155).status)
        assertEquals(Status.ALERT, AlertApi.evaluate(mapOf(155 to 0L), state, 36, 0).status)
    }

    @Test
    fun parseAlerts() {
        val body = """[{"regionId":"4","regionType":"State","activeAlerts":[]},
            {"regionId":"36","activeAlerts":[{"regionId":"36","type":"AIR","lastUpdate":"2026-11-02T19:06:00Z"}]},
            {"regionId":"37","activeAlerts":[{"regionId":"37","type":"ARTILLERY"}]}]"""
        val r = AlertApi.parseAlerts(body)
        assertTrue(r.ok)
        assertEquals(setOf(36), r.alerts.keys)
        assertTrue((r.alerts[36] ?: 0L) > 0L)
        assertTrue(AlertApi.parseAlerts("[]").ok)
    }

    @Test
    fun news() {
        assertTrue(Telegram.isAlertNews("Повітряна тривога у Вінницькій області"))
        assertTrue(Telegram.isAlertNews("Шахеди курсом на Київщину"))
        assertTrue(Telegram.isAlertNews("Загроза КАБ для Харківщини"))
        assertFalse(Telegram.isAlertNews("У кабінеті мера відбулася нарада"))
        assertEquals("deraketaua", Telegram.cleanChannel("https://t.me/s/deraketaua"))
        assertEquals("deraketaua", Telegram.cleanChannel("@deraketaua"))
        assertTrue(Telegram.isPrivateLink("https://t.me/+AbCdEf"))
    }

    /** Каждый класс из AndroidManifest.xml должен существовать — иначе приложение падает при запуске. */
    @Test
    fun manifestClassesExist() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val names = Regex("""<(application|activity|receiver|service)\b[^>]*?android:name="([^"]+)"""")
            .findAll(manifest).map { it.groupValues[2] }.toList()
        assertTrue("в манифесте не нашлось классов", names.size >= 5)
        val missing = names.filter { n ->
            val full = if (n.startsWith(".")) "ua.otboi.app$n" else n
            runCatching { Class.forName(full, false, javaClass.classLoader) }.isFailure
        }
        assertTrue("нет классов из манифеста: $missing", missing.isEmpty())
    }

    /** Во всех языках каждая строка заполнена. */
    @Test
    fun translationsComplete() {
        for (lang in ua.otboi.app.i18n.L.all) {
            for (m in lang.javaClass.methods) {
                if (m.parameterCount != 0 || m.returnType != String::class.java || !m.name.startsWith("get")) continue
                val v = m.invoke(lang) as String
                assertTrue("${lang.code}.${m.name} пустая", v.isNotBlank())
            }
            assertEquals(7, lang.daysShort.size)
            for (key in listOf("notif", "exact", "fsi", "battery")) assertTrue(lang.permTitle(key).isNotBlank())
            for (b in 0..7) assertTrue(lang.wearBand(b).isNotBlank())
            assertTrue(lang.dur(0, 2, 5).contains("2"))
        }
        assertEquals("uk", ua.otboi.app.i18n.L.pick("uk").code)
        assertEquals("ru", ua.otboi.app.i18n.L.pick("ru").code)
        assertEquals("en", ua.otboi.app.i18n.L.pick("de").code)
    }

    @Test
    fun translit() {
        val en = ua.otboi.app.i18n.En
        assertEquals("Vinnytskyi", en.translit("Вінницький"))
        assertEquals("Kyiv", en.translit("Київ"))
        assertEquals("Bila Tserkva", en.translit("Біла Церква"))
        assertEquals("Kamianskyi", en.translit("Кам’янський"))
        assertEquals("Zaporizhzhia", en.translit("Запоріжжя"))
        assertEquals("Zghurivka", en.translit("Згурівка"))
        assertEquals("Yahotyn", en.translit("Яготин"))
        assertEquals("Vinnytskyi raion", en.shortPlace("Вінницький район"))
        assertEquals("Vinnytsia Oblast", en.oblastName(4, "Вінницька обл."))
    }

    @Test
    fun weatherAndWear() {
        val body = """{"current":{"temperature_2m":4.2,"apparent_temperature":0.6,"weather_code":61,"wind_speed_10m":5.1},
            "hourly":{"time":["2026-11-03T06:00","2026-11-03T07:00","2026-11-03T08:00","2026-11-03T12:00","2026-11-03T15:00"],
                      "apparent_temperature":[-2.0,-1.0,0.0,6.0,8.0],"weather_code":[61,61,61,3,3]},
            "daily":{"temperature_2m_max":[9.5],"temperature_2m_min":[1.1],"precipitation_probability_max":[70],
                     "weather_code":[61],"uv_index_max":[1.2],"wind_speed_10m_max":[11.0]}}"""
        val w = ua.otboi.app.data.WeatherRepo.parse(body, 4, 1L)!!
        assertEquals(4, w.stateId)
        assertEquals(70, w.precipProb)
        assertEquals(-0.5, w.morningFeels, 0.01)
        assertEquals(7.0, w.dayFeels, 0.01)
        val wear = ua.otboi.app.data.Wear
        assertEquals(0, wear.band(-20.0))
        assertEquals(2, wear.band(0.0))
        assertEquals(6, wear.band(25.0))
        assertEquals(7, wear.band(31.0))
        val advice = wear.advice(w, 7)
        assertTrue(advice, advice.contains("umbrella"))
        assertTrue(advice, advice.contains("windy"))
        assertTrue(advice, advice.contains("layers"))
        assertEquals("+3°", ua.otboi.app.data.Fmt.temp(2.6))
        assertEquals("−1°", ua.otboi.app.data.Fmt.temp(-1.2))
        assertEquals("0°", ua.otboi.app.data.Fmt.temp(0.3))
    }

    @Test
    fun updateNotesByLanguage() {
        val body = """{"versionCode":11,"versionName":"1.3.0","sha256":"ab","notes":{"uk":"Українською","ru":"По-русски","en":"In English"}}"""
        val i = ua.otboi.app.update.Updater.parse(body).getOrThrow()
        assertEquals("In English", i.notes)
    }

    @Test
    fun backupSource() {
        val body = """{"version":10,"states":{
            "Вінницька область":{"enabled":false,"enabled_at":null,"districts":{
                "Вінницький район":{"enabled":true,"enabled_at":"2026-11-02T19:06:46.584Z"},
                "Кам’янський район":{"enabled":false,"enabled_at":null},
                "Гайсинський район":{"enabled":false,"enabled_at":null}}},
            "м. Київ":{"enabled":true,"enabled_at":"2026-11-02T18:56:52.999Z","districts":{}},
            "Львівська область":{"enabled":false,"districts":{"Львівський район":{"enabled":false}}}}}"""
        val b = ua.otboi.app.net.BackupApi
        assertEquals(Status.ALERT, b.evaluate(body, "Вінницька область", "Вінницький район").status)
        assertTrue(b.evaluate(body, "Вінницька область", "Вінницький район").backup)
        assertTrue(b.evaluate(body, "Вінницька область", "Вінницький район").since > 0L)
        assertEquals(Status.PARTIAL, b.evaluate(body, "Вінницька область", "Гайсинський район").status)
        assertEquals(Status.PARTIAL, b.evaluate(body, "Вінницька область", null).status)
        assertEquals(Status.PARTIAL, b.evaluate(body, "Вінницька область", "Кам'янський район").status)
        assertEquals(Status.UNKNOWN, b.evaluate(body, "Вінницька область", "Неіснуючий район").status)
        assertEquals(Status.ALERT, b.evaluate(body, "м. Київ", null).status)
        assertEquals(Status.CLEAR, b.evaluate(body, "Львівська область", "Львівський район").status)
        assertEquals(Status.CLEAR, b.evaluate(body, "Львівська область", null).status)
        assertEquals(Status.UNKNOWN, b.evaluate(body, "Атлантида", null).status)
    }

    @Test
    fun updateInfo() {
        val i = ua.otboi.app.update.Updater.parse(
            """{"versionCode":7,"versionName":"1.0.6","sha256":"ABCDEF","notes":"Запасной источник"}"""
        ).getOrThrow()
        assertEquals(7L, i.versionCode)
        assertEquals("1.0.6", i.versionName)
        assertEquals("abcdef", i.sha256)
        assertTrue(ua.otboi.app.update.Updater.parse("<html>").isFailure)
    }

    @Test
    fun parseTelegram() {
        val html = """<div class="tgme_widget_message_wrap"><div class="tgme_widget_message" data-post="chan/12">
            <div class="tgme_widget_message_text js-message_text">🚀 Ракета<br/>курсом на Київ</div>
            <time datetime="2026-11-02T18:31:08+00:00">18:31</time></div></div>"""
        val posts = Telegram.parse(html, "chan", listOf("київ"))
        assertEquals(1, posts.size)
        assertEquals("🚀 Ракета\nкурсом на Київ", posts[0].text)
        assertTrue(posts[0].isAlert)
        assertTrue(posts[0].highlighted)
        assertTrue(posts[0].time > 0L)
    }
}

/** Живая проверка API (только печатает, не падает без сети). */
class LiveApiTest {
    @Test
    fun liveSirenAndTelegram() {
        runBlocking {
            val (body, err) = AlertApi.fetchRegionsRaw()
            println("LIVE regions: length=${body?.length} err=$err")
            val tree = if (body != null) AlertApi.parseRegions(body) else emptyList()
            println("LIVE states: ${tree.size}")
            tree.firstOrNull { it.id == 14 }?.let { st ->
                val first = st.children.firstOrNull()
                println("LIVE ${st.name}: районов ${st.children.size}; ${first?.name} → громад ${first?.children?.size}")
            }
            val air = AlertApi.airAlerts()
            println("LIVE alerts ok=${air.ok} err=${air.error} count=${air.alerts.size} sample=${air.alerts.entries.take(6)}")
            for (id in listOf(4, 14, 31, 22)) {
                val r = AlertApi.status(id, 0, 0, tree)
                println("LIVE state $id → ${r.status} since=${r.since} ${r.reason}")
            }
            val tg = Telegram.fetch("deraketaua", listOf("київ"))
            tg.onSuccess { posts ->
                println("LIVE telegram posts=${posts.size} alert=${posts.count { it.isAlert }}")
                posts.take(3).forEach { println("LIVE   ${it.id} t=${it.time} alert=${it.isAlert}: ${it.text.take(70).replace('\n', ' ')}") }
            }.onFailure { println("LIVE telegram error: ${it.message}") }

            // Погода (open-meteo) для Вінниці.
            val vk = ua.otboi.app.data.Oblasts.byId(4)!!.city
            val wres = ua.otboi.app.net.Http.get(
                "https://api.open-meteo.com/v1/forecast?latitude=${vk.lat}&longitude=${vk.lon}" +
                    "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&hourly=apparent_temperature,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,uv_index_max,wind_speed_10m_max" +
                    "&wind_speed_unit=ms&timezone=auto&forecast_days=1",
                "bylarik-test", "application/json",
            )
            val wd = if (wres.code == 200) ua.otboi.app.data.WeatherRepo.parse(wres.body, 4, System.currentTimeMillis()) else null
            println("LIVE WEATHER code=${wres.code} $wd")
            if (wd != null) println("LIVE WEAR ${ua.otboi.app.data.Wear.advice(wd, 7)}")

            // Запасной источник (vadimklimenko.com).
            for ((st, d) in listOf("Вінницька область" to "Вінницький район", "Вінницька область" to null, "м. Київ" to null, "Київська область" to "Броварський район")) {
                val r = ua.otboi.app.net.BackupApi.status(st, d)
                println("LIVE BACKUP $st / $d → ${r.status} since=${r.since} ${r.reason}")
            }
        }
    }
}
