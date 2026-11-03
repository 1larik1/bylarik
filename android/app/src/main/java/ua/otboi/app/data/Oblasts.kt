package ua.otboi.app.data

import ua.otboi.app.net.RegionNode

/** Области Украины. ID совпадают с ukrainealarm.com (siren.pp.ua). */
object Oblasts {
    class Oblast(val id: Int, val name: String, val keys: List<String>)

    val all: List<Oblast> = listOf(
        Oblast(4, "Вінницька область", listOf("вінниц", "вінничч")),
        Oblast(8, "Волинська область", listOf("волин", "луцьк")),
        Oblast(9, "Дніпропетровська область", listOf("дніпр", "дніпропетров")),
        Oblast(28, "Донецька область", listOf("донеч", "донецьк", "краматорськ", "словʼянськ", "слов'янськ")),
        Oblast(10, "Житомирська область", listOf("житомир")),
        Oblast(11, "Закарпатська область", listOf("закарпат", "ужгород")),
        Oblast(12, "Запорізька область", listOf("запоріж")),
        Oblast(13, "Івано-Франківська область", listOf("франків", "прикарпат")),
        Oblast(31, "м. Київ", listOf("київ", "столиц")),
        Oblast(14, "Київська область", listOf("київщин", "київськ", "бровар", "бориспіл", "біла церква")),
        Oblast(15, "Кіровоградська область", listOf("кіровоград", "кропивниц")),
        Oblast(16, "Луганська область", listOf("луган")),
        Oblast(27, "Львівська область", listOf("львів")),
        Oblast(17, "Миколаївська область", listOf("миколаїв")),
        Oblast(18, "Одеська область", listOf("одес")),
        Oblast(19, "Полтавська область", listOf("полтав", "кременчук")),
        Oblast(5, "Рівненська область", listOf("рівне", "рівного", "рівненськ", "рівненщин")),
        Oblast(20, "Сумська область", listOf("сумщин", "суми", "сумськ", "сумах")),
        Oblast(21, "Тернопільська область", listOf("терноп")),
        Oblast(22, "Харківська область", listOf("харків")),
        Oblast(23, "Херсонська область", listOf("херсон")),
        Oblast(3, "Хмельницька область", listOf("хмельниц")),
        Oblast(24, "Черкаська область", listOf("черкас")),
        Oblast(26, "Чернівецька область", listOf("чернівц", "буковин")),
        Oblast(25, "Чернігівська область", listOf("чернігів")),
    )

    fun name(id: Int): String = all.firstOrNull { it.id == id }?.name ?: "Регион $id"

    /** «Київська область» → «Київська обл.», «Броварський район» → «Броварський р-н». */
    fun short(name: String): String = name
        .replace("територіальна громада", "громада")
        .replace(" область", " обл.")
        .replace(" район", " р-н")
        .trim()

    /** Основа слова для подсветки новостей: «Броварський район» → «бровар». */
    fun stem(name: String): String? {
        val words = name.trim().lowercase().split(" ").filter { it.isNotBlank() }
        val w = (if (words.firstOrNull() == "м.") words.getOrNull(1) else words.firstOrNull()) ?: return null
        for (suffix in listOf("цький", "зький", "ський", "цька", "зька", "ська", "ий", "а")) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 4) return w.dropLast(suffix.length)
        }
        return w.takeIf { it.length >= 4 }
    }

    /** Слова, по которым новость считается «про твоё место». */
    fun keywords(s: AppSettings, tree: List<RegionNode>): List<String> {
        val out = ArrayList<String>()
        all.firstOrNull { it.id == s.stateId }?.let { out += it.keys }
        val state = tree.firstOrNull { it.id == s.stateId }
        val district = state?.children?.firstOrNull { it.id == s.districtId }
        district?.let { d -> stem(d.name)?.let { out += it } }
        district?.children?.firstOrNull { it.id == s.communityId }?.let { c -> stem(c.name)?.let { out += it } }
        return out.distinct()
    }
}
