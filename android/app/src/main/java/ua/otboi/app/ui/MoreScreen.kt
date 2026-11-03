package ua.otboi.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ua.otboi.app.alarm.Scheduler
import ua.otboi.app.alarm.WakeService
import ua.otboi.app.data.Feed
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Store
import ua.otboi.app.i18n.T
import ua.otboi.app.net.BackupApi
import ua.otboi.app.net.Telegram

@Composable
fun MoreScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Store.settings.collectAsState()
    val log by Store.log.collectAsState()
    val accent = LocalAccent.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val perms = remember(tick, refresh) { Perms.items(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) Perms.open(ctx, "notif")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Txt(T.moreTitle, 26.sp, C.Text, FontWeight.Bold)

        // Готовность
        SectionLabel(T.secReady, Modifier.padding(top = 6.dp))
        val missing = perms.filter { !it.ok }
        if (missing.isEmpty()) {
            CardBox(background = C.MintBg, border = C.MintBg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(C.Mint), contentAlignment = Alignment.Center) {
                        Icon(Ic.check, T.done, tint = C.MintBg, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Txt(T.allSet, 14.sp, C.MintText, FontWeight.SemiBold)
                }
            }
        } else {
            missing.forEach { p ->
                CardBox(
                    onClick = {
                        if (p.key == "notif" && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Perms.open(ctx, p.key)
                        }
                    },
                    border = C.Stroke,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt(p.title, 15.sp, C.Text, FontWeight.SemiBold)
                            Txt(p.hint, 12.sp, C.Text3, lineHeight = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(accent).padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Txt(T.allow, 13.sp, C.OnAccent, FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Txt(T.vendorHint, 12.sp, C.Text3, lineHeight = 16.sp)
        OutlineButton(T.openAppSettings, height = 46.dp) { Perms.open(ctx, "app") }

        // Внешний вид
        SectionLabel(T.secAppearance, Modifier.padding(top = 10.dp))
        Txt(T.themeLabel, 14.sp, C.Text2)
        Palettes.all.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p -> ThemeCard(p, p.id == s.themeId, Modifier.weight(1f)) { Store.updateSettings { it.copy(themeId = p.id) } } }
            }
        }
        Txt(T.digitsLabel, 14.sp, C.Text2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DigitStyle.entries.forEach { style ->
                val sel = style.id == s.digitStyle
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(C.Surface)
                        .border(if (sel) 2.dp else 1.dp, if (sel) accent else C.Stroke, shape)
                        .clickable { Store.updateSettings { it.copy(digitStyle = style.id) } }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ClockText("07:30", 30f * style.smallScale, style.small, style.smallWeight, C.Text, accent)
                    Spacer(Modifier.height(4.dp))
                    Txt(T.digitName(style.id), 12.sp, if (sel) C.Text else C.Text3, FontWeight.SemiBold)
                }
            }
        }

        // Если тревога
        SectionLabel(T.secAlert, Modifier.padding(top = 10.dp))
        Txt(T.whenAfterClear, 14.sp, C.Text2)
        ChipRow(listOf(0, 5, 10, 15, 30), s.delayAfterClearMin, { if (it == 0) T.immediately else T.afterMin(it) }) { v ->
            Store.updateSettings { it.copy(delayAfterClearMin = v) }
        }
        Txt(T.maxWait, 14.sp, C.Text2)
        ChipRow(listOf(1, 2, 3, 4, 6, 8), s.maxWaitHours, { T.hours(it) }) { v ->
            Store.updateSettings { it.copy(maxWaitHours = v) }
        }
        Txt(T.maxWaitHint, 12.sp, C.Text3, lineHeight = 16.sp)
        if (s.districtId > 0) {
            ToggleRow(T.neighborsTitle, T.neighborsHint, s.neighborsCount) { v -> Store.updateSettings { it.copy(neighborsCount = v) } }
        }
        ToggleRow(T.economyTitle, if (s.economy) T.economyOn else T.economyOff, s.economy) { v ->
            Store.updateSettings { it.copy(economy = v) }
        }
        Txt(T.sourcesHint(BackupApi.NAME), 12.sp, C.Text3, lineHeight = 16.sp)

        // Будильник
        SectionLabel(T.secAlarm, Modifier.padding(top = 10.dp))
        Txt(T.snoozeFor, 14.sp, C.Text2)
        ChipRow(listOf(5, 10, 15), s.snoozeMin, { T.minutes(it) }) { v ->
            Store.updateSettings { it.copy(snoozeMin = v) }
        }

        // Погода
        SectionLabel(T.secWeather, Modifier.padding(top = 10.dp))
        ToggleRow(T.weatherToggle, T.weatherToggleHint, s.weather) { v -> Store.updateSettings { it.copy(weather = v) } }

        // Каналы
        SectionLabel(T.secChannels, Modifier.padding(top = 10.dp))
        ChannelsEditor(s.channels) { scope.launch { Feed.refresh() } }

        // Проверка
        SectionLabel(T.secTest, Modifier.padding(top = 10.dp))
        PrimaryButton(T.testButton) {
            val at = Scheduler.scheduleTest(ctx)
            Store.addLog(T.lTest(Fmt.hm(at)))
            Toast.makeText(ctx, T.testToast(Fmt.hm(at)), Toast.LENGTH_LONG).show()
        }
        Txt(T.testHint, 12.sp, C.Text3, lineHeight = 16.sp)

        VersionSection()

        // Журнал
        SectionLabel(T.secLog, Modifier.padding(top = 10.dp))
        if (log.isEmpty()) Txt(T.logEmpty, 13.sp, C.Text3)
        log.take(15).forEach { e ->
            Row {
                Txt(Fmt.dateTime(e.time), 12.sp, C.Text3, modifier = Modifier.width(92.dp))
                Txt(e.text, 13.sp, C.Text2, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }

        // Удаление
        Spacer(Modifier.height(10.dp))
        OutlineButton(T.deleteApp, color = C.RedText, height = 48.dp) { confirmDelete = true }
        Txt(T.deleteAppHint, 12.sp, C.Text3, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(8.dp))
        val version = remember {
            runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
        }
        Txt(T.footer(version), 12.sp, C.Muted, lineHeight = 16.sp)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = C.Sheet,
            title = { Txt(T.deleteConfirmTitle, 18.sp, C.Text, FontWeight.SemiBold) },
            text = { Txt(T.deleteConfirmText, 14.sp, C.Text2, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Scheduler.cancelAll(ctx)
                    runCatching { ctx.stopService(Intent(ctx, WakeService::class.java)) }
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Txt(T.delete, 15.sp, C.RedText, FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Txt(T.cancel, 15.sp, C.Text2, FontWeight.SemiBold) }
            },
        )
    }
}

/** Карточка темы с маленьким превью. */
@Composable
private fun ThemeCard(p: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val accent = Color(p.accent)
    Column(
        modifier
            .clip(shape)
            .background(C.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) LocalAccent.current else C.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(p.bg))
                .border(1.dp, Color(p.stroke), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Txt("07:30", 20.sp, Color(p.hero), FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopStart))
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(46.dp, 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(p.track))
            ) { Box(Modifier.fillMaxHeight().width(28.dp).background(accent)) }
            Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(T.themeName(p.id), 13.sp, if (selected) C.Text else C.Text2, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (selected) Icon(Ic.check, null, tint = LocalAccent.current, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ChannelsEditor(channels: List<String>, onChanged: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    channels.forEach { ch ->
        CardBox(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("@$ch", 15.sp, C.Text, FontWeight.Medium, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable {
                        Store.updateSettings { it.copy(channels = it.channels - ch) }
                        onChanged()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.trash, T.deleteChannel(ch), tint = C.Text3, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputField(input, {
            input = it
            error = ""
        }, T.channelPlaceholder, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        val accent = LocalAccent.current
        Box(
            Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(accent)
                .clickable {
                    val clean = Telegram.cleanChannel(input)
                    error = when {
                        Telegram.isPrivateLink(input) -> T.errPrivate
                        clean.length < 4 -> T.errNotChannel
                        channels.any { it.equals(clean, ignoreCase = true) } -> T.errExists
                        else -> ""
                    }
                    if (error.isEmpty()) {
                        Store.updateSettings { it.copy(channels = it.channels + clean) }
                        input = ""
                        onChanged()
                    }
                }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Txt(T.add, 14.sp, C.OnAccent, FontWeight.SemiBold)
        }
    }
    if (error.isNotEmpty()) Txt(error, 12.sp, C.RedText)
    Txt(T.channelsHint, 12.sp, C.Text3, lineHeight = 16.sp)
}
