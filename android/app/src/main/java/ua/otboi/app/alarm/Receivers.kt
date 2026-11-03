package ua.otboi.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ua.otboi.app.data.Store

/** Срабатывание системного будильника → запускаем службу, которая проверит тревогу. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Scheduler.ACTION_FIRE -> WakeService.start(
                context,
                WakeService.intent(context, WakeService.ACTION_FIRE)
                    .putExtra(WakeService.EXTRA_ALARM, intent.getLongExtra(Scheduler.EXTRA_ALARM, 0L))
                    .putExtra(WakeService.EXTRA_PLANNED, intent.getLongExtra(Scheduler.EXTRA_PLANNED, 0L))
            )
            Scheduler.ACTION_SNOOZE -> WakeService.start(
                context,
                WakeService.intent(context, WakeService.ACTION_RING)
                    .putExtra(WakeService.EXTRA_LABEL, intent.getStringExtra(Scheduler.EXTRA_LABEL))
                    .putExtra(WakeService.EXTRA_REASON, "Отложенный будильник")
            )
            Scheduler.ACTION_DEADLINE -> WakeService.start(context, WakeService.ACTION_RESUME)
        }
    }
}

/** После перезагрузки, обновления приложения или смены времени — заново ставим будильники. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context)
        val w = Store.waiting.value ?: return
        Scheduler.scheduleDeadline(context, w.deadline)
        WakeService.start(context, WakeService.ACTION_RESUME)
    }
}
