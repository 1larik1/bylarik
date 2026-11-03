package ua.otboi.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ua.otboi.app.OtboiApp
import ua.otboi.app.R
import ua.otboi.app.alarm.Scheduler
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Live
import ua.otboi.app.data.Places
import ua.otboi.app.data.Status
import ua.otboi.app.data.Store
import ua.otboi.app.i18n.T
import ua.otboi.app.ui.MainActivity
import ua.otboi.app.ui.Palette
import ua.otboi.app.ui.Palettes
import java.time.format.TextStyle

/** Виджет на главном экране: часы, дата, статус тревоги и ближайший будильник. */
class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.render(context, manager, ids)
        Widgets.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        Widgets.render(context, manager, intArrayOf(id))
    }

    override fun onEnabled(context: Context) {
        Widgets.schedule(context)
    }

    override fun onDisabled(context: Context) {
        Widgets.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Widgets.ACTION_REFRESH -> Widgets.refresh(context, goAsync(), force = true)
            Widgets.ACTION_TICK -> Widgets.refresh(context, goAsync(), force = false)
        }
    }
}

/**
 * Всё про виджет. Часы и дату рисует сама система (TextClock), поэтому раз в минуту
 * приложению просыпаться не нужно. Статус тревоги обновляется:
 *  - когда меняются будильники, настройки, ожидание отбоя или результат проверки;
 *  - по нажатию на статус;
 *  - раз в 15 минут (в экономном режиме — раз в 30), но только пока телефон и так не спит:
 *    будильник Android без пробуждения, ночью с выключенным экраном он ничего не делает.
 */
object Widgets {
    const val ACTION_REFRESH = "ua.otboi.app.widget.REFRESH"
    const val ACTION_TICK = "ua.otboi.app.widget.TICK"

    /** Статус старше этого не показываем: лучше «Обновить», чем устаревшее «тревоги нет». */
    private const val FRESH_MS = 30L * 60 * 1000

    /** Плановую проверку пропускаем, если проверяли совсем недавно (например, приложение открыто). */
    private const val RECENT_MS = 10L * 60 * 1000

    @Volatile
    private var checking = false

    private fun component(ctx: Context) = ComponentName(ctx, ClockWidget::class.java)

    fun ids(ctx: Context): IntArray = try {
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(component(ctx)) ?: IntArray(0)
    } catch (_: Exception) {
        IntArray(0)
    }

    fun hasAny(ctx: Context): Boolean = ids(ctx).isNotEmpty()

    /** Умеет ли launcher добавить виджет по кнопке из приложения. */
    fun canPin(ctx: Context): Boolean = try {
        AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported
    } catch (_: Exception) {
        false
    }

    /** Попросить launcher добавить виджет (он сам покажет окно подтверждения). */
    fun requestPin(ctx: Context): Boolean = try {
        AppWidgetManager.getInstance(ctx).requestPinAppWidget(component(ctx), null, null)
    } catch (_: Exception) {
        false
    }

    /** Перерисовать все виджеты (если они есть). */
    fun update(ctx: Context) {
        val ids = ids(ctx)
        if (ids.isEmpty()) return
        render(ctx, AppWidgetManager.getInstance(ctx), ids)
    }

    fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try {
                manager.updateAppWidget(id, build(ctx.applicationContext, clockSize(manager.getAppWidgetOptions(id))))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Размер цифр под высоту виджета: в низком — поменьше, чтобы влезла строка с будильником,
     * в высоком — крупнее, чтобы не было пустоты. Высота в портретной ориентации — MAX_HEIGHT.
     */
    private fun clockSize(options: Bundle?): Float {
        val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        if (h <= 0) return 46f
        return ((h - 76) / 1.2f).coerceIn(34f, 80f)
    }

    /** Проверить тревогу и перерисовать. force — по нажатию; иначе плановая (пропускаем, если данные свежие). */
    fun refresh(ctx: Context, pending: BroadcastReceiver.PendingResult?, force: Boolean) {
        val app = ctx.applicationContext
        val s = Store.settings.value
        val r = Store.reading.value
        val recent = r != null && r.status != Status.UNKNOWN && System.currentTimeMillis() - r.at < RECENT_MS
        // Пока ждём отбоя, тревогу и так проверяет служба будильника.
        if (s.stateId <= 0 || checking || Store.waiting.value != null || (!force && recent)) {
            update(app)
            pending?.finish()
            return
        }
        checking = true
        update(app)
        OtboiApp.scope.launch {
            try {
                withTimeoutOrNull(20_000L) { Live.check(app) }
            } catch (_: Exception) {
            } finally {
                checking = false
                update(app)
                pending?.finish()
            }
        }
    }

    private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 202,
        Intent(ctx, ClockWidget::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Плановое обновление статуса. ELAPSED_REALTIME (без _WAKEUP) — спящий телефон не будит. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val every = if (Store.settings.value.economy) AlarmManager.INTERVAL_HALF_HOUR else AlarmManager.INTERVAL_FIFTEEN_MINUTES
        try {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + every, every, tickIntent(ctx))
        } catch (_: Exception) {
        }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx))
    }

    private fun openApp(ctx: Context): PendingIntent {
        val intent = (ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent(ctx, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun refreshIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 201,
        Intent(ctx, ClockWidget::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun Long.argb(): Int = toInt()

    private fun background(p: Palette): Int = when (p.id) {
        "graphite" -> R.drawable.widget_bg_graphite
        "ocean" -> R.drawable.widget_bg_ocean
        "light" -> R.drawable.widget_bg_light
        else -> R.drawable.widget_bg_night
    }

    private class Chip(val text: CharSequence, val color: Int, val bg: Int)

    private fun dotted(label: String, dot: Int, text: Int, bg: Int): Chip {
        val sb = SpannableStringBuilder("● ").append(label)
        sb.setSpan(ForegroundColorSpan(dot), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return Chip(sb, text, bg)
    }

    private fun chip(p: Palette, now: Long): Chip {
        val s = Store.settings.value
        val r = Store.reading.value
        val fresh = r != null && r.status != Status.UNKNOWN && now - r.at < FRESH_MS
        return when {
            s.stateId <= 0 -> Chip(T.wPickPlace, p.text2.argb(), R.drawable.widget_chip_grey)
            checking -> Chip(T.checking, p.text2.argb(), R.drawable.widget_chip_grey)
            fresh && Live.isAlertForMe(r, s) -> dotted(T.statusAlert(""), p.red.argb(), p.redText.argb(), R.drawable.widget_chip_red)
            fresh && r!!.status == Status.PARTIAL -> dotted(T.statusNearby, p.amber.argb(), p.amberText.argb(), R.drawable.widget_chip_amber)
            fresh -> dotted(T.statusClear, p.mint.argb(), p.mintText.argb(), R.drawable.widget_chip_mint)
            Store.waiting.value != null -> dotted(T.statusAlert(""), p.red.argb(), p.redText.argb(), R.drawable.widget_chip_red)
            else -> Chip(T.refresh, p.text2.argb(), R.drawable.widget_chip_grey)
        }
    }

    private fun build(ctx: Context, clockDp: Float): RemoteViews {
        val s = Store.settings.value
        val p = Palettes.byId(s.themeId)
        val now = System.currentTimeMillis()
        val v = RemoteViews(ctx.packageName, R.layout.widget_clock)

        v.setInt(R.id.w_root, "setBackgroundResource", background(p))
        v.setTextColor(R.id.w_clock, p.hero.argb())
        v.setTextViewTextSize(R.id.w_clock, TypedValue.COMPLEX_UNIT_DIP, clockDp)
        v.setTextColor(R.id.w_date, p.text2.argb())
        val datePattern = "EEEE, " + T.dayMonthPattern
        v.setCharSequence(R.id.w_date, "setFormat12Hour", datePattern)
        v.setCharSequence(R.id.w_date, "setFormat24Hour", datePattern)

        val c = chip(p, now)
        v.setTextViewText(R.id.w_status, c.text)
        v.setTextColor(R.id.w_status, c.color)
        v.setInt(R.id.w_status, "setBackgroundResource", c.bg)

        val gap = (6 * ctx.resources.displayMetrics.density).toInt()
        val w = Store.waiting.value
        val next = Scheduler.next(Store.alarms.value, Fmt.zoned(now))
        when {
            w != null -> {
                v.setImageViewResource(R.id.w_icon, R.drawable.ic_w_hourglass)
                v.setInt(R.id.w_icon, "setColorFilter", p.redText.argb())
                v.setViewVisibility(R.id.w_alarm, View.VISIBLE)
                val struck = SpannableString(w.time)
                struck.setSpan(StrikethroughSpan(), 0, struck.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                v.setTextViewText(R.id.w_alarm, struck)
                v.setTextColor(R.id.w_alarm, p.text2.argb())
                v.setTextViewText(R.id.w_tail, " · " + T.wWaiting)
                v.setTextColor(R.id.w_tail, p.redText.argb())
                v.setViewPadding(R.id.w_tail, 0, 0, 0, 0)
            }
            next != null -> {
                v.setImageViewResource(R.id.w_icon, R.drawable.ic_w_alarm)
                v.setInt(R.id.w_icon, "setColorFilter", p.accent.argb())
                v.setViewVisibility(R.id.w_alarm, View.VISIBLE)
                v.setTextViewText(R.id.w_alarm, next.first.timeText)
                v.setTextColor(R.id.w_alarm, p.text.argb())
                // День недели, а не «завтра»: так подпись не устареет в полночь, пока телефон спит.
                v.setTextViewText(R.id.w_tail, " · " + next.second.dayOfWeek.getDisplayName(TextStyle.SHORT, T.locale))
                v.setTextColor(R.id.w_tail, p.text2.argb())
                v.setViewPadding(R.id.w_tail, 0, 0, 0, 0)
            }
            else -> {
                v.setImageViewResource(R.id.w_icon, R.drawable.ic_w_alarm)
                v.setInt(R.id.w_icon, "setColorFilter", p.muted.argb())
                v.setViewVisibility(R.id.w_alarm, View.GONE)
                v.setTextViewText(R.id.w_tail, T.alarmsOff)
                v.setTextColor(R.id.w_tail, p.text3.argb())
                v.setViewPadding(R.id.w_tail, gap, 0, 0, 0)
            }
        }
        v.setTextViewText(R.id.w_place, Places.shortName(s))
        v.setTextColor(R.id.w_place, p.text3.argb())

        val open = openApp(ctx)
        v.setOnClickPendingIntent(R.id.w_root, open)
        v.setOnClickPendingIntent(R.id.w_status, if (s.stateId > 0) refreshIntent(ctx) else open)
        return v
    }
}
