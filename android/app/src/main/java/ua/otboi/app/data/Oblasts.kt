package ua.otboi.app.data

import ua.otboi.app.net.RegionNode

/** Области Украины. ID совпадают с ukrainealarm.com (siren.pp.ua). */
object Oblasts {
    class Oblast(val id: Int, val name: String, val keys: List<String>) {
        /** Областной центр — для погоды. */
        val city: City get() = cities[id] ?: City("Київ", "Kyiv", 50.4501, 30.5234)
    }

    class City(val uk: String, val en: String, val lat: Double, val lon: Double)

    private val cities: Map<Int, City> = mapOf(
        4 to City("Вінниця", "Vinnytsia", 49.2331, 28.4682),
        8 to City("Луцьк", "Lutsk", 50.7472, 25.3254),
        9 to City("Дніпро", "Dnipro", 48.4647, 35.0462),
        28 to City("Краматорськ", "Kramatorsk", 48.7389, 37.5848),
        10 to City("Житомир", "Zhytomyr", 50.2547, 28.6587),
        11 to City("Ужгород", "Uzhhorod", 48.6208, 22.2879),
        12 to City("Запоріжжя", "Zaporizhzhia", 47.8388, 35.1396),
        13 to City("Івано-Франківськ", "Ivano-Frankivsk", 48.9226, 24.7111),
        31 to City("Київ", "Kyiv", 50.4501, 30.5234),
        14 to City("Київ", "Kyiv", 50.4501, 30.5234),
        15 to City("Кропивницький", "Kropyvnytskyi", 48.5079, 32.2623),
        16 to City("Сєвєродонецьк", "Sievierodonetsk", 48.9482, 38.4917),
        27 to City("Львів", "Lviv", 49.8397, 24.0297),
        17 to City("Миколаїв", "Mykolaiv", 46.975, 31.9946),
        18 to City("Одеса", "Odesa", 46.4825, 30.7233),
        19 to City("Полтава", "Poltava", 49.5883, 34.5514),
        5 to City("Рівне", "Rivne", 50.6199, 26.2516),
        20 to City("Суми", "Sumy", 50.9077, 34.7981),
        21 to City("Тернопіль", "Ternopil", 49.5535, 25.5948),
        22 to City("Харків", "Kharkiv", 49.9935, 36.2304),
        23 to City("Херсон", "Kherson", 46.6354, 32.6169),
        3 to City("Хмельницький", "Khmelnytskyi", 49.4229, 26.9871),
        24 to City("Черкаси", "Cherkasy", 49.4444, 32.0598),
        26 to City("Чернівці", "Chernivtsi", 48.2921, 25.9358),
        25 to City("Чернігів", "Chernihiv", 51.4982, 31.2893),
    )

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

    fun name(id: Int): String = all.firstOrNull { it.id == id }?.name ?: "$id"

    fun byId(id: Int): Oblast? = all.firstOrNull { it.id == id }

    /** Короткое название на языке телефона: «Київська обл.», «Kyiv Oblast», «Броварський р-н». */
    fun short(name: String): String = ua.otboi.app.i18n.T.shortPlace(name)

    /** Название области для показа: «Вінницька обл.» / «Vinnytsia Oblast». */
    fun display(id: Int): String = ua.otboi.app.i18n.T.oblastName(id, short(name(id)))

    fun cityName(id: Int): String {
        val c = byId(id)?.city ?: return ""
        return ua.otboi.app.i18n.T.cityName(c.uk, c.en)
    }

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
