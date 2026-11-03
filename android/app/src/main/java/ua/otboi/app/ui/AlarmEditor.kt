package ua.otboi.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.otboi.app.alarm.Scheduler
import ua.otboi.app.data.Alarm
import ua.otboi.app.data.AlertRule
import ua.otboi.app.data.Days
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Sound
import ua.otboi.app.data.Store
import ua.otboi.app.i18n.T
import ua.otboi.app.alarm.Preview
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(initial: Alarm, isNew: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val d = LocalDigits.current
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.days) }
    var rule by remember { mutableStateOf(initial.rule) }
    var sound by remember { mutableStateOf(initial.sound) }
    var playing by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) { onDispose { Preview.stop() } }
    LaunchedEffect(playing) {
        if (playing) {
            delay(15_000L)
            Preview.stop()
            playing = false
        }
    }

    // Выбор из мелодий телефона (стандартное окно Android).
    val pickRingtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        sound = when {
            uri == null -> sound
            uri == Settings.System.DEFAULT_ALARM_ALERT_URI -> sound.copy(uri = "", name = "", file = false)
            else -> sound.copy(
                uri = uri.toString(),
                file = false,
                name = runCatching { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) }.getOrNull() ?: "",
            )
        }
    }
    // Свой файл: просим постоянный доступ, чтобы будильник мог его открыть и через месяц.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.substringBeforeLast('.') ?: ""
        sound = sound.copy(uri = uri.toString(), name = name, file = true)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = C.Sheet,
        contentColor = C.Text,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(40.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(C.Chip)
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Txt(if (isNew) T.newAlarm else T.editAlarm, 20.sp, C.Text, FontWeight.SemiBold)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wheel(24, hour) { hour = it }
                Text(
                    ":",
                    color = accent,
                    fontFamily = d.small,
                    fontWeight = d.smallWeight,
                    fontSize = (44f * d.smallScale).fixedSp(),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Wheel(60, minute) { minute = it }
            }

            val preview = Scheduler.nextTime(
                Alarm(0, hour, minute, days, true, rule),
                ZonedDateTime.now(),
            )
            if (preview != null) {
                Txt(
                    "${Fmt.dayWord(preview, ZonedDateTime.now())} · ${Fmt.until(preview.toInstant().toEpochMilli() - System.currentTimeMillis())}",
                    14.sp, C.Text2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(T.daysLabel)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (day in 1..7) {
                    val on = day in days
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (on) accent else C.Surface)
                            .border(1.dp, if (on) accent else C.Stroke, CircleShape)
                            .clickable { days = if (on) days - day else days + day },
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(Days.short(day), 13.sp, if (on) C.OnAccent else C.Text2, FontWeight.SemiBold)
                    }
                }
            }
            val presets = listOf(
                T.presetWeekdays to Days.weekdays,
                T.presetWeekend to Days.weekend,
                T.presetEveryDay to Days.all,
                T.presetOnce to emptySet<Int>(),
            )
            ChipRow(presets, presets.firstOrNull { it.second == days } ?: ("" to setOf(-1)), { it.first }) {
                days = it.second
            }

            SectionLabel(T.ifAlertLabel)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertRule.entries.forEach { r ->
                    RuleRow(r, r == rule) { rule = r }
                }
            }

            SectionLabel(T.soundLabel)
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (sound.vibrationOnly) Ic.vibrate else Ic.music, null,
                        tint = accent, modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Txt(sound.displayName, 15.sp, C.Text, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    if (!sound.vibrationOnly) {
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(C.PurpleDeep)
                                .clickable {
                                    if (playing) {
                                        Preview.stop()
                                        playing = false
                                    } else {
                                        playing = Preview.play(ctx, sound.uri)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (playing) Ic.stop else Ic.play, null, tint = C.PurplePale, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Txt(if (playing) T.stop else T.preview, 13.sp, C.PurplePale, FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundChoice(T.soundFromPhone, Ic.music, !sound.vibrationOnly && !sound.file, Modifier.weight(1f)) {
                    Preview.stop()
                    playing = false
                    val current = if (sound.uri.isBlank() || sound.vibrationOnly || sound.file) {
                        Settings.System.DEFAULT_ALARM_ALERT_URI
                    } else {
                        Uri.parse(sound.uri)
                    }
                    val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, T.soundPickTitle)
                    runCatching { pickRingtone.launch(i) }
                }
                SoundChoice(T.soundFromFile, Ic.folder, sound.file && !sound.vibrationOnly, Modifier.weight(1f)) {
                    Preview.stop()
                    playing = false
                    runCatching { pickFile.launch(arrayOf("audio/*")) }
                }
                SoundChoice(T.soundVibrationOnly, Ic.vibrate, sound.vibrationOnly, Modifier.weight(1f)) {
                    Preview.stop()
                    playing = false
                    sound = if (sound.vibrationOnly) {
                        sound.copy(uri = "", name = "", file = false)
                    } else {
                        sound.copy(uri = Sound.NONE, name = "", vibrate = true, file = false)
                    }
                }
            }
            if (!sound.vibrationOnly) {
                ToggleRow(T.gradual, if (sound.gradual) T.gradualOn else T.gradualOff, sound.gradual) {
                    sound = sound.copy(gradual = it)
                }
                ToggleRow(T.vibration, null, sound.vibrate) { sound = sound.copy(vibrate = it) }
            }

            PrimaryButton(T.save) {
                Preview.stop()
                Store.upsertAlarm(initial.copy(hour = hour, minute = minute, days = days, rule = rule, sound = sound, enabled = true))
                Scheduler.rescheduleAll(ctx)
                onClose()
            }
            if (!isNew) {
                Txt(
                    T.deleteAlarm,
                    15.sp, C.RedText, FontWeight.Medium,
                    align = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            Store.deleteAlarm(initial.id)
                            Scheduler.rescheduleAll(ctx)
                            onClose()
                        }
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun SoundChoice(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) C.Surface2 else C.Surface)
            .border(1.dp, if (selected) accent else C.StrokeDim, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) accent else C.Text3, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Txt(label, 12.sp, if (selected) C.Text else C.Text2, FontWeight.SemiBold, align = TextAlign.Center, maxLines = 2)
    }
}

/** Строка с переключателем: заголовок, подсказка, тумблер. */
@Composable
fun ToggleRow(title: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, 15.sp, C.Text, FontWeight.SemiBold)
                if (!hint.isNullOrBlank()) Txt(hint, 12.sp, C.Text3, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Toggle(on, onChange)
        }
    }
}

@Composable
private fun RuleRow(rule: AlertRule, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) C.Surface2 else C.Surface)
            .border(1.dp, if (selected) accent else C.StrokeDim, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) accent else C.Muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(rule.title, 15.sp, C.Text, FontWeight.SemiBold)
            Txt(rule.description, 12.sp, C.Text3, lineHeight = 16.sp)
        }
    }
}

/** Барабан с цифрами (бесконечная прокрутка, доводка к центру). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(count: Int, value: Int, onChange: (Int) -> Unit) {
    val d = LocalDigits.current
    val itemH = 64.dp
    val loops = 200
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (loops / 2) * count + value)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val centered by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }
    LaunchedEffect(centered) { onChange(centered % count) }

    Box(Modifier.width(112.dp).height(itemH * 3), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemH)
                .clip(RoundedCornerShape(18.dp))
                .background(C.Surface2)
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemH),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count * loops) { i ->
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        Fmt.two(i % count),
                        color = if (i == centered) C.Hero else C.Muted,
                        fontFamily = d.small,
                        fontWeight = d.smallWeight,
                        fontSize = (44f * d.smallScale).fixedSp(),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
