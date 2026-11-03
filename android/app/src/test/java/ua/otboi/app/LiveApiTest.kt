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

            // Запасной источник (vadimklimenko.com).
            for ((st, d) in listOf("Вінницька область" to "Вінницький район", "Вінницька область" to null, "м. Київ" to null, "Київська область" to "Броварський район")) {
                val r = ua.otboi.app.net.BackupApi.status(st, d)
                println("LIVE BACKUP $st / $d → ${r.status} since=${r.since} ${r.reason}")
            }
        }
    }
}
