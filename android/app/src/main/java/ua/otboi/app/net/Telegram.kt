package ua.otboi.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

data class Post(
    val id: String,
    val channel: String,
    val time: Long,
    val text: String,
    /** В тексте упоминается твоя область / район. */
    val highlighted: Boolean,
    /** Сообщение про тревогу или угрозу, а не обычная новость. */
    val isAlert: Boolean,
)

/**
 * Публичный канал читается через веб-версию t.me/s/<канал> — без входа в Telegram.
 * Только для информации: будить или нет, решают официальные данные о тревоге.
 */
object Telegram {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    suspend fun fetch(raw: String, keywords: List<String>): Result<List<Post>> = withContext(Dispatchers.IO) {
        if (isPrivateLink(raw)) {
            return@withContext Result.failure(Exception("закрытые каналы (ссылки с «+») читать нельзя — только публичные"))
        }
        val channel = cleanChannel(raw)
        if (channel.isEmpty()) return@withContext Result.failure(Exception("не указан канал"))
        try {
            val res = Http.get("https://t.me/s/$channel", UA, "text/html")
            if (res.code != 200) return@withContext Result.failure(Exception("Telegram ответил ${res.code}"))
            val posts = parse(res.body, channel, keywords)
            if (posts.isEmpty()) {
                Result.failure(Exception("постов нет — канал закрыт, без веб-версии или не существует"))
            } else {
                Result.success(posts)
            }
        } catch (e: Exception) {
            Result.failure(Exception(Http.shortError(e)))
        }
    }

    /** Ссылка на закрытый канал или приглашение: t.me/+AbC…, t.me/joinchat/… */
    fun isPrivateLink(raw: String): Boolean {
        val c = raw.trim().lowercase()
        return c.contains("t.me/+") || c.contains("/joinchat") || c.startsWith("+")
    }

    /** «@deraketaua», «t.me/deraketaua», «https://t.me/s/deraketaua» → «deraketaua». */
    fun cleanChannel(raw: String): String {
        var c = raw.trim()
        for (prefix in listOf("https://", "http://", "www.", "t.me/s/", "t.me/", "telegram.me/", "@")) {
            if (c.startsWith(prefix, ignoreCase = true)) c = c.substring(prefix.length)
        }
        c = c.substringBefore('/').substringBefore('?')
        return c.filter { ch -> (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '_' }
    }

    /** Посты канала, новые сверху. */
    fun parse(html: String, channel: String, keywords: List<String>): List<Post> {
        val doc = Jsoup.parse(html)
        val keys = keywords.map { it.lowercase() }.filter { it.isNotBlank() }
        val out = ArrayList<Post>()
        for (wrap in doc.select("div.tgme_widget_message_wrap")) {
            val textEl = wrap.selectFirst("div.tgme_widget_message_text") ?: continue
            val id = wrap.selectFirst("[data-post]")?.attr("data-post")?.takeIf { it.isNotBlank() } ?: continue
            val time = parseTime(wrap.selectFirst("time[datetime]")?.attr("datetime"))
            val text = cleanText(textEl)
            if (text.isBlank()) continue
            val lower = text.lowercase()
            out += Post(id, channel, time, text, keys.any { lower.contains(it) }, isAlertNews(text))
        }
        return out.reversed()
    }

    private fun cleanText(el: Element): String {
        val copy = el.clone()
        copy.select("br").forEach { it.replaceWith(TextNode("\n")) }
        return copy.wholeText()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    // MARK: фильтр «новости про тревогу»

    /** Части слов, по которым пост считается сообщением про тревогу или угрозу (укр. и рус.). */
    private val alertStems = listOf(
        // тревога и отбой
        "тривог", "відбій", "відбою", "тревог", "отбой", "сирен", "в укритт", "в укрыти",
        // чем атакуют
        "шахед", "шахєд", "герань", "гербер", "дрон", "бпла", "безпілот", "беспилот", "мопед",
        "ракет", "балістик", "баллистик", "крилат", "крылат", "кинджал", "кинжал", "іскандер", "искандер",
        "калібр", "калибр", "х-101", "х-59", "х-22", "х-69", "ту-95", "ту-22", "ту-160", "міг-31", "миг-31", "су-34",
        "авіабомб", "авиабомб", "керовані авіа", "управляемые авиа",
        // что происходит
        "вибух", "взрыв", "приліт", "прильот", "прилет", "обстріл", "обстрел", "атак",
        "загроз", "угроз", "ппо ", "пво ", "збит", "сбит", "сбил", "уламк", "обломк",
        "зліт", "злет", "взлет", "курс на", "ворожий", "ворожі", "вражеск", "реактив",
        "бандерол", "бандерл", "тікайте", "бегите", "локаційно",
    )

    /** Короткие сокращения — только целым словом (чтобы «каб» не ловил «кабінет»). */
    private val alertWords = Regex(
        "(?<![\\p{L}\\p{N}])(каб|каби|кабів|кабы|кабов|кр|бпла|ппо|пво|рсзв|мрлс|рлс)(?![\\p{L}\\p{N}])"
    )

    /** Эмодзи, которые мониторинговые каналы ставят именно на угрозы. */
    private val alertEmoji = listOf("🚀", "🛵", "💥", "🛩", "🎯")

    fun isAlertNews(text: String): Boolean {
        val lower = text.lowercase()
        if (alertStems.any { lower.contains(it) }) return true
        if (alertWords.containsMatchIn(lower)) return true
        return alertEmoji.any { text.contains(it) }
    }
}
