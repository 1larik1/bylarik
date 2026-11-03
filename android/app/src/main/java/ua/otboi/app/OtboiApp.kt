package ua.otboi.app

import android.app.Application
import ua.otboi.app.alarm.Notifs
import ua.otboi.app.data.Store

/**
 * Запускается раньше всего остального — и когда открываешь приложение,
 * и когда ночью срабатывает будильник (приложение при этом может быть закрыто).
 */
class OtboiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifs.createChannels(this)
    }
}
