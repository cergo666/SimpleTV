package io.mediacast.simpletv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.Programme
import io.mediacast.simpletv.data.StreamResolver

@Composable
fun PlaylistOverlay(
    vm: MainViewModel,
    wide: Boolean,
    now: Long,
    group: String,
    onGroup: (String) -> Unit,
    focusEpg: Boolean,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    embedded: Boolean = false,
    onHome: (() -> Unit)? = null,
    videoPreview: (@Composable () -> Unit)? = null,
) {
    val all by vm.channels.collectAsState()
    val fav by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val epgVersion by vm.epgVersion.collectAsState()
    val playback by vm.controller.state.collectAsState()
    val playing = playback.channel
    var query by rememberSaveable { mutableStateOf("") }
    var guideSelection by remember { mutableStateOf<Pair<Channel, Programme>?>(null) }
    val groups = remember(all, fav, recent) {
        val seen = all.count { it.key in recent }
        listOf(Group(MainViewModel.NOW, -1, true)) +
            (if (seen > 0) listOf(Group(MainViewModel.RECENT, seen, true)) else emptyList()) +
            vm.groups(all, fav)
    }
    val effectiveGroup = if (groups.any { it.name == group }) group else MainViewModel.ALL
    val matches by produceState(emptyMap<String, Programme>(), all, query, epgVersion, now / 60_000, effectiveGroup) {
        value = emptyMap()
        if (query.isNotBlank() || effectiveGroup == MainViewModel.NOW) {
            if (query.isNotBlank()) delay(200)
            value = vm.guideMatches(all, query.trim(), now)
        }
    }
    val list = remember(all, fav, recent, effectiveGroup, query, matches) {
        when {
            query.isNotBlank() -> all.filter { it.name.contains(query.trim(), ignoreCase = true) || it.key in matches }
            effectiveGroup == MainViewModel.NOW -> all.filter { it.key in matches }
            effectiveGroup == MainViewModel.RECENT -> {
                val byKey = all.associateBy { it.key }
                recent.mapNotNull { byKey[it] }
            }
            else -> vm.channelsOf(effectiveGroup, all, fav)
        }
    }
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var previewKey by remember { mutableStateOf<String?>(null) }
    val focused = list.firstOrNull { it.key == focusedKey } ?: list.firstOrNull { it.key == playing?.key } ?: list.firstOrNull()

    val play: (Channel) -> Unit = { ch ->
        val match = matches[ch.key]?.takeIf { !wide && query.isNotBlank() }
        if (match != null) {
            guideSelection = ch to match
        } else if (videoPreview != null) {
            if (previewKey == ch.key && playing?.key == ch.key) {
                onClose()
            } else {
                previewKey = ch.key
                if (playing?.key != ch.key || playback.archive != null) vm.play(ch, list)
            }
        } else {
            vm.play(ch, list)
            onClose()
        }
    }

    guideSelection?.let { (ch, programme) ->
        BackHandler { guideSelection = null }
        Column(Modifier.fillMaxSize().background(C.Panel).padding(10.dp)) {
            FocusItem(onClick = { guideSelection = null }) { _ ->
                Text(stringResource(R.string.back_to_search), color = C.Text, modifier = Modifier.padding(10.dp))
            }
            EpgPanel(
                vm, ch, now, focusCurrent = false, selectedProgramme = programme,
                onPlayLive = { vm.play(ch, list); guideSelection = null; onClose() },
                onPlayArchive = { vm.playArchive(ch, it); guideSelection = null; onClose() },
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    if (wide) {
        Row(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(C.panelBrush)
                .padding(start = 24.dp, top = 20.dp, bottom = 20.dp, end = 24.dp)
        ) {
            GroupsColumn(groups, effectiveGroup, onGroup = { onGroup(it); query = "" }, onSettings = onSettings, onHome = onHome, now = now, modifier = Modifier.weight(0.24f).fillMaxHeight())
            Spacer(Modifier.width(12.dp))
            ChannelsColumn(
                vm, list, playing, fav, now, effectiveGroup, query, { query = it },
                matches = matches,
                focusCurrent = !focusEpg,
                onFocus = { focusedKey = it.key }, onPlay = play, restoreKey = focused?.key,
                modifier = Modifier.weight(0.40f).fillMaxHeight()
            )
            Spacer(Modifier.width(12.dp))
            val epgCh = focused ?: playing
            if (epgCh != null) {
                Column(Modifier.weight(0.36f).fillMaxHeight()) {
                    if (videoPreview != null) {
                        videoPreview()
                        Text(stringResource(R.string.browse_preview_hint), color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    EpgPanel(
                    vm, epgCh, now, focusCurrent = focusEpg,
                    selectedProgramme = matches[epgCh.key]?.takeIf { query.isNotBlank() },
                    onPlayLive = { play(epgCh) },
                    onPlayArchive = { p -> vm.playArchive(epgCh, p); onClose() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            } else Column(Modifier.weight(0.36f)) { videoPreview?.invoke() }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(C.Panel)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!embedded) Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = C.Text, modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(12.dp).size(24.dp))
                Text(stringResource(R.string.nav_channels), color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Icon(Icons.Default.Settings, stringResource(R.string.nav_settings), tint = C.Text2, modifier = Modifier.clip(CircleShape).clickable { onSettings() }.padding(12.dp).size(24.dp))
            }
            SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(groups, key = { it.name }) { g ->
                    val sel = g.name == effectiveGroup
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (sel) C.Accent else C.Card)
                            .clickable { onGroup(g.name); query = "" }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(if (g.count < 0) groupLabel(g.name) else "${groupLabel(g.name)}  ${g.count}", color = if (sel) C.OnAccent else C.Text2, fontSize = 13.sp) }
                }
            }
            ChannelsColumn(
                vm, list, playing, fav, now, effectiveGroup, query, { query = it },
                matches = matches,
                focusCurrent = false, showHeader = false,
                onFocus = {}, onPlay = play, modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun GroupsColumn(groups: List<Group>, selected: String, onGroup: (String) -> Unit, onSettings: () -> Unit, onHome: (() -> Unit)?, now: Long, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(C.accentBrush))
            Text("  ${stringResource(R.string.app_name)}", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(hhmm(now), color = C.Text2, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
        }
        if (onHome != null) FocusItem(Modifier.fillMaxWidth(), onClick = onHome) { _ ->
            Text(stringResource(R.string.browse_home), color = C.Text, modifier = Modifier.padding(12.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(groups, key = { it.name }) { g ->
                FocusItem(
                    Modifier.fillMaxWidth(),
                    selected = g.name == selected,
                    onFocus = { if (g.name != selected) onGroup(g.name) },
                    onClick = { onGroup(g.name) },
                ) { _ ->
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (g.name == MainViewModel.FAV) Icon(Icons.Default.Star, null, tint = C.Accent2, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        Text(
                            groupLabel(g.name), color = if (g.name == selected) C.Text else C.Text2,
                            fontWeight = if (g.special) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        if (g.count >= 0) Text("${g.count}", color = C.Text3, fontSize = 13.sp)
                    }
                }
            }
        }
        FocusItem(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onSettings) { _ ->
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = C.Text2, modifier = Modifier.size(18.dp))
                Text("  ${stringResource(R.string.nav_settings)}", color = C.Text2, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(C.Card).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = C.Text3, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(start = 8.dp)) {
            if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = C.Text3, fontSize = 15.sp)
            BasicTextField(
                query, onQuery, singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) Icon(
            Icons.Default.Close, stringResource(R.string.action_clear), tint = C.Text3,
            modifier = Modifier.clip(CircleShape).clickable { onQuery("") }.padding(12.dp).size(24.dp),
        )
    }
}

@Composable
private fun ChannelsColumn(
    vm: MainViewModel,
    list: List<Channel>,
    playing: Channel?,
    fav: Set<String>,
    now: Long,
    group: String,
    query: String,
    onQuery: (String) -> Unit,
    focusCurrent: Boolean,
    onFocus: (Channel) -> Unit,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    restoreKey: String? = null,
    matches: Map<String, Programme> = emptyMap(),
) {
    val state = rememberLazyListState()
    val target = list.indexOfFirst { it.key == (restoreKey ?: playing?.key) }.let { if (it < 0) 0 else it }
    val fr = remember { FocusRequester() }
    // При смене выборки возвращаемся к текущей позиции, а не к протухшему offset.
    LaunchedEffect(focusCurrent, query, group, list.size) {
        if (list.isNotEmpty()) state.scrollToItem((target - 4).coerceAtLeast(0))
        if (focusCurrent) {
            delay(80)
            runCatching { fr.requestFocus() }
        }
    }
    Column(modifier) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                Text(if (query.isBlank()) group else stringResource(R.string.search_title), color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("  ${list.size}", color = C.Text3, fontSize = 15.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            SearchField(query, onQuery, Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
        if (list.isEmpty()) {
            Text(
                if (query.isBlank()) stringResource(R.string.empty_group) else stringResource(R.string.nothing_found),
                color = C.Text3, modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(list, key = { _, ch -> ch.key }) { i, ch ->
                ChannelRow(
                    vm, ch, now,
                    playing = ch.key == playing?.key,
                    fav = ch.key in fav,
                    match = matches[ch.key]?.takeIf { query.isNotBlank() },
                    showNext = group == MainViewModel.NOW,
                    focusRequester = if (i == target) fr else null,
                    onFocus = { onFocus(ch) },
                    onClick = { onPlay(ch) },
                    onLong = { vm.toggleFavorite(ch) },
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    vm: MainViewModel,
    ch: Channel,
    now: Long,
    playing: Boolean,
    fav: Boolean,
    match: Programme?,
    showNext: Boolean,
    focusRequester: FocusRequester?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    val epgVersion by vm.epgVersion.collectAsState()
    val minute = now / 60_000
    val nn by produceState(emptyList<Programme>(), ch.key, epgVersion, minute) { value = vm.nowNext(ch) }
    val cur = nn.firstOrNull { it.isNow(now) }
    FocusItem(
        Modifier.fillMaxWidth(), selected = playing, focusRequester = focusRequester,
        onFocus = onFocus, onLongClick = onLong, onClick = onClick,
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ch.number.toString(), color = if (playing) C.Accent else C.Text3, fontSize = 13.sp,
                modifier = Modifier.width(38.dp), maxLines = 1
            )
            LogoImage(vm.logoOf(ch), ch.name, 34.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ch.name, color = C.Text, fontSize = 16.sp, fontWeight = if (playing || focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (fav) Icon(Icons.Default.Star, null, tint = C.Accent2, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                    if (ch.hasArchive) Text(" ◷", color = C.Blue, fontSize = 13.sp)
                    if (ch.variants.size > 1) Text(" ×${ch.variants.size}", color = C.Text3, fontSize = 12.sp)
                }
                if (match != null && match != cur) {
                    Text("${hhmm(match.start)}  ${match.title}", color = C.Accent2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (cur != null) {
                    Text(cur.title, color = C.Text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressLine(cur.progress(now), Modifier.fillMaxWidth().padding(top = 4.dp), height = 2.dp)
                    if (showNext) nn.firstOrNull { it.start > now }?.let { next ->
                        Text(stringResource(R.string.next_programme_short, hhmm(next.start), next.title), color = C.Text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(ch.group, color = C.Text3, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (playing) Text(stringResource(R.string.pill_live), color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Телепрограмма канала: прошедшие передачи с архивом можно открыть. */
@Composable
fun EpgPanel(
    vm: MainViewModel,
    ch: Channel,
    now: Long,
    focusCurrent: Boolean,
    onPlayLive: () -> Unit,
    onPlayArchive: (Programme) -> Unit,
    modifier: Modifier = Modifier,
    selectedProgramme: Programme? = null,
) {
    val epgVersion by vm.epgVersion.collectAsState()
    val archiveDays = ch.variants.maxOfOrNull { it.catchup?.days ?: 0.0 } ?: 0.0
    val from = now - (maxOf(archiveDays, 0.25) * 86_400_000).toLong()
    val day = now / 86_400_000
    val items by produceState<List<Programme>?>(null, ch.key, epgVersion, day, archiveDays) {
        delay(120) // не дёргаем базу при быстрой прокрутке каналов
        value = vm.schedule(ch, from, now + 3 * 86_400_000L)
    }
    val state = rememberLazyListState()
    val fr = remember { FocusRequester() }
    val list = items ?: emptyList()
    var focusedStart by rememberSaveable(ch.key) { mutableStateOf<Long?>(null) }
    val curIdx = list.indexOfFirst { it.isNow(now) }.let { if (it < 0) list.indexOfFirst { p -> p.start > now } else it }
    val target = (focusedStart ?: selectedProgramme?.start)?.let { start ->
        list.indexOfFirst { it.start == start }.takeIf { it >= 0 }
    } ?: curIdx
    LaunchedEffect(items, focusCurrent, selectedProgramme) {
        if (list.isNotEmpty() && target >= 0) {
            state.scrollToItem((target - 2).coerceAtLeast(0))
            if (focusCurrent) { delay(80); runCatching { fr.requestFocus() } }
        }
    }
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x99060A13)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoImage(vm.logoOf(ch), ch.name, 40.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(ch.name, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (archiveDays > 0) Pill(stringResource(R.string.archive_label, formatDays(archiveDays)), color = Color(0x334DA3FF), textColor = C.Blue)
                    if (ch.variants.size > 1) Text(pluralStringResource(R.plurals.variants_count, ch.variants.size, ch.variants.size), color = C.Text3, fontSize = 12.sp)
                }
            }
        }
        // Ручной выбор источника: OK по источнику — играть канал с него (выбор запоминается)
        val playback by vm.controller.state.collectAsState()
        val playingThis = playback.channel?.key == ch.key
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            items(ch.variants.size, key = { i -> ch.variants.getOrNull(i)?.url ?: i }) { i ->
                val v = ch.variants[i]
                val active = playingThis && playback.channel?.variants?.getOrNull(playback.variantIndex)?.url == v.url
                FocusItem(
                    Modifier.clip(RoundedCornerShape(50)).background(if (active) C.Accent else C.Card),
                    shape = RoundedCornerShape(50),
                    onClick = { vm.selectVariant(ch, i) },
                ) { _ ->
                    Text(
                        "${i + 1}. ${v.sourceName}", color = if (active) Color.White else C.Text2, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val selected = list.firstOrNull { it.start == focusedStart }
            ?: selectedProgramme
            ?: list.getOrNull(curIdx)?.takeIf { it.isNow(now) }
        selected?.let { programme ->
            Text(programme.title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${hhmm(programme.start)} – ${hhmm(programme.stop)}", color = C.Text2, fontSize = 14.sp)
            programme.desc?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (curIdx >= 0) FocusItem(onClick = {
                focusedStart = list[curIdx].start
                scope.launch { state.scrollToItem(curIdx) }
            }) { _ -> Text(stringResource(R.string.action_now), color = C.Accent2, modifier = Modifier.padding(8.dp)) }
            if (selected != null && ch.variants.any {
                    StreamResolver.catchupUrl(it, selected.start, selected.stop, now) != null
                }) FocusItem(onClick = { onPlayArchive(selected) }) { _ ->
                Text(stringResource(R.string.action_from_start), color = C.Accent2, modifier = Modifier.padding(8.dp))
            }
            FocusItem(onClick = onPlayLive) { _ ->
                Text(stringResource(R.string.action_back_to_live), color = C.Accent2, modifier = Modifier.padding(8.dp))
            }
        }
        when {
            items == null -> Text(stringResource(R.string.loading_guide), color = C.Text3)
            list.isEmpty() -> Text(
                if (vm.epg.isEmpty) stringResource(R.string.guide_not_loaded) else stringResource(R.string.no_programme),
                color = C.Text3
            )
        }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(list, key = { _, p -> "${p.start}_${p.stop}_${p.title}" }) { i, p -> Column {
                val prevDay = list.getOrNull(i - 1)?.let { dayTitle(it.start) }
                val thisDay = dayTitle(p.start)
                if (prevDay != thisDay) {
                    Text(thisDay, color = C.Accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp))
                }
                val isNow = p.isNow(now)
                val past = p.stop <= now
                val canArchive = past && ch.variants.any {
                    StreamResolver.catchupUrl(it, p.start, p.stop, now) != null
                }
                FocusItem(
                    Modifier.fillMaxWidth(), selected = isNow,
                    focusRequester = if (i == target) fr else null,
                    onFocus = { focusedStart = p.start },
                    onClick = { if (isNow) onPlayLive() else if (canArchive) onPlayArchive(p) },
                ) { focused ->
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(hhmm(p.start), color = if (isNow) C.Accent else if (past) C.Text3 else C.Text2, fontSize = 14.sp, modifier = Modifier.width(52.dp))
                            Text(
                                p.title, color = if (past && !canArchive) C.Text3 else C.Text, fontSize = 15.sp,
                                fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = if (focused) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            val archiveDesc = stringResource(R.string.archive_available)
                            if (canArchive) Text(
                                "◷", color = C.Blue, fontSize = 14.sp,
                                modifier = Modifier.semantics { contentDescription = archiveDesc },
                            )
                            if (isNow) Pill(stringResource(R.string.pill_live), color = C.Red, textColor = Color.White)
                        }
                        if (isNow) ProgressLine(p.progress(now), Modifier.fillMaxWidth().padding(start = 52.dp, top = 6.dp))
                        val desc = p.desc
                        if ((focused || isNow) && !desc.isNullOrBlank()) {
                            Text(
                                desc, color = C.Text3, fontSize = 13.sp, maxLines = if (focused) 6 else 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 52.dp, top = 4.dp)
                            )
                        }
                    }
                }
            } }
        }
    }
}

@Composable
fun formatDays(d: Double): String = when {
    d >= 1 -> stringResource(R.string.archive_days, d.toInt())
    else -> stringResource(R.string.archive_hours, (d * 24).toInt())
}
