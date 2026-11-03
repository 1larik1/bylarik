package ua.otboi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ua.otboi.app.update.Updater

/** Карточка «Доступна новая версия» — на главном экране и в «Ещё». */
@Composable
fun UpdateCard(state: Updater.State, compact: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    val info = when (state) {
        is Updater.State.Available -> state.info
        is Updater.State.Downloading -> state.info
        is Updater.State.Installing -> state.info
        is Updater.State.NeedsPermission -> state.info
        is Updater.State.Failed -> state.info
        else -> null
    } ?: return

    CardBox(background = C.Surface2, border = accent.copy(alpha = 0.55f), radius = 22.dp) {
        Txt("ОБНОВЛЕНИЕ", 12.sp, C.PurpleLight, FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(4.dp))
        Txt("Доступна версия ${info.versionName}", 16.sp, C.Text, FontWeight.SemiBold)
        if (!compact && info.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Txt(info.notes, 13.sp, C.Text2, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            is Updater.State.Downloading -> {
                Txt("Скачиваю… ${state.percent}%", 13.sp, C.Text2)
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
            is Updater.State.Installing -> Txt("Открываю установщик Android…", 13.sp, C.Text2)
            is Updater.State.NeedsPermission -> {
                Txt(
                    "Разреши «bylarik» устанавливать приложения (откроются настройки), потом вернись и нажми ещё раз.",
                    13.sp, C.Text2, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Обновить", height = 48.dp) { scope.launch { Updater.install(ctx, info) } }
            }
            is Updater.State.Failed -> {
                Txt(state.message, 13.sp, C.RedText, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Попробовать ещё раз", height = 48.dp) { scope.launch { Updater.install(ctx, info) } }
            }
            else -> {
                PrimaryButton("Обновить", height = 48.dp) { scope.launch { Updater.install(ctx, info) } }
                if (!compact) {
                    Spacer(Modifier.height(6.dp))
                    Txt("Настройки и будильники сохранятся.", 12.sp, C.Text3)
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
    SectionLabel("ВЕРСИЯ", Modifier.padding(top = 10.dp))
    Txt("bylarik $name", 15.sp, C.Text, FontWeight.SemiBold)
    val status = when (val s = state) {
        is Updater.State.Checking -> "Проверяю…"
        is Updater.State.UpToDate -> "Это последняя версия"
        is Updater.State.Failed -> if (s.info == null) s.message else ""
        else -> ""
    }
    if (status.isNotBlank()) Txt(status, 13.sp, if (state is Updater.State.Failed) C.RedText else C.Text3, lineHeight = 18.sp)
    UpdateCard(state, compact = false)
    if (state !is Updater.State.Downloading && state !is Updater.State.Installing) {
        OutlineButton("Проверить обновления", height = 48.dp) { scope.launch { Updater.check(ctx, force = true) } }
    }
    Txt("Обновления берутся из github.com/${Updater.REPO}", 12.sp, C.Text3)
}
