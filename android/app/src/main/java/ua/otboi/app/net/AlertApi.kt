package ua.otboi.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import ua.otboi.app.data.Reading
import ua.otboi.app.data.Status

/** Узел дерева регионов: область → район → громада. */
class RegionNode(val id: Int, val name: String, val type: String, val children: List<RegionNode>) {
    val allIds: Set<Int> by lazy {
        val out = HashSet<Int>()
        fun walk(n: RegionNode) {
            out += n.id
            n.children.forEach { walk(it) }
        }
        walk(this)
        out
    }
}

/**
 * Официальные данные о тревоге: siren.pp.ua (открытая обёртка над api.ukrainealarm.com, без ключа).
 * С декабря 2025 тревоги объявляют по районам, поэтому смотрим всё дерево: область → район → громада.
 */
object AlertApi {
    private const val BASE = "https://siren.pp.ua/api/v3"
    private const val UA = "Otboi-Android/1.0"

    /** alerts: regionId → когда началась воздушная тревога (0 — неизвестно). */
    class AirResult(
        val ok: Boolean,
        val alerts: Map<Int, Long>,
        val error: String = "",
        val rateLimited: Boolean = false,
    )

    suspend fun airAlerts(path: String = "/alerts"): AirResult = withContext(Dispatchers.IO) {
        val first = airOnce(path)
        if (!first.ok && first.rateLimited) {
            delay(4_000)
            airOnce(path)
        } else {
            first
        }
    }

    private fun airOnce(path: String): AirResult = try {
        val res = Http.get(BASE + path, UA, "application/json")
        when {
            res.code == 429 -> AirResult(false, emptyMap(), "сервер перегружен (429)", rateLimited = true)
            res.code != 200 -> AirResult(false, emptyMap(), "сервер ответил ${res.code}")
            else -> parseAlerts(res.body)
        }
    } catch (e: Exception) {
        AirResult(false, emptyMap(), Http.shortError(e))
    }

    fun parseAlerts(body: String): AirResult {
        val json = JSONTokener(body).nextValue()
        val out = HashMap<Int, Long>()
        val found = BooleanArray(1)
        collectAir(json, null, out, found)
        if (!found[0]) {
            return if (json is JSONArray && json.length() == 0) {
                AirResult(true, emptyMap())
            } else {
                AirResult(false, emptyMap(), "непонятный ответ сервера")
            }
        }
        return AirResult(true, out)
    }

    /** Обходит ответ и собирает regionId воздушных тревог из массивов activeAlerts. */
    private fun collectAir(value: Any?, parentId: Int?, out: MutableMap<Int, Long>, found: BooleanArray) {
        when (value) {
            is JSONObject -> {
                val ownId = intOf(value.opt("regionId")) ?: parentId
                val list = value.opt("activeAlerts")
                if (list is JSONArray) {
                    found[0] = true
                    for (i in 0 until list.length()) {
                        val a = list.optJSONObject(i) ?: continue
                        if (a.optString("type").uppercase() != "AIR") continue
                        val rid = intOf(a.opt("regionId")) ?: ownId ?: continue
                        val since = parseTime(a.optString("lastUpdate"))
                        val prev = out[rid]
                        out[rid] = when {
                            prev == null || prev == 0L -> since
                            since == 0L -> prev
                            else -> minOf(prev, since)
                        }
                    }
                } else if (value.has("activeAlerts")) {
                    found[0] = true
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k != "activeAlerts") collectAir(value.opt(k), ownId, out, found)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) collectAir(value.opt(i), parentId, out, found)
        }
    }

    // MARK: дерево регионов

    /** Тело ответа /regions или текст ошибки. */
    fun fetchRegionsRaw(): Pair<String?, String> = try {
        val res = Http.get("$BASE/regions", UA, "application/json")
        if (res.code == 200) res.body to "" else null to "сервер ответил ${res.code}"
    } catch (e: Exception) {
        null to Http.shortError(e)
    }

    fun parseRegions(body: String): List<RegionNode> {
        val json = JSONTokener(body).nextValue()
        val list: JSONArray = when {
            json is JSONObject && json.optJSONArray("states") != null -> json.getJSONArray("states")
            json is JSONArray -> json
            else -> return emptyList()
        }
        val out = ArrayList<RegionNode>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            node(o)?.let { out += it }
        }
        return out
    }

    private fun node(o: JSONObject): RegionNode? {
        val id = intOf(o.opt("regionId")) ?: return null
        val kids = o.optJSONArray("regionChildIds")
        val children = ArrayList<RegionNode>()
        if (kids != null) {
            for (i in 0 until kids.length()) {
                val k = kids.optJSONObject(i) ?: continue
                node(k)?.let { children += it }
            }
        }
        return RegionNode(id, o.optString("regionName"), o.optString("regionType"), children)
    }

    // MARK: статус для выбранного места

    fun evaluate(air: Map<Int, Long>, state: RegionNode, districtId: Int, communityId: Int): Reading {
        fun sinceOf(ids: Collection<Int>): Long = ids.mapNotNull { air[it] }.filter { it > 0L }.minOrNull() ?: 0L

        if (air.containsKey(state.id)) return Reading(Status.ALERT, since = sinceOf(listOf(state.id)))
        val inState = air.keys.filter { it in state.allIds }
        val partial = if (inState.isNotEmpty()) Reading(Status.PARTIAL, since = sinceOf(inState)) else Reading(Status.CLEAR)
        if (districtId <= 0) return partial

        if (communityId > 0) {
            // Громада: тревога, если она объявлена на громаду или на весь её район.
            val parent = state.children.firstOrNull { d -> d.children.any { it.id == communityId } }
            val mine = setOf(communityId, parent?.id ?: districtId)
            val hit = air.keys.filter { it in mine }
            return if (hit.isNotEmpty()) Reading(Status.ALERT, since = sinceOf(hit)) else partial
        }
        val district = state.children.firstOrNull { it.id == districtId }
        val mine = district?.allIds ?: setOf(districtId)
        val hit = air.keys.filter { it in mine }
        return if (hit.isNotEmpty()) Reading(Status.ALERT, since = sinceOf(hit)) else partial
    }

    suspend fun status(stateId: Int, districtId: Int, communityId: Int, tree: List<RegionNode>): Reading {
        if (stateId <= 0) return Reading(Status.UNKNOWN, "область не выбрана")
        val community = if (districtId > 0) communityId else 0
        val state = tree.firstOrNull { it.id == stateId }
        if (state != null) {
            val all = airAlerts("/alerts")
            if (all.ok) return evaluate(all.alerts, state, districtId, community)
        }

        // Запасной путь: отдельные запросы по области, району и громаде.
        val levels = ArrayList<Int>()
        levels += stateId
        if (districtId > 0) levels += districtId
        if (community > 0) levels += community
        var mine = false
        var elsewhere = false
        var since = 0L
        for ((index, id) in levels.withIndex()) {
            val r = airAlerts("/alerts/$id")
            val mostSpecific = index == levels.lastIndex
            if (r.ok) {
                if (r.alerts.containsKey(id)) {
                    mine = true
                    since = r.alerts[id] ?: 0L
                } else if (r.alerts.isNotEmpty()) {
                    if (mostSpecific && levels.size > 1) {
                        mine = true
                        since = r.alerts.values.filter { it > 0L }.minOrNull() ?: 0L
                    } else {
                        elsewhere = true
                    }
                }
            } else if (mostSpecific) {
                return Reading(Status.UNKNOWN, r.error)
            }
        }
        val status = when {
            mine -> Status.ALERT
            elsewhere -> Status.PARTIAL
            else -> Status.CLEAR
        }
        return Reading(status, since = since)
    }
}
