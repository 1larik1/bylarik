package ua.otboi.app.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ua.otboi.app.alarm.RingInfo
import ua.otboi.app.alarm.RingState
import ua.otboi.app.alarm.WakeService
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Store
import ua.otboi.app.data.Wear
import ua.otboi.app.i18n.T
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip

/** Экран звонящего будильника — поверх блокировки. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ua.otboi.app.i18n.L.init(this)
        val light = Palettes.byId(Store.settings.value.themeId).light
        enableEdgeToEdge(
            statusBarStyle = if (light) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (light) SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE)
            else SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        setContent {
            OtboiTheme {
                val info by RingState.current.collectAsState()
                LaunchedEffect(info) {
                    if (info == null) finish()
                }
                info?.let { i ->
                    RingScreen(
                        i,
                        Store.settings.value.snoozeMin,
                        onDismiss = {
                            WakeService.start(this, WakeService.ACTION_DISMISS)
                            finish()
                        },
                        onSnooze = {
                            WakeService.start(this, WakeService.ACTION_SNOOZE)
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingScreen(info: RingInfo, snoozeMin: Int, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val accent = LocalAccent.current
    val d = LocalDigits.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Txt(T.timeToGetUp, 14.sp, C.PurpleLight, FontWeight.SemiBold, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        ClockText(Fmt.hm(now), d.heroSize, d.hero, d.heroWeight, C.Hero, accent, spacing = d.heroSpacing)
        Spacer(Modifier.height(12.dp))
        Txt(T.alarmLabel(info.label), 18.sp, C.Text, FontWeight.SemiBold, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Txt(info.reason, 16.sp, C.Text2, align = TextAlign.Center, lineHeight = 22.sp)
        val s by Store.settings.collectAsState()
        val w by Store.weather.collectAsState()
        val wd = w
        if (s.weather && wd != null && wd.stateId == s.stateId) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.Surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.weather(wd.code), null, tint = accent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(
                        "${Fmt.temp(wd.temp)} · ${T.weatherCode(wd.code)} · ${T.feels(Fmt.temp(wd.feels))}",
                        14.sp, C.Text, FontWeight.SemiBold, maxLines = 2,
                    )
                    Txt(Wear.advice(wd, Fmt.zoned(now).hour), 14.sp, C.Text2, lineHeight = 19.sp)
                }
            }
        }
        Spacer(Modifier.weight(1.3f))
        PrimaryButton(T.dismiss, height = 64.dp, onClick = onDismiss)
        Spacer(Modifier.height(12.dp))
        OutlineButton(T.snoozeBtn(snoozeMin), height = 56.dp, onClick = onSnooze)
    }
}
