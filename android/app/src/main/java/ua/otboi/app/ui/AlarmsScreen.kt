package ua.otboi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.otboi.app.alarm.Scheduler
import ua.otboi.app.alarm.WakeService
import ua.otboi.app.data.Alarm
import ua.otboi.app.data.AlertRule
import ua.otboi.app.data.AppSettings
import ua.otboi.app.data.Feed
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Live
import ua.otboi.app.data.Oblasts
import ua.otboi.app.data.Places
import ua.otboi.app.data.Reading
import ua.otboi.app.data.Store
import ua.otboi.app.data.Waiting
import ua.otboi.app.data.WeatherData
import ua.otboi.app.data.WeatherRepo
import ua.otboi.app.data.Wear
import ua.otboi.app.i18n.T
import ua.otboi.app.net.BackupApi
import ua.otboi.app.net.Post

@Composable
fun AlarmsScreen(
    onEdit: (Alarm) -> Unit,
    onAdd: () -> Unit,
    onNews: () -> Unit,
    onRegion: () -> Unit,
    onMore: () -> Unit,
) {
    val ctx = LocalContext.current
    val alarms by Store.alarms.collectAsState()
    val s by Store.settings.collectAsState()
    val reading by Store.reading.collectAsState()
    val waiting by Store.waiting.collectAsState()
    val posts by Feed.posts.collectAsState()
    val weather by Store.weather.collectAsState()
    val now = rememberNow()
    val tick = rememberResumeTick()
    val missing = remember(tick) { Perms.missing(ctx) }
    val alertPosts = posts.filter { it.isAlert }
    val w = waiting

    LaunchedEffect(s.weather, s.stateId, tick) { if (s.weather) WeatherRepo.refresh() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NowHeader(now)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlaceChip(
                    if (s.stateId > 0) Places.shortName(s) else T.statusNoPlace,
                    Modifier.weight(1f, fill = false),
                    onClick = onRegion,
                )
                Spacer(Modifier.width(10.dp))
                StatusPill(pillFor(reading, s, now))
            }
            if (reading?.backup == true) {
                Txt(T.backupNotice(BackupApi.NAME), 12.sp, C.AmberText, lineHeight = 16.sp)
            }

            if (w != null) {
                WaitingHero(w, s, now)
            } else {
                NextHero(alarms, reading, s, now)
            }

            if (missing > 0) {
                CardBox(background = C.RedBg, border = C.RedBorder, onClick = onMore) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.warn, null, tint = C.RedText, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(T.permsMissing(missing), 15.sp, C.Text, FontWeight.SemiBold)
                            Txt(T.permsMissingHint, 13.sp, C.RedText)
                        }
                        Icon(Ic.chevron, null, tint = C.RedText, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (w == null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(T.tabAlarms.uppercase(T.locale), Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LocalAccent.current)
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Ic.plus, T.addAlarm, tint = C.OnAccent, modifier = Modifier.size(20.dp))
                    }
                }
                alarms.forEach { a ->
                    AlarmRow(
                        a,
                        onClick = { onEdit(a) },
                        onToggle = { on ->
                            Store.upsertAlarm(a.copy(enabled = on))
                            Scheduler.rescheduleAll(ctx)
                        },
                    )
                }
                if (alarms.isEmpty()) {
                    CardBox(onClick = onAdd) {
                        Txt(T.emptyAlarms, 15.sp, C.Text, FontWeight.SemiBold)
                        Txt(T.emptyAlarmsHint, 13.sp, C.Text3)
                    }
                }
                if (s.weather && s.stateId > 0) WeatherCard(weather, s, now)
                NewsTeaser(alertPosts.firstOrNull(), now, onNews)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(T.aboutAlert)
                    if (alertPosts.isEmpty()) Txt(T.noNewsYet, 13.sp, C.Text3)
                    alertPosts.take(3).forEach { PostCard(it, now) }
                }
                OutlineButton(T.wakeNow) { WakeService.start(ctx, WakeService.ACTION_RING, T.rWakeNow) }
                Txt(
                    T.notToday,
                    15.sp, C.Text3, FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { WakeService.start(ctx, WakeService.ACTION_STOP_WAIT) }
                        .padding(12.dp),
                    align = TextAlign.Center,
                )
            }
        }

    }
}

/** Текущее время и дата вверху главного экрана. */
@Composable
private fun NowHeader(now: Long) {
    val d = LocalDigits.current
    val z = Fmt.zoned(now)
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        ClockText(Fmt.hm(now), 44f * d.smallScale, d.small, d.smallWeight, C.Text, LocalAccent.current)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(Fmt.weekday(z), 15.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
            Txt(Fmt.dayMonth(z), 13.sp, C.Text3, maxLines = 1)
        }
    }
}

@Composable
private fun NextHero(alarms: List<Alarm>, reading: Reading?, s: AppSettings, now: Long) {
    val d = LocalDigits.current
    val next = Scheduler.next(alarms, Fmt.zoned(now))
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Txt(T.nextWake, 13.sp, C.Text2, FontWeight.SemiBold, letterSpacing = 3.sp)
        if (next == null) {
            // «Призрачные» цифры: прочерки в тонких шрифтах почти не видно.
            ClockText("00:00", d.heroSize, d.hero, d.heroWeight, C.Ghost, C.Ghost, spacing = d.heroSpacing)
            Txt(T.alarmsOff, 16.sp, C.Text2, align = TextAlign.Center)
            Txt(T.alarmsOffHint, 13.sp, C.Text3, align = TextAlign.Center)
        } else {
            NextDetails(next.first, next.second, reading, s, now)
        }
    }
}

@Composable
private fun NextDetails(alarm: Alarm, at: java.time.ZonedDateTime, reading: Reading?, s: AppSettings, now: Long) {
    val accent = LocalAccent.current
    val d = LocalDigits.current
    val atMs = at.toInstant().toEpochMilli()
    val remaining = atMs - now
    ClockText(alarm.timeText, d.heroSize, d.hero, d.heroWeight, C.Hero, accent, spacing = d.heroSpacing)
    Txt("${Fmt.dayWord(at, Fmt.zoned(now))} · ${Fmt.until(remaining)}", 16.sp, C.Text2, align = TextAlign.Center)
    val window = 10L * 60 * 60 * 1000
    if (remaining in 1..window) {
        val fraction = (1f - remaining.toFloat() / window).coerceIn(0.02f, 1f)
        Box(
            Modifier
                .padding(top = 10.dp)
                .width(220.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(C.Track)
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(accent))
        }
        Txt(T.nightProgress((fraction * 100).toInt()), 12.sp, C.Text3)
    }
    if (Live.isAlertForMe(reading, s) && alarm.rule != AlertRule.ALWAYS) {
        val text = if (alarm.rule == AlertRule.SKIP) T.alertSkipNote(alarm.timeText) else T.alertWaitNote(alarm.timeText)
        Txt(text, 14.sp, C.RedText, align = TextAlign.Center, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun WaitingHero(w: Waiting, s: AppSettings, now: Long) {
    val d = LocalDigits.current
    val delayMin = s.delayAfterClearMin
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(T.wakeDelayed, 13.sp, C.RedText, FontWeight.SemiBold, letterSpacing = 3.sp)
        ClockText(w.time, d.heroSize, d.hero, d.heroWeight, C.Dim, C.Dim, spacing = d.heroSpacing, struck = C.Red)
        val text = when {
            w.clearSince > 0L -> T.clearAt(Fmt.hm(w.clearSince), Fmt.hm(w.clearSince + delayMin * 60_000L))
            delayMin == 0 && s.economy -> T.waitWithinMinute
            delayMin == 0 -> T.waitImmediate
            else -> T.waitAfter(delayMin)
        }
        Txt(text, 16.sp, C.Text2, align = TextAlign.Center, lineHeight = 22.sp, modifier = Modifier.widthIn(max = 320.dp))
    }
    CardBox(background = C.Surface2, border = C.Stroke, radius = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(C.PurpleDeep),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.hourglass, null, tint = C.PurplePale, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(T.canSleep, 12.sp, C.Text3)
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(T.untilWord + " ", 18.sp, C.Text, FontWeight.Medium)
                    ClockText(Fmt.hm(w.deadline), 30f * d.smallScale, d.small, d.smallWeight, C.Text, C.Text)
                }
                Txt(T.maxWaitLeft(s.maxWaitHours, Fmt.duration(w.deadline - now)), 12.sp, C.Text3)
            }
        }
    }
}

/** Погода на сегодня и совет, что надеть. */
@Composable
fun WeatherCard(w: WeatherData?, s: AppSettings, now: Long) {
    val loading by WeatherRepo.loading.collectAsState()
    val failed by WeatherRepo.failed.collectAsState()
    val city = Oblasts.cityName(s.stateId)
    CardBox(background = C.Surface, border = C.StrokeDim) {
        SectionLabel(T.weatherTitle(city.uppercase(ua.otboi.app.i18n.T.locale)))
        Spacer(Modifier.height(8.dp))
        if (w == null || w.stateId != s.stateId) {
            Txt(if (failed && !loading) T.weatherFailed else T.weatherLoading, 14.sp, C.Text3)
        } else {
            WeatherBody(w, now)
        }
    }
}

@Composable
private fun WeatherBody(w: WeatherData, now: Long) {
    val accent = LocalAccent.current
    val d = LocalDigits.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.weather(w.code), T.weatherCode(w.code), tint = accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            ClockText(Fmt.temp(w.temp, plus = false), 40f * d.smallScale, d.small, d.smallWeight, C.Text, C.Text)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(T.weatherCode(w.code), 15.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
                Txt(T.feels(Fmt.temp(w.feels)), 13.sp, C.Text3, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Txt("↑ " + Fmt.temp(w.max), 13.sp, C.Text2, maxLines = 1)
                Txt("↓ " + Fmt.temp(w.min), 13.sp, C.Text3, maxLines = 1)
            }
        }
        val extra = buildList {
            if (w.precipProb > 0) add(T.precip(w.precipProb))
            if (!w.windMax.isNaN()) add(T.wind(Math.round(w.windMax).toInt()))
        }
        if (extra.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Txt(extra.joinToString(" · "), 12.sp, C.Text3)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(C.Surface2)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.shirt, null, tint = C.PurpleLight, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Txt(T.wearTitle, 12.sp, C.Text3)
                Txt(Wear.advice(w, Fmt.zoned(now).hour), 14.sp, C.Text, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
fun AlarmRow(a: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val d = LocalDigits.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Surface)
            .border(1.dp, if (a.enabled) C.Stroke else C.StrokeOff, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val c = if (a.enabled) C.Text else C.Muted
        ClockText(a.timeText, 34f * d.smallScale, d.small, d.smallWeight, c, c, modifier = Modifier.widthIn(min = 96.dp))
        Column(Modifier.weight(1f)) {
            Txt(a.daysText, 14.sp, c, FontWeight.SemiBold, maxLines = 1)
            Txt(a.rule.hint, 12.sp, C.Text3, maxLines = 1)
        }
        Toggle(a.enabled, onToggle, description = T.alarmLabel(a.timeText))
    }
}

@Composable
private fun NewsTeaser(post: Post?, now: Long, onClick: () -> Unit) {
    CardBox(background = C.Surface2, border = C.Stroke, onClick = onClick) {
        Row(Modifier.fillMaxWidth()) {
            SectionLabel(T.latestAlertNews, Modifier.weight(1f))
            if (post != null) Txt(Fmt.ago(post.time, now), 12.sp, C.Text3)
        }
        Spacer(Modifier.height(4.dp))
        if (post == null) {
            Txt(T.newsTeaserEmpty, 14.sp, C.Text3)
        } else {
            Txt(post.text, 15.sp, C.Text, lineHeight = 20.sp, maxLines = 3)
        }
    }
}

@Composable
fun PostCard(p: Post, now: Long) {
    val accent = LocalAccent.current
    val d = LocalDigits.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (p.highlighted) C.Surface2 else C.Surface)
            .border(1.dp, if (p.highlighted) accent.copy(alpha = 0.55f) else C.StrokeDim, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClockText(Fmt.hm(p.time), 20f * d.smallScale, d.small, d.smallWeight, accent, accent, modifier = Modifier.widthIn(min = 54.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(p.text, 14.sp, C.Text, lineHeight = 19.sp, maxLines = 8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("@${p.channel} · ${Fmt.ago(p.time, now)}", 12.sp, C.Text3, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                if (p.highlighted) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(C.PurpleDeep).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Txt(T.yourPlace, 11.sp, C.PurplePale, FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
