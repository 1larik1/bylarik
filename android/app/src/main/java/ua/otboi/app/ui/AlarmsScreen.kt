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
import androidx.compose.ui.graphics.Color
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
import ua.otboi.app.data.Reading
import ua.otboi.app.data.Store
import ua.otboi.app.data.Waiting
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
    val now = rememberNow()
    val tick = rememberResumeTick()
    val missing = remember(tick) { Perms.missing(ctx) }
    val update by ua.otboi.app.update.Updater.state.collectAsState()
    val alertPosts = posts.filter { it.isAlert }
    val w = waiting

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlaceChip(
                    if (s.stateId > 0) s.placeShort.ifBlank { "Область" } else "Выбрать область",
                    Modifier.weight(1f, fill = false),
                    onClick = onRegion,
                )
                Spacer(Modifier.width(10.dp))
                StatusPill(pillFor(reading, s, now))
            }
            if (reading?.backup == true) {
                Txt(
                    "Основной источник тревог не отвечает — данные из запасного (${ua.otboi.app.net.BackupApi.NAME}, по районам)",
                    12.sp, C.AmberText, lineHeight = 16.sp,
                )
            }

            if (w != null) {
                WaitingHero(w, s, now)
            } else {
                NextHero(alarms, reading, s, now)
            }

            if (missing > 0) {
                CardBox(background = C.RedBg, border = Color(0xFF4A1726), onClick = onMore) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.warn, null, tint = C.RedText, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt("Нужно разрешение: $missing", 15.sp, C.Text, FontWeight.SemiBold)
                            Txt("Без него будильник может не сработать ночью", 13.sp, C.RedText)
                        }
                        Icon(Ic.chevron, null, tint = C.RedText, modifier = Modifier.size(20.dp))
                    }
                }
            }

            UpdateCard(update, compact = true)

            if (w == null) {
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
                        Txt("Будильников пока нет", 15.sp, C.Text, FontWeight.SemiBold)
                        Txt("Нажми «+», чтобы добавить", 13.sp, C.Text3)
                    }
                }
                NewsTeaser(alertPosts.firstOrNull(), now, onNews)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("ПРО ТРЕВОГУ · ПОСЛЕДНИЕ 3")
                    if (alertPosts.isEmpty()) {
                        Txt("Пока ничего — новости появятся здесь", 13.sp, C.Text3)
                    }
                    alertPosts.take(3).forEach { PostCard(it, now) }
                }
                OutlineButton("Разбудить сейчас") { WakeService.start(ctx, WakeService.ACTION_RING, "Разбудить сейчас") }
                Txt(
                    "Не будить сегодня",
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

        if (w == null) {
            val accent = LocalAccent.current
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.plus, "Добавить будильник", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun NextHero(alarms: List<Alarm>, reading: Reading?, s: AppSettings, now: Long) {
    val accent = LocalAccent.current
    val d = LocalDigits.current
    val nowZ = Fmt.zoned(now)
    val next = Scheduler.next(alarms, nowZ)
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Txt("СЛЕДУЮЩИЙ ПОДЪЁМ", 13.sp, C.Text2, FontWeight.SemiBold, letterSpacing = 3.sp)
        if (next == null) {
            // «Призрачные» цифры: прочерки в тонких шрифтах почти не видно.
            val ghost = Color(0xFF2A2438)
            ClockText("00:00", d.heroSize, d.hero, d.heroWeight, ghost, ghost, spacing = d.heroSpacing)
            Txt("Нет включённых будильников", 16.sp, C.Text2)
            Txt("Включи будильник ниже или добавь новый «+»", 13.sp, C.Text3)
        } else {
            NextDetails(next.first, next.second, reading, s, now)
        }
    }
}

@Composable
private fun NextDetails(alarm: Alarm, at: java.time.ZonedDateTime, reading: Reading?, s: AppSettings, now: Long) {
    val accent = LocalAccent.current
    val d = LocalDigits.current
    val nowZ = Fmt.zoned(now)
    run {
        val atMs = at.toInstant().toEpochMilli()
        val remaining = atMs - now
        ClockText(alarm.timeText, d.heroSize, d.hero, d.heroWeight, Color.White, accent, spacing = d.heroSpacing)
        Txt("${Fmt.dayWord(at, nowZ)} · ${Fmt.until(remaining)}", 16.sp, C.Text2, align = TextAlign.Center)
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
            Txt("ночь позади на ${(fraction * 100).toInt()}%", 12.sp, C.Text3)
        }
        if (Live.isAlertForMe(reading, s) && alarm.rule != AlertRule.ALWAYS) {
            val text = if (alarm.rule == AlertRule.SKIP) {
                "Сейчас тревога. Если не закончится к ${alarm.timeText} — этот будильник промолчит"
            } else {
                "Сейчас тревога. Если не закончится к ${alarm.timeText} — подожду отбоя"
            }
            Txt(text, 14.sp, C.RedText, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun WaitingHero(w: Waiting, s: AppSettings, now: Long) {
    val d = LocalDigits.current
    val delayMin = s.delayAfterClearMin
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt("ПОДЪЁМ ОТЛОЖЕН", 13.sp, C.RedText, FontWeight.SemiBold, letterSpacing = 3.sp)
        ClockText(w.time, d.heroSize, d.hero, d.heroWeight, Color(0xFF4A4260), Color(0xFF4A4260), spacing = d.heroSpacing, struck = C.Red)
        val text = if (w.clearSince > 0L) {
            "Отбой в ${Fmt.hm(w.clearSince)} — зазвоню в ${Fmt.hm(w.clearSince + delayMin * 60_000L)}"
        } else if (delayMin == 0 && s.economy) {
            "Будильник ждёт отбоя и зазвонит в течение минуты после него"
        } else if (delayMin == 0) {
            "Будильник ждёт отбоя и зазвонит сразу после него"
        } else {
            "Будильник ждёт отбоя и зазвонит через $delayMin мин после него"
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
                Txt("Можно поспать ещё", 12.sp, C.Text3)
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt("до ", 18.sp, C.Text, FontWeight.Medium)
                    ClockText(Fmt.hm(w.deadline), 30f * d.smallScale, d.small, d.smallWeight, C.Text, C.Text)
                }
                Txt("максимум ожидания — ${s.maxWaitHours} ч · осталось ${Fmt.duration(w.deadline - now)}", 12.sp, C.Text3)
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
        Toggle(a.enabled, onToggle)
    }
}

@Composable
private fun NewsTeaser(post: Post?, now: Long, onClick: () -> Unit) {
    CardBox(background = C.Surface2, border = C.Stroke, onClick = onClick) {
        Row(Modifier.fillMaxWidth()) {
            SectionLabel("ПОСЛЕДНЕЕ ПРО ТРЕВОГУ", Modifier.weight(1f))
            if (post != null) Txt(Fmt.ago(post.time, now), 12.sp, C.Text3)
        }
        Spacer(Modifier.height(4.dp))
        if (post == null) {
            Txt("Новости из Telegram-каналов появятся здесь", 14.sp, C.Text3)
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
                        Txt("твоё место", 11.sp, C.PurplePale, FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
