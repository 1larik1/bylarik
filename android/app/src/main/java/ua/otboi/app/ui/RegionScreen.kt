package ua.otboi.app.ui

import ua.otboi.app.i18n.T
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.launch
import ua.otboi.app.data.Oblasts
import ua.otboi.app.data.Places
import ua.otboi.app.data.RegionRepo
import ua.otboi.app.data.Store

/** Выбор места: область → (район) → (громада). */
@Composable
fun RegionScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Store.settings.collectAsState()
    val reading by Store.reading.collectAsState()
    val tree by RegionRepo.tree.collectAsState()
    val treeError by RegionRepo.error.collectAsState()
    val now = rememberNow()
    var level by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { RegionRepo.ensure(ctx) }
    BackHandler(enabled = level > 0) {
        level -= 1
        query = ""
    }

    val state = tree.firstOrNull { it.id == s.stateId }
    val district = state?.children?.firstOrNull { it.id == s.districtId }
    val q = query.trim().lowercase()
    fun matches(name: String) = q.isEmpty() || name.lowercase().contains(q)

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (level > 0) {
                RoundIconButton(Ic.back, T.back) {
                    level -= 1
                    query = ""
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                val title = when (level) {
                    0 -> T.yourOblast
                    1 -> T.regionDistrict
                    else -> T.regionCommunity
                }
                val sub = when (level) {
                    0 -> T.regionOblastSub
                    1 -> Oblasts.display(s.stateId)
                    else -> district?.let { Oblasts.short(it.name) } ?: ""
                }
                Txt(title, 26.sp, C.Text, FontWeight.Bold)
                Txt(sub, 14.sp, C.Text2, maxLines = 1)
            }
        }

        if (level == 0 && s.stateId > 0) {
            Spacer(Modifier.height(14.dp))
            CardBox(background = C.Surface2, border = C.Stroke) {
                Txt(T.regionNow, 12.sp, C.PurpleLight, FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(4.dp))
                Txt(ua.otboi.app.data.Places.fullName(s), 16.sp, C.Text, FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                StatusPill(pillFor(reading, s, now))
            }
        }

        Spacer(Modifier.height(14.dp))
        InputField(
            query, { query = it },
            when (level) {
                0 -> T.searchOblast
                1 -> T.searchDistrict
                else -> T.searchCommunity
            },
            Modifier.fillMaxWidth(),
            leading = Ic.search,
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (level) {
                0 -> {
                    items(Oblasts.all.filter { matches(it.name) }, key = { it.id }) { o ->
                        PlaceRow(Oblasts.display(o.id), null, o.id == s.stateId, arrow = true) {
                            if (o.id != s.stateId) Places.select(o.id, 0, 0)
                            query = ""
                            level = 1
                        }
                    }
                }
                1 -> {
                    item(key = "all") {
                        PlaceRow(T.wholeOblast, T.wholeOblastHint, s.districtId == 0, arrow = false) {
                            if (s.districtId != 0) Places.select(s.stateId, 0, 0)
                            onDone()
                        }
                    }
                    if (state == null) {
                        item(key = "load") {
                            LoadingRow(treeError) { scope.launch { RegionRepo.ensure(ctx, forceNetwork = true) } }
                        }
                    } else {
                        items(state.children.filter { matches(it.name) }.sortedBy { it.name }, key = { it.id }) { dn ->
                            PlaceRow(Oblasts.short(dn.name), null, dn.id == s.districtId, arrow = dn.children.isNotEmpty()) {
                                if (dn.id != s.districtId) Places.select(s.stateId, dn.id, 0)
                                query = ""
                                if (dn.children.isNotEmpty()) level = 2 else onDone()
                            }
                        }
                    }
                }
                else -> {
                    item(key = "all") {
                        PlaceRow(T.wholeDistrict, T.wholeDistrictHint, s.communityId == 0, arrow = false) {
                            if (s.communityId != 0) Places.select(s.stateId, s.districtId, 0)
                            onDone()
                        }
                    }
                    val list = district?.children?.filter { matches(it.name) }?.sortedBy { it.name } ?: emptyList()
                    items(list, key = { it.id }) { c ->
                        PlaceRow(Oblasts.short(c.name), null, c.id == s.communityId, arrow = false) {
                            Places.select(s.stateId, s.districtId, c.id)
                            onDone()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(title: String, hint: String?, selected: Boolean, arrow: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) C.Surface2 else C.Surface)
            .border(1.dp, if (selected) accent else C.StrokeDim, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, 16.sp, C.Text, if (selected) FontWeight.SemiBold else FontWeight.Medium)
            if (hint != null) Txt(hint, 12.sp, C.Text3)
        }
        if (selected) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.check, null, tint = C.OnAccent, modifier = Modifier.size(16.dp))
            }
        } else if (arrow) {
            Icon(Ic.chevron, null, tint = C.Text3, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LoadingRow(error: String, onRetry: () -> Unit) {
    CardBox {
        if (error.isBlank()) {
            Txt(T.loadingDistricts, 14.sp, C.Text2)
        } else {
            Txt(T.loadFailed(error), 14.sp, C.RedText)
            Spacer(Modifier.height(10.dp))
            OutlineButton(T.retry, height = 44.dp, onClick = onRetry)
        }
    }
}
