package ua.otboi.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ua.otboi.app.alarm.Notifs
import ua.otboi.app.data.Store

/**
 * Запускается раньше всего остального — и когда открываешь приложение,
 * и когда ночью срабатывает будильник (приложение при этом может быть закрыто).
 */
class OtboiApp : Application() {
    companion object {
        /** Для фоновых задач, которые не должны обрываться вместе с экраном или службой. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifs.createChannels(this)
    }
}
