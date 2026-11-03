package ua.otboi.app.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.otboi.app.data.Alarm
import ua.otboi.app.data.AlertRule
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Live
import ua.otboi.app.data.Reading
import ua.otboi.app.data.Status
import ua.otboi.app.data.Store
import ua.otboi.app.data.Waiting
import ua.otboi.app.ui.RingActivity

/**
 * Сердце будильника. Когда приходит время:
 *  1) проверяет тревогу в выбранном месте;
 *  2) тревоги нет — звонит;
 *  3) тревога — по правилу будильника либо не звонит, либо ждёт отбоя (проверка каждые 20 с, в режиме экономии — раз в минуту)
 *     и звонит сразу после отбоя (или через N минут — настройка), но не позже предельного времени.
 *  Если проверить не удалось — звонит на всякий случай.
 */
class WakeService : Service() {
    companion object {
        const val ACTION_FIRE = "ua.otboi.app.action.FIRE"
        const val ACTION_RESUME = "ua.otboi.app.action.RESUME"
        const val ACTION_RING = "ua.otboi.app.action.RING"
        const val ACTION_DISMISS = "ua.otboi.app.action.DISMISS"
        const val ACTION_SNOOZE = "ua.otboi.app.action.SNOOZE"
        const val ACTION_STOP_WAIT = "ua.otboi.app.action.STOP_WAIT"
        const val EXTRA_ALARM = "alarmId"
        const val EXTRA_PLANNED = "planned"
        const val EXTRA_LABEL = "label"
        const val EXTRA_REASON = "reason"

        private const val ID_WORK = 41
        private const val ID_RING = 42

        /** Как часто проверять тревогу, пока ждём отбоя: обычно и в режиме экономии заряда. */
        private const val POLL_MS = 20_000L
        private const val POLL_ECONOMY_MS = 60_000L

        fun intent(ctx: Context, action: String): Intent = Intent(ctx, WakeService::class.java).setAction(action)

        fun start(ctx: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(ctx, intent)
            } catch (e: Exception) {
                Store.addLog("Не удалось запустить проверку: ${e.javaClass.simpleName}")
            }
        }

        fun start(ctx: Context, action: String, reason: String? = null) {
            val i = intent(ctx, action)
            if (reason != null) i.putExtra(EXTRA_REASON, reason)
            start(ctx, i)
        }

        fun pending(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(
            ctx, code, intent(ctx, action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private enum class Mode { IDLE, CHECKING, WAITING, RINGING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mode = Mode.IDLE
    private var job: Job? = null
    private var autoStop: Job? = null
    private var ringer: Ringer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fgId = ID_WORK
    private var fgNotif: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(fgId, fgNotif ?: Notifs.working(this, "Проверяю тревогу…"))
        when (intent?.action ?: ACTION_RESUME) {
            ACTION_FIRE -> onFire(
                intent?.getLongExtra(EXTRA_ALARM, 0L) ?: 0L,
                intent?.getLongExtra(EXTRA_PLANNED, 0L) ?: 0L,
            )
            ACTION_RING -> {
                val label = intent?.getStringExtra(EXTRA_LABEL)
                    ?: Store.waiting.value?.title
                    ?: Fmt.hm(System.currentTimeMillis())
                val reason = intent?.getStringExtra(EXTRA_REASON) ?: "Разбудить сейчас"
                cancelJobs()
                ring(label, reason)
            }
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            ACTION_STOP_WAIT -> stopWaiting()
            else -> resume()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ringer?.stop()
        ringer = null
        RingState.current.value = null
        releaseWake()
        super.onDestroy()
    }

    // MARK: срабатывание

    private fun onFire(alarmId: Long, plannedIn: Long) {
        val planned = if (plannedIn > 0L) plannedIn else System.currentTimeMillis()
        val test = alarmId == Scheduler.TEST_ID
        val stored = Store.alarms.value.firstOrNull { it.id == alarmId }
        val alarm: Alarm? = if (test) {
            val z = Fmt.zoned(planned)
            Alarm(alarmId, z.hour, z.minute, emptySet(), true, AlertRule.AFTER_CLEAR)
        } else {
            stored
        }
        // Одноразовый будильник после срабатывания выключается.
        if (!test && stored != null && stored.days.isEmpty()) Store.upsertAlarm(stored.copy(enabled = false))
        Scheduler.rescheduleAll(this)

        if (alarm == null || !alarm.enabled) {
            Store.addLog("Сработал удалённый или выключенный будильник — пропускаю")
            if (mode == Mode.IDLE) finish()
            return
        }
        val label = (if (test) "Тест " else "") + alarm.timeText

        when (mode) {
            Mode.RINGING -> {
                Store.addLog("$label: уже звонит другой будильник")
                return
            }
            Mode.WAITING, Mode.CHECKING -> {
                if (alarm.rule == AlertRule.ALWAYS) {
                    cancelJobs()
                    ring(label, "Будильник без учёта тревог")
                } else {
                    Store.addLog("$label: уже жду отбоя по другому будильнику")
                }
                return
            }
            Mode.IDLE -> Unit
        }

        if (alarm.rule == AlertRule.ALWAYS) {
            ring(label, "Будильник без учёта тревог")
            return
        }

        mode = Mode.CHECKING
        acquireWake(10 * 60_000L)
        goForeground(ID_WORK, Notifs.working(this, "Будильник $label: проверяю тревогу…"))
        job = scope.launch {
            val reading = checkWithRetries()
            val s = Store.settings.value
            if (reading.backup) Store.addLog("$label: основной источник не ответил — решаю по запасному (${ua.otboi.app.net.BackupApi.NAME})")
            when {
                reading.status == Status.UNKNOWN ->
                    ring(label, "Не удалось проверить тревогу (${reading.reason}) — бужу на всякий случай")
                !Live.isAlertForMe(reading, s) ->
                    ring(label, if (test) "Тест: тревоги нет — будильник звонит" else "Тревоги нет — пора вставать")
                alarm.rule == AlertRule.SKIP -> {
                    Store.addLog("$label: тревога — не бужу (правило «Не будить»)")
                    Notifs.info(
                        this@WakeService,
                        "Будильник $label не звонил",
                        "Тревога" + (if (s.placeShort.isNotBlank()) " · ${s.placeShort}" else "") + ". Спи дальше."
                    )
                    finish()
                }
                else -> startWaiting(
                    Waiting(
                        alarmId = alarm.id,
                        time = alarm.timeText,
                        planned = planned,
                        deadline = planned + s.maxWaitHours * 3_600_000L,
                        clearSince = 0L,
                        test = test,
                    )
                )
            }
        }
    }

    private suspend fun checkWithRetries(): Reading {
        var last = Reading(Status.UNKNOWN, "нет ответа")
        for (attempt in 0 until 4) {
            last = Live.check(applicationContext)
            if (last.status != Status.UNKNOWN) return last
            if (attempt < 3) delay(15_000)
        }
        return last
    }

    // MARK: ожидание отбоя

    private fun startWaiting(w: Waiting) {
        if (System.currentTimeMillis() >= w.deadline) {
            ring(w.title, "Тревога ещё идёт, но ждать дольше нельзя — пора вставать")
            return
        }
        Store.setWaiting(w)
        Store.addLog("${w.title}: тревога — жду отбоя (не позже ${Fmt.hm(w.deadline)})")
        Scheduler.scheduleDeadline(this, w.deadline)
        enterWaitLoop()
    }

    private fun enterWaitLoop() {
        val w = Store.waiting.value
        if (w == null) {
            finish()
            return
        }
        mode = Mode.WAITING
        acquireWake(w.deadline - System.currentTimeMillis() + 15 * 60_000L)
        updateWaitNotif()
        job = scope.launch { waitLoop() }
    }

    private suspend fun waitLoop() {
        while (true) {
            val w = Store.waiting.value ?: return
            val s = Store.settings.value
            val delayMs = s.delayAfterClearMin * 60_000L
            val now = System.currentTimeMillis()
            if (w.clearSince > 0L && now >= w.clearSince + delayMs) {
                ring(w.title, "Отбой в ${Fmt.hm(w.clearSince)} — пора вставать")
                return
            }
            if (now >= w.deadline) {
                ring(w.title, "Тревога ещё идёт, но ждать дольше нельзя — пора вставать")
                return
            }
            val r = Live.check(applicationContext)
            if (r.status != Status.UNKNOWN) {
                val cur = Store.waiting.value ?: return
                val alert = Live.isAlertForMe(r, s)
                if (!alert && cur.clearSince == 0L) {
                    val t = System.currentTimeMillis()
                    Store.setWaiting(cur.copy(clearSince = t))
                    Store.addLog("${cur.title}: отбой — зазвоню в ${Fmt.hm(t + delayMs)}")
                } else if (alert && cur.clearSince > 0L) {
                    Store.setWaiting(cur.copy(clearSince = 0L))
                    Store.addLog("${cur.title}: снова тревога — жду дальше")
                }
            }
            updateWaitNotif()
            val w2 = Store.waiting.value ?: return
            val t = System.currentTimeMillis()
            val poll = if (Store.settings.value.economy) POLL_ECONOMY_MS else POLL_MS
            var next = t + poll
            if (w2.clearSince > 0L) next = minOf(next, w2.clearSince + delayMs)
            next = minOf(next, w2.deadline)
            delay((next - t).coerceIn(1_000L, poll))
        }
    }

    private fun updateWaitNotif() {
        val w = Store.waiting.value ?: return
        goForeground(ID_WORK, Notifs.waiting(this, w, Store.settings.value, Store.reading.value))
    }

    private fun resume() {
        if (mode != Mode.IDLE) return
        val w = Store.waiting.value
        if (w == null) {
            finish()
            return
        }
        if (System.currentTimeMillis() >= w.deadline) {
            ring(w.title, "Тревога ещё идёт, но ждать дольше нельзя — пора вставать")
            return
        }
        Store.addLog("${w.title}: продолжаю ждать отбоя")
        enterWaitLoop()
    }

    private fun stopWaiting() {
        val w = Store.waiting.value
        if (w != null) Store.addLog("${w.title}: ожидание отменено — сегодня не бужу")
        Store.setWaiting(null)
        Scheduler.cancelDeadline(this)
        if (mode != Mode.RINGING) finish()
    }

    // MARK: звонок

    private fun ring(label: String, reason: String) {
        Store.setWaiting(null)
        Scheduler.cancelDeadline(this)
        mode = Mode.RINGING
        Store.addLog("$label: звоню — $reason")
        val info = RingInfo(label, reason, System.currentTimeMillis())
        RingState.current.value = info
        goForeground(ID_RING, Notifs.ringing(this, info, Store.settings.value.snoozeMin))
        ringer?.stop()
        ringer = Ringer(this).also { it.start(scope) }
        acquireWake(12 * 60_000L)
        try {
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        autoStop?.cancel()
        autoStop = scope.launch {
            delay(10 * 60_000L)
            if (mode == Mode.RINGING) {
                Store.addLog("$label: звонил 10 минут — выключился сам")
                Notifs.info(this@WakeService, "Будильник $label звонил 10 минут", "Его никто не выключил.")
                finish()
            }
        }
    }

    private fun dismiss() {
        if (mode == Mode.RINGING) Store.addLog("${RingState.current.value?.label ?: "Будильник"}: выключен")
        finish()
    }

    private fun snooze() {
        val info = RingState.current.value
        if (mode == Mode.RINGING) {
            val min = Store.settings.value.snoozeMin
            val at = System.currentTimeMillis() + min * 60_000L
            val label = info?.label ?: Fmt.hm(System.currentTimeMillis())
            Scheduler.scheduleSnooze(this, at, label)
            Store.addLog("$label: отложен до ${Fmt.hm(at)}")
            Notifs.info(this, "Будильник отложен", "Зазвонит в ${Fmt.hm(at)}")
        }
        finish()
    }

    // MARK: служебное

    private fun goForeground(id: Int, n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, id, n, type)
        } catch (e: Exception) {
            Store.addLog("Ошибка службы: ${e.javaClass.simpleName}")
        }
        if (id != fgId) {
            try {
                getSystemService(NotificationManager::class.java)?.cancel(fgId)
            } catch (_: Exception) {
            }
        }
        fgId = id
        fgNotif = n
    }

    private fun cancelJobs() {
        job?.cancel()
        job = null
        autoStop?.cancel()
        autoStop = null
    }

    private fun finish() {
        cancelJobs()
        ringer?.stop()
        ringer = null
        RingState.current.value = null
        mode = Mode.IDLE
        releaseWake()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        fgNotif = null
        fgId = ID_WORK
        stopSelf()
    }

    private fun acquireWake(ms: Long) {
        val pm = getSystemService(PowerManager::class.java) ?: return
        val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "otboi:wake").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        wl.acquire(ms.coerceAtLeast(60_000L))
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
