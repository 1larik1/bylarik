package ua.otboi.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.otboi.app.alarm.Scheduler
import ua.otboi.app.data.Alarm
import ua.otboi.app.data.AlertRule
import ua.otboi.app.data.Days
import ua.otboi.app.data.Feed
import ua.otboi.app.data.Live
import ua.otboi.app.data.RegionRepo
import ua.otboi.app.data.Store
import ua.otboi.app.i18n.T
import ua.otboi.app.update.Updater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ua.otboi.app.i18n.L.init(this)
        applyBars(Palettes.byId(Store.settings.value.themeId).light)
        setContent {
            val s by Store.settings.collectAsState()
            val light = Palettes.byId(s.themeId).light
            LaunchedEffect(light) { applyBars(light) }
            OtboiTheme { AppRoot() }
        }
        // Пока приложение открыто: тревога — раз в минуту, новости — раз в 2 минуты.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    RegionRepo.ensure(applicationContext)
                    while (isActive) {
                        Live.check(applicationContext)
                        delay(60_000L)
                    }
                }
                launch {
                    while (isActive) {
                        Feed.refresh()
                        delay(120_000L)
                    }
                }
                launch { Updater.check(applicationContext) }
            }
        }
    }

    /** Цвет значков в строке состояния под тему (тёмные значки на светлой теме). */
    private fun applyBars(light: Boolean) {
        if (light) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Scheduler.rescheduleAll(this)
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(if (Store.settings.value.stateId <= 0) 2 else 0) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var editingNew by remember { mutableStateOf(false) }
    val update by Updater.state.collectAsState()

    LaunchedEffect(s.stateId, s.districtId, s.communityId) {
        if (s.stateId > 0) {
            Live.check(ctx.applicationContext)
            Feed.refresh()
        }
    }

    BackHandler(enabled = tab != 0) { tab = 0 }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            when (tab) {
                0 -> AlarmsScreen(
                    onEdit = {
                        editing = it
                        editingNew = false
                    },
                    onAdd = {
                        editing = Alarm(Store.newAlarmId(), 7, 0, Days.weekdays, true, AlertRule.AFTER_CLEAR)
                        editingNew = true
                    },
                    onNews = { tab = 1 },
                    onRegion = { tab = 2 },
                    onMore = { tab = 3 },
                )
                1 -> NewsScreen()
                2 -> RegionScreen(onDone = { tab = 0 })
                else -> MoreScreen()
            }
        }
        BottomNav(tab, badgeOnMore = Updater.infoOf(update) != null) { tab = it }
    }

    editing?.let { a ->
        AlarmEditor(a, editingNew) { editing = null }
    }

    // Новая версия — предлагаем обновиться сразу при входе (но не посреди редактирования будильника).
    UpdatePrompt(enabled = editing == null)
}

@Composable
fun BottomNav(tab: Int, badgeOnMore: Boolean = false, onTab: (Int) -> Unit) {
    val items = listOf(
        Ic.clock to T.tabAlarms,
        Ic.rss to T.tabNews,
        Ic.map to T.tabPlace,
        Ic.dots to T.tabMore,
    )
    Column(Modifier.fillMaxWidth().background(C.Nav)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.NavStroke))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(74.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (icon, label) ->
                val sel = i == tab
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(58.dp, 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (sel) C.PurpleDeep else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, label, tint = if (sel) C.PurplePale else C.Text3, modifier = Modifier.size(20.dp))
                        if (i == 3 && badgeOnMore) {
                            // Точка «есть обновление» на вкладке «Ещё».
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 3.dp, end = 13.dp)
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(LocalAccent.current)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Txt(
                        label, 12.sp,
                        if (sel) C.Text else C.Text3,
                        if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
