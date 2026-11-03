package ua.otboi.app

import android.app.Application
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ua.otboi.app.alarm.Notifs
import ua.otboi.app.data.Store
import ua.otboi.app.i18n.L
import ua.otboi.app.ui.Palettes
import ua.otboi.app.ui.Th
import ua.otboi.app.widget.Widgets

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
        watchWidget()
    }

    /** Виджет перерисовывается сам, когда меняются будильники, настройки, ожидание или статус тревоги. */
    private fun watchWidget() {
        val app = this
        scope.launch {
            combine(Store.alarms, Store.settings, Store.reading, Store.waiting) { a, s, r, w -> listOf(a, s, r, w) }
                .collectLatest {
                    delay(300)
                    Widgets.update(app)
                }
        }
        scope.launch {
            Store.settings.map { it.economy }.distinctUntilChanged().collect {
                if (Widgets.hasAny(app)) Widgets.schedule(app)
            }
        }
    }

    /** Сменили язык телефона — тексты и названия каналов уведомлений на новом языке. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        L.init(this)
        Notifs.createChannels(this)
        Widgets.update(this)
    }
}
