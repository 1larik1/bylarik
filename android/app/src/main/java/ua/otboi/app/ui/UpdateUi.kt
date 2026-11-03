package ua.otboi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ua.otboi.app.OtboiApp
import ua.otboi.app.i18n.T
import ua.otboi.app.update.UpdateInfo
import ua.otboi.app.update.Updater

/** Скачать и поставить. В фоне приложения, чтобы закрытое окно не оборвало скачивание. */
private fun startInstall(ctx: android.content.Context, info: UpdateInfo) {
    val app = ctx.applicationContext
    OtboiApp.scope.launch { Updater.install(app, info) }
}

/** Полоска и подписи «скачиваю / ставлю / нужна галочка / ошибка» с кнопкой. */
@Composable
private fun UpdateActions(state: Updater.State, info: UpdateInfo) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    when (state) {
        is Updater.State.Downloading -> {
            Txt(T.downloading(state.percent), 13.sp, C.Text2)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Track)) {
                Box(
                    Modifier
                        .fillMaxWidth(state.percent.coerceIn(0, 100) / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent)
                )
            }
        }
        is Updater.State.Installing -> Txt(T.openingInstaller, 13.sp, C.Text2)
        is Updater.State.NeedsPermission -> {
            Txt(T.needInstallPermission, 13.sp, C.Text2, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(T.updateButton, height = 50.dp) { startInstall(ctx, info) }
        }
        is Updater.State.Failed -> {
            Txt(state.message, 13.sp, C.RedText, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(T.tryAgain, height = 50.dp) { startInstall(ctx, info) }
        }
        else -> PrimaryButton(T.updateButton, height = 50.dp) { startInstall(ctx, info) }
    }
}

/** Карточка новой версии в «Ещё» → «Версия». */
@Composable
fun UpdateCard(state: Updater.State) {
    val accent = LocalAccent.current
    val info = Updater.infoOf(state) ?: return
    CardBox(background = C.Surface2, border = accent.copy(alpha = 0.55f), radius = 22.dp) {
        Txt(T.updateLabel, 12.sp, C.PurpleLight, FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(4.dp))
        Txt(T.updateAvailable(info.versionName), 16.sp, C.Text, FontWeight.SemiBold)
        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Txt(info.notes, 13.sp, C.Text2, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        UpdateActions(state, info)
        if (state is Updater.State.Available) {
            Spacer(Modifier.height(6.dp))
            Txt(T.settingsKept, 12.sp, C.Text3)
        }
    }
}

/**
 * Окно «Вышла версия …» при входе в приложение. «Позже» — не спрашивать несколько часов;
 * обновиться можно и потом, в «Ещё».
 */
@Composable
fun UpdatePrompt(enabled: Boolean) {
    val ctx = LocalContext.current
    val state by Updater.state.collectAsState()
    val tick = rememberResumeTick()
    var open by rememberSaveable { mutableStateOf(false) }
    val info = Updater.infoOf(state)
    val available = state as? Updater.State.Available

    LaunchedEffect(available?.info?.versionCode, tick, enabled) {
        if (!open && enabled && available != null && Updater.shouldPrompt(ctx, available.info.versionCode)) open = true
    }
    // Вернулись из настроек с разрешением на установку — продолжаем сами.
    LaunchedEffect(tick) {
        val s = state
        if (open && s is Updater.State.NeedsPermission && Updater.canInstall(ctx)) startInstall(ctx, s.info)
    }

    if (!open || info == null) return
    val busy = state is Updater.State.Downloading || state is Updater.State.Installing
    val later = {
        if (!busy) {
            Updater.postpone(ctx, info.versionCode)
            open = false
        }
    }
    Dialog(onDismissRequest = later, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val accent = LocalAccent.current
        Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            CardBox(
                background = C.Sheet,
                border = accent.copy(alpha = 0.55f),
                radius = 28.dp,
                padding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp),
            ) {
                SectionLabel(T.updateLabel)
                Spacer(Modifier.height(6.dp))
                Txt(T.updateAvailable(info.versionName), 22.sp, C.Text, FontWeight.SemiBold)
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        Txt(info.notes, 15.sp, C.Text2, lineHeight = 21.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Txt(T.settingsKept, 13.sp, C.Text3, lineHeight = 18.sp)
                Spacer(Modifier.height(18.dp))
                UpdateActions(state, info)
                if (!busy) {
                    Txt(
                        T.updateLater,
                        15.sp, C.Text2, FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = later)
                            .padding(vertical = 13.dp),
                        align = TextAlign.Center,
                    )
                    Txt(T.updateLaterHint, 12.sp, C.Text3, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/** Раздел «Версия» в «Ещё»: номер версии и ручная проверка. */
@Composable
fun VersionSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val name = remember { Updater.currentVersionName(ctx) }
    SectionLabel(T.secVersion, Modifier.padding(top = 10.dp))
    Txt("bylarik $name", 15.sp, C.Text, FontWeight.SemiBold)
    val status = when (val s = state) {
        is Updater.State.Checking -> T.checking
        is Updater.State.UpToDate -> T.upToDate
        is Updater.State.Failed -> if (s.info == null) s.message else ""
        else -> ""
    }
    if (status.isNotBlank()) Txt(status, 13.sp, if (state is Updater.State.Failed) C.RedText else C.Text3, lineHeight = 18.sp)
    UpdateCard(state)
    if (state !is Updater.State.Downloading && state !is Updater.State.Installing) {
        OutlineButton(T.checkUpdates, height = 48.dp) { scope.launch { Updater.check(ctx, force = true) } }
    }
}
