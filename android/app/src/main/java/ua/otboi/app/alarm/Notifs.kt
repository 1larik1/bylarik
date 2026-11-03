package ua.otboi.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ua.otboi.app.R
import ua.otboi.app.data.AppSettings
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Reading
import ua.otboi.app.data.Store
import ua.otboi.app.data.Waiting
import ua.otboi.app.ui.MainActivity
import ua.otboi.app.ui.RingActivity

data class RingInfo(val label: String, val reason: String, val startedAt: Long)

/** Что сейчас звонит — экран будильника следит за этим. */
object RingState {
    val current = kotlinx.coroutines.flow.MutableStateFlow<RingInfo?>(null)
}

object Notifs {
    const val CH_RING = "ring"
    const val CH_WAIT = "wait"
    const val CH_INFO = "info"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val ring = NotificationChannel(CH_RING, "Будильник", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Звонящий будильник"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val wait = NotificationChannel(CH_WAIT, "Ожидание отбоя", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Будильник ждёт отбоя тревоги"
            setShowBadge(false)
        }
        val info = NotificationChannel(CH_INFO, "Сообщения", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Пропущенные и отложенные будильники"
        }
        nm.createNotificationChannels(listOf(ring, wait, info))
    }

    private fun accent(): Int = Store.settings.value.accent.toInt()

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 100,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun working(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_WAIT)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(accent())
            .setContentTitle("bylarik")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()

    fun waiting(ctx: Context, w: Waiting, s: AppSettings, r: Reading?): Notification {
        val ringAt = if (w.clearSince > 0L) w.clearSince + s.delayAfterClearMin * 60_000L else 0L
        val title = if (ringAt > 0L) {
            "Отбой в ${Fmt.hm(w.clearSince)} — разбужу в ${Fmt.hm(ringAt)}"
        } else {
            "Тревога — будильник ${w.title} ждёт отбоя"
        }
        val text = "Не позже ${Fmt.hm(w.deadline)}" + if (s.placeShort.isNotBlank()) " · ${s.placeShort}" else ""
        val b = NotificationCompat.Builder(ctx, CH_WAIT)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(accent())
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Разбудить сейчас", WakeService.pending(ctx, WakeService.ACTION_RING, 201))
            .addAction(0, "Не будить сегодня", WakeService.pending(ctx, WakeService.ACTION_STOP_WAIT, 202))
        val since = r?.since ?: 0L
        if (ringAt == 0L && since > 0L) {
            b.setWhen(since).setShowWhen(true).setUsesChronometer(true)
        }
        return b.build()
    }

    fun ringing(ctx: Context, info: RingInfo, snoozeMin: Int): Notification {
        val full = PendingIntent.getActivity(
            ctx, 300,
            Intent(ctx, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CH_RING)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(accent())
            .setContentTitle("Будильник ${info.label}")
            .setContentText(info.reason)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Выключить", WakeService.pending(ctx, WakeService.ACTION_DISMISS, 301))
            .addAction(0, "Отложить $snoozeMin мин", WakeService.pending(ctx, WakeService.ACTION_SNOOZE, 302))
            .build()
    }

    fun info(ctx: Context, title: String, text: String) {
        val n = NotificationCompat.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(accent())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java)
                ?.notify((System.currentTimeMillis() % 100_000L).toInt() + 1_000, n)
        } catch (_: SecurityException) {
        }
    }
}
