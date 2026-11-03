package ua.otboi.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ua.otboi.app.data.Alarm
import ua.otboi.app.data.Store
import ua.otboi.app.ui.MainActivity
import java.time.ZonedDateTime

/** Ставит системные будильники (AlarmManager.setAlarmClock — срабатывают даже в глубоком сне). */
object Scheduler {
    const val ACTION_FIRE = "ua.otboi.app.alarm.FIRE"
    const val ACTION_SNOOZE = "ua.otboi.app.alarm.SNOOZE"
    const val ACTION_DEADLINE = "ua.otboi.app.alarm.DEADLINE"
    const val EXTRA_ALARM = "alarmId"
    const val EXTRA_PLANNED = "planned"
    const val EXTRA_LABEL = "label"
    const val TEST_ID = -1L

    private const val RC_NEXT = 1
    private const val RC_DEADLINE = 2
    private const val RC_SNOOZE = 3
    private const val RC_TEST = 4

    /** Ближайшее время срабатывания будильника после now (или null, если выключен). */
    fun nextTime(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        val base = now.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)
        for (i in 0..7) {
            val c = base.plusDays(i.toLong())
            if (!c.isAfter(now)) continue
            if (alarm.days.isEmpty() || c.dayOfWeek.value in alarm.days) return c
        }
        return null
    }

    fun next(alarms: List<Alarm>, now: ZonedDateTime): Pair<Alarm, ZonedDateTime>? =
        alarms.mapNotNull { a -> nextTime(a, now)?.let { a to it } }.minByOrNull { it.second.toInstant() }

    private fun receiver(ctx: Context, action: String): Intent =
        Intent(ctx, AlarmReceiver::class.java).setAction(action)

    private fun pending(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun rescheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val n = next(Store.alarms.value, ZonedDateTime.now())
        if (n == null) {
            am.cancel(pending(ctx, RC_NEXT, receiver(ctx, ACTION_FIRE)))
            return
        }
        val at = n.second.toInstant().toEpochMilli()
        val intent = receiver(ctx, ACTION_FIRE)
            .putExtra(EXTRA_ALARM, n.first.id)
            .putExtra(EXTRA_PLANNED, at)
        setClock(ctx, am, at, pending(ctx, RC_NEXT, intent))
    }

    fun scheduleDeadline(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        setClock(ctx, am, at, pending(ctx, RC_DEADLINE, receiver(ctx, ACTION_DEADLINE)))
    }

    fun cancelDeadline(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx, RC_DEADLINE, receiver(ctx, ACTION_DEADLINE)))
    }

    fun scheduleSnooze(ctx: Context, at: Long, label: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        setClock(ctx, am, at, pending(ctx, RC_SNOOZE, receiver(ctx, ACTION_SNOOZE).putExtra(EXTRA_LABEL, label)))
    }

    /** Тест: через минуту сработает будильник с правилом «после отбоя» — вся цепочка как по-настоящему. */
    fun scheduleTest(ctx: Context): Long {
        val at = System.currentTimeMillis() + 60_000L
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return at
        val intent = receiver(ctx, ACTION_FIRE).putExtra(EXTRA_ALARM, TEST_ID).putExtra(EXTRA_PLANNED, at)
        setClock(ctx, am, at, pending(ctx, RC_TEST, intent))
        return at
    }

    private fun setClock(ctx: Context, am: AlarmManager, at: Long, op: PendingIntent) {
        val show = PendingIntent.getActivity(
            ctx, 9, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
                return
            }
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }
}
