package ua.otboi.app

import android.app.Application
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ua.otboi.app.alarm.Notifs
import ua.otboi.app.data.Store
import ua.otboi.app.i18n.L
import ua.otboi.app.ui.Palettes
import ua.otboi.app.ui.Th

/**
 * Запускается раньше всего остального: и когда открываешь приложение,
 * и когда ночью срабатывает будильник (приложение при этом может быть закрыто).
 */
class OtboiApp : Application() {
    companion object {
        /** Для фоновых задач, которые не должны обрываться вместе с экраном или службой. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onCreate() {
        super.onCreate()
        L.init(this)
        Store.init(this)
        Th.p = Palettes.byId(Store.settings.value.themeId)
        Notifs.createChannels(this)
    }

    /** Сменили язык телефона — тексты и названия каналов уведомлений на новом языке. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        L.init(this)
        Notifs.createChannels(this)
    }
}
