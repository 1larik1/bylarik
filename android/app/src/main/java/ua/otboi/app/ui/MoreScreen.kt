package ua.otboi.app.ui

import android.Manifest
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
import androidx.compose.material3.Icon
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
import ua.otboi.app.data.Feed
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Store
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
        Txt("Ещё", 26.sp, C.Text, FontWeight.Bold)

        // Готовность
        SectionLabel("ГОТОВНОСТЬ", Modifier.padding(top = 6.dp))
        perms.forEach { p ->
            CardBox(
                onClick = if (p.ok) null else ({
                    if (p.key == "notif" && Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Perms.open(ctx, p.key)
                    }
                }),
                border = if (p.ok) C.StrokeDim else C.Stroke,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(p.title, 15.sp, C.Text, FontWeight.SemiBold)
                        Txt(p.hint, 12.sp, C.Text3, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (p.ok) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(C.MintBg), contentAlignment = Alignment.Center) {
                            Icon(Ic.check, "Готово", tint = C.Mint, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(accent).padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Txt("Разрешить", 13.sp, Color.White, FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Txt(
            "На Xiaomi, Samsung, Huawei и Honor ещё включи «Автозапуск» и сними ограничения фоновой работы для «Отбоя» в настройках приложения.",
            12.sp, C.Text3, lineHeight = 16.sp,
        )
        OutlineButton("Открыть настройки приложения", height = 46.dp) { Perms.open(ctx, "app") }

        // Цифры и цвет
        SectionLabel("ЦИФРЫ", Modifier.padding(top = 10.dp))
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
                    Txt(style.title, 12.sp, if (sel) C.Text else C.Text3, FontWeight.SemiBold)
                }
            }
        }
        SectionLabel("ЦВЕТ", Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AccentOptions.forEach { argb ->
                val sel = argb == s.accent
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .clickable { Store.updateSettings { it.copy(accent = argb) } }
                )
            }
        }

        // Тревога
        SectionLabel("ЕСЛИ ТРЕВОГА", Modifier.padding(top = 10.dp))
        Txt("Когда звонить после отбоя", 14.sp, C.Text2)
        ChipRow(listOf(0, 5, 10, 15, 30), s.delayAfterClearMin, { if (it == 0) "сразу" else "через $it мин" }) { v ->
            Store.updateSettings { it.copy(delayAfterClearMin = v) }
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Экономия заряда", 15.sp, C.Text, FontWeight.SemiBold)
                    Txt(
                        if (s.economy) {
                            "пока жду отбоя, проверяю тревогу раз в минуту — звонок может опоздать до минуты"
                        } else {
                            "сейчас проверяю тревогу каждые 20 с — звоню почти сразу после отбоя"
                        },
                        12.sp, C.Text3, lineHeight = 16.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Toggle(s.economy) { v -> Store.updateSettings { it.copy(economy = v) } }
            }
        }
        Txt("Ждать отбоя не дольше", 14.sp, C.Text2)
        ChipRow(listOf(1, 2, 3, 4, 6, 8), s.maxWaitHours, { "$it ч" }) { v ->
            Store.updateSettings { it.copy(maxWaitHours = v) }
        }
        Txt("Если тревога не закончится — разбужу всё равно, когда выйдет это время.", 12.sp, C.Text3, lineHeight = 16.sp)
        Txt(
            "Тревогу смотрю по siren.pp.ua. Если он не отвечает — по запасному ${ua.otboi.app.net.BackupApi.NAME} (по районам). " +
                "Если не отвечают оба — бужу на всякий случай.",
            12.sp, C.Text3, lineHeight = 16.sp,
        )
        if (s.districtId > 0) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("Тревога в соседних районах", 15.sp, C.Text, FontWeight.SemiBold)
                        Txt("тоже считается тревогой для будильника", 12.sp, C.Text3)
                    }
                    Spacer(Modifier.width(10.dp))
                    Toggle(s.neighborsCount) { v -> Store.updateSettings { it.copy(neighborsCount = v) } }
                }
            }
        }
        Txt("Отложить на", 14.sp, C.Text2)
        ChipRow(listOf(5, 10, 15), s.snoozeMin, { "$it мин" }) { v ->
            Store.updateSettings { it.copy(snoozeMin = v) }
        }

        // Каналы
        SectionLabel("TELEGRAM-КАНАЛЫ", Modifier.padding(top = 10.dp))
        ChannelsEditor(s.channels) { scope.launch { Feed.refresh() } }

        // Проверка
        SectionLabel("ПРОВЕРКА", Modifier.padding(top = 10.dp))
        PrimaryButton("Тестовый будильник через 1 минуту") {
            val at = Scheduler.scheduleTest(ctx)
            Store.addLog("Тест: будильник на ${Fmt.hm(at)}")
            Toast.makeText(ctx, "Сработает в ${Fmt.hm(at)} — можно заблокировать экран", Toast.LENGTH_LONG).show()
        }
        Txt(
            "Тест ведёт себя как настоящий будильник с правилом «После отбоя»: если сейчас тревога — не зазвонит и будет ждать отбоя, если тревоги нет — зазвонит.",
            12.sp, C.Text3, lineHeight = 16.sp,
        )

        // Журнал
        VersionSection()

        SectionLabel("ЖУРНАЛ", Modifier.padding(top = 10.dp))
        if (log.isEmpty()) Txt("Пока пусто", 13.sp, C.Text3)
        log.take(20).forEach { e ->
            Row {
                Txt(Fmt.dateTime(e.time), 12.sp, C.Text3, modifier = Modifier.width(92.dp))
                Txt(e.text, 13.sp, C.Text2, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(8.dp))
        val version = remember {
            runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
        }
        Txt(
            "Отбой $version · будить или нет — решают официальные данные о тревоге (siren.pp.ua). Telegram — только для информации.",
            12.sp, C.Muted, lineHeight = 16.sp,
        )
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
                    Icon(Ic.trash, "Удалить @$ch", tint = C.Text3, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputField(input, {
            input = it
            error = ""
        }, "@канал или ссылка t.me/…", Modifier.weight(1f))
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
                        Telegram.isPrivateLink(input) -> "Закрытые каналы (ссылки с «+») читать нельзя — только публичные"
                        clean.length < 4 -> "Не похоже на имя канала"
                        channels.any { it.equals(clean, ignoreCase = true) } -> "Этот канал уже есть"
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
            Txt("Добавить", 14.sp, Color.White, FontWeight.SemiBold)
        }
    }
    if (error.isNotEmpty()) Txt(error, 12.sp, C.RedText)
    Txt("Только публичные каналы. Ошибки чтения видно на вкладке «Новости».", 12.sp, C.Text3, lineHeight = 16.sp)
}
