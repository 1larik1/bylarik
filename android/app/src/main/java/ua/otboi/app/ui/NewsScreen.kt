package ua.otboi.app.ui

import ua.otboi.app.i18n.T
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ua.otboi.app.data.Feed
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Store

/** Лента новостей из Telegram: по умолчанию только про тревогу. */
@Composable
fun NewsScreen() {
    val s by Store.settings.collectAsState()
    val posts by Feed.posts.collectAsState()
    val errors by Feed.errors.collectAsState()
    val loading by Feed.loading.collectAsState()
    val scope = rememberCoroutineScope()
    val now = rememberNow()

    LaunchedEffect(Unit) { Feed.refresh(maxAgeMs = 60_000L) }

    val shown = if (s.alertsOnly) posts.filter { it.isAlert } else posts

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(T.newsTitle, 26.sp, C.Text, FontWeight.Bold)
                val updated = if (Feed.loadedAt > 0L) " · " + T.updatedAgo(Fmt.ago(Feed.loadedAt, now)) else ""
                Txt(
                    (if (s.channels.isEmpty()) T.noChannels else s.channels.joinToString(", ") { "@$it" }) + updated,
                    13.sp, C.Text3, maxLines = 1,
                )
            }
            RoundIconButton(Ic.refresh, T.refresh, tint = if (loading) LocalAccent.current else C.Text2) {
                scope.launch { Feed.refresh() }
            }
        }
        Spacer(Modifier.height(14.dp))
        ChipRow(listOf(true, false), s.alertsOnly, { if (it) T.filterAlerts else T.filterAll }) { v ->
            Store.updateSettings { it.copy(alertsOnly = v) }
        }
        errors.forEach { (ch, e) ->
            Spacer(Modifier.height(8.dp))
            Txt("@$ch: $e", 12.sp, C.RedText)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown.take(80), key = { it.id }) { p -> PostCard(p, now) }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    CardBox {
                        Txt(
                            if (loading) T.loading else if (s.alertsOnly) T.emptyAlerts else T.emptyAll,
                            15.sp, C.Text2,
                        )
                        Txt(T.addChannelsHint, 13.sp, C.Text3)
                    }
                }
            }
        }
    }
}
