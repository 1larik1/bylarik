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
import ua.otboi.app.data.Store
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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Txt(if (isNew) "Новый будильник" else "Будильник", 20.sp, C.Text, FontWeight.SemiBold)

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

            SectionLabel("ДНИ")
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
                        Txt(Days.short[day - 1], 13.sp, if (on) Color.White else C.Text2, FontWeight.SemiBold)
                    }
                }
            }
            val presets = listOf(
                "Будни" to Days.weekdays,
                "Выходные" to Days.weekend,
                "Каждый день" to Days.all,
                "Один раз" to emptySet<Int>(),
            )
            ChipRow(presets, presets.firstOrNull { it.second == days } ?: ("" to setOf(-1)), { it.first }) {
                days = it.second
            }

            SectionLabel("ЕСЛИ ТРЕВОГА")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertRule.entries.forEach { r ->
                    RuleRow(r, r == rule) { rule = r }
                }
            }

            PrimaryButton("Сохранить") {
                Store.upsertAlarm(initial.copy(hour = hour, minute = minute, days = days, rule = rule, enabled = true))
                Scheduler.rescheduleAll(ctx)
                onClose()
            }
            if (!isNew) {
                Txt(
                    "Удалить будильник",
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
                        color = if (i == centered) Color.White else C.Muted,
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
