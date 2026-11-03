package ua.otboi.app.i18n

import android.content.Context
import java.util.Locale

/**
 * Все тексты интерфейса. Язык берётся из системы (или из «Язык приложения» в настройках Android 13+):
 * украинский, русский, для остальных — английский.
 */
object L {
    @Volatile
    var s: Strings = En
        private set

    fun init(ctx: Context) {
        val lang = try {
            ctx.resources.configuration.locales[0].language
        } catch (_: Exception) {
            Locale.getDefault().language
        }
        s = pick(lang)
    }

    fun pick(lang: String): Strings = when (lang) {
        "uk" -> Uk
        "ru" -> Ru
        else -> En
    }

    val all: List<Strings> get() = listOf(Uk, Ru, En)
}

/** Короткий доступ: T.save, T.tabAlarms … */
val T: Strings get() = L.s

abstract class Strings {
    abstract val code: String
    abstract val locale: Locale

    // Навигация
    abstract val tabAlarms: String
    abstract val tabNews: String
    abstract val tabPlace: String
    abstract val tabMore: String

    // Статус тревоги
    abstract val statusNoPlace: String
    abstract val statusNoData: String
    abstract val statusClear: String
    abstract fun statusAlert(dur: String): String
    abstract fun statusAlertPart(dur: String): String
    abstract val statusNearby: String
    abstract fun backupNotice(source: String): String

    // Главный экран
    abstract val nextWake: String
    abstract val alarmsOff: String
    abstract val alarmsOffHint: String
    abstract val emptyAlarms: String
    abstract val emptyAlarmsHint: String
    abstract val addAlarm: String
    abstract fun nightProgress(pct: Int): String
    abstract fun alertSkipNote(time: String): String
    abstract fun alertWaitNote(time: String): String
    abstract fun permsMissing(n: Int): String
    abstract val permsMissingHint: String
    abstract val latestAlertNews: String
    abstract val newsTeaserEmpty: String
    abstract val yourPlace: String

    // Ожидание отбоя
    abstract val wakeDelayed: String
    abstract val waitImmediate: String
    abstract val waitWithinMinute: String
    abstract fun waitAfter(min: Int): String
    abstract fun clearAt(clear: String, ring: String): String
    abstract val canSleep: String
    abstract val untilWord: String
    abstract fun maxWaitLeft(h: Int, left: String): String
    abstract val aboutAlert: String
    abstract val noNewsYet: String
    abstract val wakeNow: String
    abstract val notToday: String

    // Дни и время
    abstract val daysShort: List<String>
    abstract val weekdaysRange: String
    abstract val weekendShort: String
    abstract val everyDay: String
    abstract val once: String
    abstract fun dur(days: Long, hours: Long, minutes: Long): String
    abstract val lessThanMinute: String
    abstract fun inTime(dur: String): String
    abstract val justNow: String
    abstract fun minAgo(m: Long): String
    abstract fun hoursAgo(h: Long): String
    abstract val today: String
    abstract val tomorrow: String
    abstract val dayMonthPattern: String

    // Правило на тревогу
    abstract val ruleAfterTitle: String
    abstract val ruleAfterHint: String
    abstract val ruleAfterDesc: String
    abstract val ruleSkipTitle: String
    abstract val ruleSkipHint: String
    abstract val ruleSkipDesc: String
    abstract val ruleAlwaysTitle: String
    abstract val ruleAlwaysHint: String
    abstract val ruleAlwaysDesc: String

    // Редактор будильника
    abstract val newAlarm: String
    abstract val editAlarm: String
    abstract val daysLabel: String
    abstract val presetWeekdays: String
    abstract val presetWeekend: String
    abstract val presetEveryDay: String
    abstract val presetOnce: String
    abstract val ifAlertLabel: String
    abstract val save: String
    abstract val deleteAlarm: String
    abstract val soundLabel: String
    abstract val soundDefault: String
    abstract val soundVibrationOnly: String
    abstract val soundFromPhone: String
    abstract val soundFromFile: String
    abstract val soundPickTitle: String
    abstract val preview: String
    abstract val stop: String
    abstract val gradual: String
    abstract val gradualOn: String
    abstract val gradualOff: String
    abstract val vibration: String
    abstract val soundFileGone: String

    // Выбор места
    abstract val yourOblast: String
    abstract val regionDistrict: String
    abstract val regionCommunity: String
    abstract val regionOblastSub: String
    abstract val regionNow: String
    abstract val searchOblast: String
    abstract val searchDistrict: String
    abstract val searchCommunity: String
    abstract val wholeOblast: String
    abstract val wholeOblastHint: String
    abstract val wholeDistrict: String
    abstract val wholeDistrictHint: String
    abstract val loadingDistricts: String
    abstract fun loadFailed(e: String): String
    abstract val retry: String
    abstract val back: String

    // Новости
    abstract val newsTitle: String
    abstract val noChannels: String
    abstract fun updatedAgo(ago: String): String
    abstract val refresh: String
    abstract val filterAlerts: String
    abstract val filterAll: String
    abstract val loading: String
    abstract val emptyAlerts: String
    abstract val emptyAll: String
    abstract val addChannelsHint: String

    // «Ещё»
    abstract val moreTitle: String
    abstract val secAppearance: String
    abstract val themeLabel: String
    abstract val digitsLabel: String
    abstract fun digitName(id: String): String
    abstract fun themeName(id: String): String
    abstract val secAlert: String
    abstract val whenAfterClear: String
    abstract val immediately: String
    abstract fun afterMin(n: Int): String
    abstract val economyTitle: String
    abstract val economyOn: String
    abstract val economyOff: String
    abstract val maxWait: String
    abstract fun hours(n: Int): String
    abstract val maxWaitHint: String
    abstract val neighborsTitle: String
    abstract val neighborsHint: String
    abstract fun sourcesHint(backup: String): String
    abstract val secAlarm: String
    abstract val snoozeFor: String
    abstract fun minutes(n: Int): String
    abstract val secWeather: String
    abstract val weatherToggle: String
    abstract val weatherToggleHint: String
    abstract val secChannels: String
    abstract val channelPlaceholder: String
    abstract val add: String
    abstract val errPrivate: String
    abstract val errNotChannel: String
    abstract val errExists: String
    abstract val channelsHint: String
    abstract fun deleteChannel(ch: String): String
    abstract val secReady: String
    abstract val allSet: String
    abstract val allow: String
    abstract val done: String
    abstract val vendorHint: String
    abstract val openAppSettings: String
    abstract fun permTitle(key: String): String
    abstract fun permHint(key: String): String
    abstract val secTest: String
    abstract val testButton: String
    abstract fun testToast(t: String): String
    abstract val testHint: String
    abstract val secWidget: String
    abstract val widgetHint: String
    abstract val widgetAdd: String
    abstract val widgetAdded: String
    abstract val widgetManual: String
    abstract val secVersion: String
    abstract val checking: String
    abstract val upToDate: String
    abstract val checkUpdates: String
    abstract val updateLabel: String
    abstract fun updateAvailable(v: String): String
    abstract fun downloading(p: Int): String
    abstract val openingInstaller: String
    abstract val needInstallPermission: String
    abstract val updateButton: String
    abstract val tryAgain: String
    abstract val settingsKept: String
    abstract val updateLater: String
    abstract val updateLaterHint: String
    abstract fun updErrCheck(m: String): String
    abstract val updErrNoReleases: String
    abstract fun updErrHttp(c: Int): String
    abstract val updErrBadFile: String
    abstract val updErrOff: String
    abstract fun updErrDownload(m: String): String
    abstract fun updErrInstall(m: String): String
    abstract val updErrCorrupt: String
    abstract val updErrSignature: String
    abstract val updErrInstaller: String
    abstract fun updErrNotInstalled(m: String): String
    abstract val secLog: String
    abstract val logEmpty: String
    abstract val deleteApp: String
    abstract val deleteAppHint: String
    abstract val deleteConfirmTitle: String
    abstract val deleteConfirmText: String
    abstract val cancel: String
    abstract val delete: String
    abstract fun footer(version: String): String

    // Экран звонка
    abstract val timeToGetUp: String
    abstract fun alarmLabel(label: String): String
    abstract val dismiss: String
    abstract fun snoozeBtn(n: Int): String
    abstract val testWord: String

    // Уведомления
    abstract val chRing: String
    abstract val chRingDesc: String
    abstract val chWait: String
    abstract val chWaitDesc: String
    abstract val chInfo: String
    abstract val chInfoDesc: String
    abstract val nChecking: String
    abstract fun nCheckingFor(label: String): String
    abstract fun nWaitTitle(label: String): String
    abstract fun nWaitClear(clear: String, ring: String): String
    abstract fun nNotLater(t: String): String
    abstract fun nSkippedTitle(label: String): String
    abstract fun nSkippedText(place: String): String
    abstract val nSnoozedTitle: String
    abstract fun nSnoozedText(t: String): String
    abstract fun nTimeoutTitle(label: String): String
    abstract val nTimeoutText: String
    abstract fun nUpdateTitle(v: String): String
    abstract val nUpdateText: String

    // Почему будильник зазвонил
    abstract val rNoAlert: String
    abstract val rTestNoAlert: String
    abstract fun rUnknown(why: String): String
    abstract val rAlways: String
    abstract fun rClear(t: String): String
    abstract val rDeadline: String
    abstract val rSnoozed: String
    abstract val rWakeNow: String

    // Журнал
    abstract val lDeleted: String
    abstract fun lAlreadyRinging(l: String): String
    abstract fun lAlreadyWaiting(l: String): String
    abstract fun lSkip(l: String): String
    abstract fun lWait(l: String, t: String): String
    abstract fun lClear(l: String, t: String): String
    abstract fun lAlertAgain(l: String): String
    abstract fun lResume(l: String): String
    abstract fun lCancelled(l: String): String
    abstract fun lRing(l: String, r: String): String
    abstract fun lAutoStop(l: String): String
    abstract fun lDismissed(l: String): String
    abstract fun lSnoozed(l: String, t: String): String
    abstract fun lServiceError(e: String): String
    abstract fun lStartFail(e: String): String
    abstract fun lBackup(l: String, source: String): String
    abstract val lNoSound: String
    abstract fun lTest(t: String): String
    abstract fun lUpdAvailable(v: String): String
    abstract val lUpdInstalling: String
    abstract val lUpdInstalled: String
    abstract fun lUpdFailed(m: String): String

    // Виджет
    abstract val wWaiting: String
    abstract val wPickPlace: String

    // Ошибки сети и источников
    abstract val eNoInternet: String
    abstract val eTimeout: String
    abstract val eNoConnection: String
    abstract val eSsl: String
    abstract val eBadResponse: String
    abstract fun eGeneric(name: String): String
    abstract val eNoPlace: String
    abstract val eBusy: String
    abstract fun eCode(c: Int): String
    abstract val eRegionsEmpty: String
    abstract fun eBackup(m: String): String
    abstract fun eBackupUnknown(name: String): String
    abstract val tgPrivate: String
    abstract val tgNoChannel: String
    abstract fun tgCode(c: Int): String
    abstract val tgNoPosts: String

    // Погода
    abstract fun weatherTitle(city: String): String
    abstract fun feels(t: String): String
    abstract fun precip(p: Int): String
    abstract fun wind(v: Int): String
    abstract fun weatherCode(c: Int): String
    abstract val wearTitle: String
    abstract fun wearBand(band: Int): String
    abstract val wearUmbrella: String
    abstract val wearWaterproof: String
    abstract val wearWind: String
    abstract val wearSun: String
    abstract val wearLayers: String
    abstract val weatherLoading: String
    abstract val weatherFailed: String

    // Места
    open fun oblastName(id: Int, ukName: String): String = ukName
    open fun shortPlace(name: String): String = name
        .replace("територіальна громада", "громада")
        .replace(" область", " обл.")
        .replace(" район", " р-н")
        .trim()
    open fun cityName(ukCity: String, enCity: String): String = ukCity
}
