package ua.otboi.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ua.otboi.app.net.AlertApi
import ua.otboi.app.net.BackupApi
import ua.otboi.app.net.Post
import ua.otboi.app.net.RegionNode
import ua.otboi.app.net.Telegram
import java.io.File

/** Дерево регионов (область → район → громада), с кешем в файле. */
object RegionRepo {
    val tree = MutableStateFlow<List<RegionNode>>(emptyList())
    val error = MutableStateFlow("")
    private val mutex = Mutex()
    private const val WEEK = 7L * 24 * 60 * 60 * 1000

    suspend fun ensure(ctx: Context, forceNetwork: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = File(ctx.applicationContext.filesDir, "regions.json")
            if (tree.value.isEmpty() && file.exists()) {
                val cached = runCatching { AlertApi.parseRegions(file.readText()) }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) tree.value = cached
            }
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > WEEK
            if (tree.value.isEmpty() || stale || forceNetwork) {
                val (body, err) = AlertApi.fetchRegionsRaw()
                if (body != null) {
                    val nodes = runCatching { AlertApi.parseRegions(body) }.getOrDefault(emptyList())
                    if (nodes.isNotEmpty()) {
                        tree.value = nodes
                        runCatching { file.writeText(body) }
                        error.value = ""
                    } else if (tree.value.isEmpty()) {
                        error.value = "список регионов пуст"
                    }
                } else if (tree.value.isEmpty()) {
                    error.value = err
                }
            }
        }
    }
}

/** Выбор места: сохраняет ID и человеческое название. */
object Places {
    fun select(stateId: Int, districtId: Int, communityId: Int) {
        val state = RegionRepo.tree.value.firstOrNull { it.id == stateId }
        val district = state?.children?.firstOrNull { it.id == districtId }
        val community = district?.children?.firstOrNull { it.id == communityId }
        val parts = ArrayList<String>()
        parts += Oblasts.short(Oblasts.name(stateId))
        if (districtId > 0) parts += district?.let { Oblasts.short(it.name) } ?: "район"
        if (communityId > 0) parts += community?.let { Oblasts.short(it.name) } ?: "громада"
        Store.updateSettings {
            it.copy(
                stateId = stateId,
                districtId = districtId,
                communityId = if (districtId > 0) communityId else 0,
                placeShort = parts.last(),
                placeFull = parts.joinToString(" · "),
            )
        }
        Store.setReading(null)
    }
}

/** Текущий статус тревоги для выбранного места. */
object Live {
    private val mutex = Mutex()

    suspend fun check(ctx: Context): Reading = mutex.withLock {
        val s = Store.settings.value
        if (s.stateId <= 0) {
            val r = Reading(Status.UNKNOWN, "область не выбрана")
            Store.setReading(r)
            return@withLock r
        }
        RegionRepo.ensure(ctx)
        var r = AlertApi.status(s.stateId, s.districtId, s.communityId, RegionRepo.tree.value)
        if (r.status == Status.UNKNOWN) {
            // Основной источник не ответил — спрашиваем запасной (данные по районам).
            val main = r.reason
            val district: String? = if (s.districtId > 0) {
                RegionRepo.tree.value.firstOrNull { it.id == s.stateId }
                    ?.children?.firstOrNull { it.id == s.districtId }?.name
            } else {
                null
            }
            if (s.districtId <= 0 || district != null) {
                val b = BackupApi.status(Oblasts.name(s.stateId), district)
                r = if (b.status != Status.UNKNOWN) b else r.copy(reason = "$main; ${b.reason}")
            }
        }
        val prev = Store.reading.value
        val now = System.currentTimeMillis()
        if (r.status == Status.UNKNOWN) {
            // Короткий сбой сети не перекрашивает экран: держим последний ответ 5 минут.
            if (prev == null || prev.status == Status.UNKNOWN || now - prev.at > 5 * 60_000L) Store.setReading(r)
            return@withLock r
        }
        if ((r.status == Status.ALERT || r.status == Status.PARTIAL) && r.since <= 0L) {
            val keep = prev != null && prev.status == r.status && prev.since > 0L
            r = r.copy(since = if (keep) prev!!.since else now)
        }
        // Место могли поменять, пока шёл запрос.
        val cur = Store.settings.value
        if (cur.stateId == s.stateId && cur.districtId == s.districtId && cur.communityId == s.communityId) {
            Store.setReading(r)
        }
        r
    }

    /** Считать ли это тревогой для будильника. */
    fun isAlertForMe(r: Reading?, s: AppSettings): Boolean {
        if (r == null) return false
        return r.status == Status.ALERT ||
            (r.status == Status.PARTIAL && (s.districtId == 0 || s.neighborsCount))
    }
}

/** Лента из публичных Telegram-каналов. */
object Feed {
    val posts = MutableStateFlow<List<Post>>(emptyList())
    val errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val loading = MutableStateFlow(false)
    @Volatile
    var loadedAt = 0L
        private set
    private val mutex = Mutex()

    suspend fun refresh(maxAgeMs: Long = 0L) {
        if (maxAgeMs > 0 && System.currentTimeMillis() - loadedAt < maxAgeMs && posts.value.isNotEmpty()) return
        mutex.withLock {
            loading.value = true
            try {
                val s = Store.settings.value
                val keys = Oblasts.keywords(s, RegionRepo.tree.value)
                val results = coroutineScope {
                    s.channels.map { ch -> async { ch to Telegram.fetch(ch, keys) } }.awaitAll()
                }
                val all = ArrayList<Post>()
                val errs = HashMap<String, String>()
                for ((ch, res) in results) {
                    res.onSuccess { all += it }.onFailure { errs[ch] = it.message ?: "ошибка" }
                }
                if (all.isNotEmpty() || errs.isEmpty()) {
                    posts.value = all.distinctBy { it.id }.sortedByDescending { it.time }
                }
                errors.value = errs
                loadedAt = System.currentTimeMillis()
            } finally {
                loading.value = false
            }
        }
    }
}
