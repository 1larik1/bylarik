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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
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
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Txt("ПОРА ВСТАВАТЬ", 14.sp, C.PurpleLight, FontWeight.SemiBold, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        ClockText(Fmt.hm(now), d.heroSize, d.hero, d.heroWeight, Color.White, accent, spacing = d.heroSpacing)
        Spacer(Modifier.height(12.dp))
        Txt("Будильник ${info.label}", 18.sp, C.Text, FontWeight.SemiBold, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Txt(info.reason, 16.sp, C.Text2, align = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.weight(1.3f))
        PrimaryButton("Выключить", height = 64.dp, onClick = onDismiss)
        Spacer(Modifier.height(12.dp))
        OutlineButton("Отложить на $snoozeMin мин", height = 56.dp, onClick = onSnooze)
    }
}
