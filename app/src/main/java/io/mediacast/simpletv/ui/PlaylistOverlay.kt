package io.mediacast.simpletv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.Programme
import io.mediacast.simpletv.data.StreamResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlaylistOverlay(
    vm: MainViewModel,
    isTv: Boolean,
    wide: Boolean,
    now: Long,
    group: String,
    onGroup: (String) -> Unit,
    focusEpg: Boolean,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    embedded: Boolean = false,
    onHome: (() -> Unit)? = null,
    onGuide: (() -> Unit)? = null,
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
    var focusChannelsNonce by remember { mutableIntStateOf(0) }
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
        Column(
            Modifier
                .fillMaxSize()
                .background(C.Panel)
                .then(if (!embedded) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(AppSpace.md),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
        ) {
            FocusItem(
                containerColor = C.Card,
                borderColor = C.Border,
                shape = RoundedCornerShape(AppRadius.control),
                onClick = { guideSelection = null },
            ) { _ ->
                Text(
                    stringResource(R.string.back_to_search),
                    color = C.Text,
                    fontSize = AppType.body,
                    modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                )
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
        val groupColumnFr = remember { FocusRequester() }
        val epgFr = remember { FocusRequester() }
        Column(
            Modifier
                .fillMaxSize()
                .background(C.panelBrush)
                .then(
                    if (isTv) Modifier.padding(horizontal = TvSafe.horizontal, vertical = TvSafe.vertical)
                    else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = AppSpace.xl, vertical = AppSpace.lg),
                ),
        ) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                GroupsColumn(
                    groups = groups,
                    selected = effectiveGroup,
                    selectedFocusRequester = groupColumnFr,
                    onGroup = {
                        onGroup(it)
                        query = ""
                    },
                    onConfirmGroup = {
                        onGroup(it)
                        query = ""
                        focusChannelsNonce++
                    },
                    onRightToChannels = { focusChannelsNonce++ },
                    onSettings = onSettings,
                    onHome = onHome,
                    onGuide = onGuide,
                    now = now,
                    modifier = Modifier.weight(0.24f).fillMaxHeight(),
                )
                Spacer(Modifier.width(AppSpace.md))
                ChannelsColumn(
                    vm = vm,
                    list = list,
                    playing = playing,
                    fav = fav,
                    now = now,
                    group = effectiveGroup,
                    query = query,
                    onQuery = { query = it },
                    focusCurrent = !focusEpg,
                    focusChannelsNonce = focusChannelsNonce,
                    onFocus = { focusedKey = it.key },
                    onPlay = play,
                    onLeftToGroups = { runCatching { groupColumnFr.requestFocus() } },
                    onRightToEpg = { runCatching { epgFr.requestFocus() } },
                    restoreKey = focused?.key,
                    matches = matches,
                    modifier = Modifier.weight(0.40f).fillMaxHeight(),
                )
                Spacer(Modifier.width(AppSpace.md))
                val epgCh = focused ?: playing
                if (epgCh != null) {
                    Column(Modifier.weight(0.36f).fillMaxHeight()) {
                        if (videoPreview != null) {
                            videoPreview()
                            Spacer(Modifier.height(AppSpace.sm))
                        }
                        EpgPanel(
                            vm = vm,
                            ch = epgCh,
                            now = now,
                            focusCurrent = focusEpg,
                            compact = videoPreview != null,
                            selectedProgramme = matches[epgCh.key]?.takeIf { query.isNotBlank() },
                            externalFocusRequester = epgFr,
                            onLeftToChannels = { focusChannelsNonce++ },
                            onPlayLive = { play(epgCh) },
                            onPlayArchive = { p -> vm.playArchive(epgCh, p); onClose() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                } else {
                    Column(Modifier.weight(0.36f)) { videoPreview?.invoke() }
                }
            }
            if (isTv) {
                val okLabel = stringResource(if (videoPreview != null) R.string.hint_preview_or_watch else R.string.hint_watch)
                RemoteHintBar(
                    listOf(
                        stringResource(R.string.hint_key_ok) to okLabel,
                        stringResource(R.string.hint_key_menu) to stringResource(R.string.hint_favorite),
                        stringResource(R.string.hint_key_lr) to stringResource(R.string.hint_columns),
                        stringResource(R.string.hint_key_back) to stringResource(R.string.hint_back),
                    ),
                    modifier = Modifier.padding(top = AppSpace.sm),
                )
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(C.Panel)
                .then(if (!embedded) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!embedded) {
                    FocusItem(Modifier.size(48.dp), shape = CircleShape, onClick = onClose) { _ ->
                        Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = C.Text, modifier = Modifier.align(Alignment.Center))
                    }
                }
                Text(
                    stringResource(R.string.nav_channels),
                    color = C.Text,
                    fontSize = AppType.title,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = AppSpace.xs),
                )
                FocusItem(Modifier.size(48.dp), shape = CircleShape, onClick = onSettings) { _ ->
                    Icon(Icons.Default.Settings, stringResource(R.string.nav_settings), tint = C.Text2, modifier = Modifier.align(Alignment.Center))
                }
            }
            SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = AppSpace.xs))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
                contentPadding = PaddingValues(vertical = AppSpace.xs),
            ) {
                items(groups, key = { it.name }) { g ->
                    val sel = g.name == effectiveGroup
                    FocusItem(
                        selected = sel,
                        containerColor = if (sel) C.Accent else C.Card,
                        borderColor = if (sel) Color.Transparent else C.Border,
                        shape = RoundedCornerShape(50),
                        onClick = { onGroup(g.name); query = "" },
                    ) { _ ->
                        Text(
                            if (g.count < 0) groupLabel(g.name) else "${groupLabel(g.name)}  ${g.count}",
                            color = if (sel) C.OnAccent else C.Text2,
                            fontSize = AppType.caption,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                        )
                    }
                }
            }
            ChannelsColumn(
                vm = vm,
                list = list,
                playing = playing,
                fav = fav,
                now = now,
                group = effectiveGroup,
                query = query,
                onQuery = { query = it },
                focusCurrent = false,
                focusChannelsNonce = 0,
                onFocus = {},
                onPlay = play,
                showHeader = false,
                matches = matches,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GroupsColumn(
    groups: List<Group>,
    selected: String,
    selectedFocusRequester: FocusRequester,
    onGroup: (String) -> Unit,
    onConfirmGroup: (String) -> Unit,
    onRightToChannels: () -> Unit,
    onSettings: () -> Unit,
    onHome: (() -> Unit)?,
    onGuide: (() -> Unit)?,
    now: Long,
    modifier: Modifier,
) {
    val homeFr = remember { FocusRequester() }
    val guideFr = remember { FocusRequester() }
    val settingsFr = remember { FocusRequester() }
    Column(
        modifier.onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                onRightToChannels()
                true
            } else {
                false
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = AppSpace.sm, vertical = AppSpace.xs)) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(AppSpace.xs))
            Text(stringResource(R.string.app_name), color = C.Text, fontSize = AppType.title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(hhmm(now), color = C.Text2, fontSize = AppType.subtitle, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(AppSpace.sm))
        if (onHome != null) {
            FocusItem(
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                            when {
                                onGuide != null -> runCatching { guideFr.requestFocus() }.isSuccess
                                else -> runCatching { selectedFocusRequester.requestFocus() }.isSuccess
                            }
                        } else {
                            false
                        }
                    },
                shape = RoundedCornerShape(AppRadius.control),
                focusRequester = homeFr,
                onClick = onHome,
            ) { _ ->
                Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = C.Text2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpace.sm))
                    Text(stringResource(R.string.browse_home), color = C.Text, fontSize = AppType.body, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (onGuide != null) {
            FocusItem(
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> if (onHome != null) runCatching { homeFr.requestFocus() }.isSuccess else false
                            Key.DirectionDown -> runCatching { selectedFocusRequester.requestFocus() }.isSuccess
                            else -> false
                        }
                    },
                shape = RoundedCornerShape(AppRadius.control),
                focusRequester = guideFr,
                onClick = onGuide,
            ) { _ ->
                Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, null, tint = C.Text2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpace.sm))
                    Text(stringResource(R.string.nav_programme), color = C.Text, fontSize = AppType.body, fontWeight = FontWeight.Medium)
                }
            }
        }
        HorizontalDivider(color = C.Divider, modifier = Modifier.padding(vertical = AppSpace.xs))
        val groupState = rememberLazyListState()
        val selectedGroupIdx = groups.indexOfFirst { it.name == selected }.coerceAtLeast(0)
        val firstVisibleGroupIdx = groupState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: selectedGroupIdx
        val targetGroupName = if (groupState.layoutInfo.visibleItemsInfo.any { it.index == selectedGroupIdx }) {
            groups.getOrNull(selectedGroupIdx)?.name
        } else {
            groups.getOrNull(firstVisibleGroupIdx)?.name
        }
        LazyColumn(
            Modifier.weight(1f),
            state = groupState,
            contentPadding = PaddingValues(AppSpace.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(groups, key = { _, g -> g.name }) { idx, g ->
                val isSelected = g.name == selected
                FocusItem(
                    Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                e.key == Key.DirectionUp && idx == 0 -> {
                                    when {
                                        onGuide != null -> runCatching { guideFr.requestFocus() }.isSuccess
                                        onHome != null -> runCatching { homeFr.requestFocus() }.isSuccess
                                        else -> false
                                    }
                                }
                                e.key == Key.DirectionDown && idx == groups.lastIndex ->
                                    runCatching { settingsFr.requestFocus() }.isSuccess
                                else -> false
                            }
                        },
                    selected = isSelected,
                    shape = RoundedCornerShape(AppRadius.control),
                    focusRequester = if (g.name == targetGroupName) selectedFocusRequester else null,
                    onFocus = { if (!isSelected) onGroup(g.name) },
                    onClick = { onConfirmGroup(g.name) },
                ) { _ ->
                    Row(Modifier.padding(horizontal = AppSpace.sm, vertical = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                        SelectedBar(isSelected)
                        Spacer(Modifier.width(AppSpace.sm))
                        if (g.name == MainViewModel.FAV) {
                            Icon(Icons.Default.Star, null, tint = C.Accent2, modifier = Modifier.padding(end = AppSpace.xs).size(16.dp))
                        }
                        Text(
                            groupLabel(g.name),
                            color = if (isSelected) C.Text else C.Text2,
                            fontWeight = if (g.special || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = AppType.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (g.count >= 0) {
                            Text("${g.count}", color = C.Text3, fontSize = AppType.caption, modifier = Modifier.padding(start = AppSpace.sm))
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = C.Divider, modifier = Modifier.padding(vertical = AppSpace.xs))
        FocusItem(
            Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                        runCatching { selectedFocusRequester.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(AppRadius.control),
            focusRequester = settingsFr,
            onClick = onSettings,
        ) { _ ->
            Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = C.Text2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpace.sm))
                Text(stringResource(R.string.nav_settings), color = C.Text2, fontSize = AppType.body)
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onDown: (() -> Boolean)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AppRadius.control)
    Row(
        modifier
            .clip(shape)
            .background(if (focused) C.CardHover else C.Card)
            .border(if (focused) 2.dp else 1.dp, if (focused) C.Accent2 else C.Border, shape)
            .then(
                if (onDown != null) {
                    Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) onDown() else false
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = if (focused) C.Accent2 else C.Text3, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(start = AppSpace.sm)) {
            if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = C.Text3, fontSize = AppType.body)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = AppType.body),
                cursorBrush = SolidColor(C.Accent2),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (query.isNotEmpty()) {
            FocusItem(Modifier.size(36.dp), shape = CircleShape, onClick = { onQuery("") }) { _ ->
                Icon(Icons.Default.Close, stringResource(R.string.action_clear), tint = C.Text2, modifier = Modifier.align(Alignment.Center).size(18.dp))
            }
        }
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
    focusChannelsNonce: Int,
    onFocus: (Channel) -> Unit,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    onLeftToGroups: (() -> Unit)? = null,
    onRightToEpg: (() -> Unit)? = null,
    showHeader: Boolean = true,
    restoreKey: String? = null,
    matches: Map<String, Programme> = emptyMap(),
) {
    val state = rememberLazyListState()
    val target = list.indexOfFirst { it.key == (restoreKey ?: playing?.key) }.let { if (it < 0) 0 else it }
    val fr = remember { FocusRequester() }
    val searchFr = remember { FocusRequester() }

    // Прокручиваем при смене группы или поиска, но не крадём фокус из колонки групп и поля поиска.
    LaunchedEffect(query, group) {
        if (list.isNotEmpty()) state.scrollToItem((target - 4).coerceAtLeast(0))
    }

    // Начальный фокус при открытии оверлея или явном подтверждении (OK) группы.
    LaunchedEffect(focusCurrent, focusChannelsNonce) {
        if (!focusCurrent && focusChannelsNonce == 0) return@LaunchedEffect
        if (list.isNotEmpty()) {
            state.scrollToItem((target - 4).coerceAtLeast(0))
            delay(80)
            runCatching { fr.requestFocus() }
        }
    }

    Column(
        modifier.then(
            if (onLeftToGroups != null || onRightToEpg != null) {
                Modifier.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.DirectionLeft && onLeftToGroups != null -> {
                            onLeftToGroups()
                            true
                        }
                        e.key == Key.DirectionRight && onRightToEpg != null -> {
                            onRightToEpg()
                            true
                        }
                        else -> false
                    }
                }
            } else {
                Modifier
            },
        ),
    ) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = AppSpace.sm, bottom = AppSpace.sm)) {
                Text(
                    if (query.isBlank()) groupLabel(group) else stringResource(R.string.search_title),
                    color = C.Text,
                    fontSize = AppType.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text("  ${list.size}", color = C.Text3, fontSize = AppType.body, modifier = Modifier.padding(bottom = 2.dp))
            }
            SearchField(
                query,
                onQuery,
                Modifier.fillMaxWidth().padding(bottom = AppSpace.sm),
                focusRequester = searchFr,
                onDown = { runCatching { fr.requestFocus() }.isSuccess },
            )
        }
        if (list.isEmpty()) {
            Text(
                if (query.isBlank()) stringResource(R.string.empty_group) else stringResource(R.string.nothing_found),
                color = C.Text3,
                fontSize = AppType.body,
                modifier = Modifier.padding(AppSpace.lg),
            )
        }
        val firstVisibleChannelIdx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        val effectiveTargetIdx = if (state.layoutInfo.visibleItemsInfo.isEmpty() || state.layoutInfo.visibleItemsInfo.any { it.index == target }) {
            target
        } else {
            firstVisibleChannelIdx
        }
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(AppSpace.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(list, key = { _, ch -> ch.key }) { i, ch ->
                ChannelRow(
                    vm = vm,
                    ch = ch,
                    now = now,
                    playing = ch.key == playing?.key,
                    fav = ch.key in fav,
                    match = matches[ch.key]?.takeIf { query.isNotBlank() },
                    showNext = group == MainViewModel.NOW,
                    focusRequester = if (i == effectiveTargetIdx) fr else null,
                    onUpToSearch = if (i == 0 && showHeader) ({ runCatching { searchFr.requestFocus() }.isSuccess }) else null,
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
    onUpToSearch: (() -> Boolean)? = null,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    val epgVersion by vm.epgVersion.collectAsState()
    val minute = now / 60_000
    val cached = remember(ch.key, epgVersion, minute) { vm.peekNowNext(ch, now) }
    val nn by produceState(cached.orEmpty(), ch.key, epgVersion, minute) {
        if (cached == null) {
            delay(30)
            value = vm.nowNextCached(ch, now)
        } else {
            value = cached
        }
    }
    val cur = nn.firstOrNull { it.isNow(now) }
    FocusItem(
        Modifier
            .fillMaxWidth()
            .then(
                if (onUpToSearch != null) {
                    Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) onUpToSearch() else false
                    }
                } else {
                    Modifier
                },
            ),
        selected = playing,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(AppRadius.control),
        onFocus = onFocus,
        onLongClick = onLong,
        onClick = onClick,
    ) { focused ->
        Row(Modifier.padding(horizontal = AppSpace.sm, vertical = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            SelectedBar(playing)
            Spacer(Modifier.width(AppSpace.sm))
            Text(
                ch.number.toString(),
                color = if (playing) C.Accent2 else C.Text3,
                fontSize = AppType.body,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.widthIn(min = 42.dp).padding(end = AppSpace.xs),
                maxLines = 1,
            )
            LogoImage(vm.logoOf(ch), ch.name, 36.dp)
            Column(Modifier.weight(1f).padding(start = AppSpace.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ch.name,
                        color = C.Text,
                        fontSize = AppType.subtitle,
                        fontWeight = if (playing || focused) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fav) Icon(Icons.Default.Star, null, tint = C.Accent2, modifier = Modifier.padding(start = AppSpace.xs).size(14.dp))
                    if (ch.hasArchive) {
                        Icon(
                            painterResource(R.drawable.ic_archive),
                            stringResource(R.string.archive_available),
                            tint = C.Blue,
                            modifier = Modifier.padding(start = AppSpace.xs).size(14.dp),
                        )
                    }
                    if (ch.variants.size > 1) {
                        Text(" ×${ch.variants.size}", color = C.Text3, fontSize = AppType.caption)
                    }
                }
                if (match != null && match != cur) {
                    Text("${hhmm(match.start)}  ${match.title}", color = C.Accent2, fontSize = AppType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (cur != null) {
                    Text(cur.title, color = C.Text2, fontSize = AppType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressLine(cur.progress(now), Modifier.fillMaxWidth().padding(top = AppSpace.xs), height = 3.dp)
                    if (showNext) {
                        nn.firstOrNull { it.start > now }?.let { next ->
                            Text(
                                stringResource(R.string.next_programme_short, hhmm(next.start), next.title),
                                color = C.Text3,
                                fontSize = AppType.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Text(groupLabel(ch.group), color = C.Text3, fontSize = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (playing) {
                Pill(
                    stringResource(R.string.pill_live),
                    color = C.LiveBg,
                    textColor = C.OnLive,
                    modifier = Modifier.padding(start = AppSpace.sm),
                )
            }
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
    compact: Boolean = false,
    selectedProgramme: Programme? = null,
    externalFocusRequester: FocusRequester? = null,
    onLeftToChannels: (() -> Unit)? = null,
) {
    val epgVersion by vm.epgVersion.collectAsState()
    val archiveDays = ch.variants.maxOfOrNull { it.catchup?.days ?: 0.0 } ?: 0.0
    val from = now - (maxOf(archiveDays, 0.25) * 86_400_000).toLong()
    val to = now + 3 * 86_400_000L
    val day = now / 86_400_000
    val cachedSchedule = remember(ch.key, epgVersion, day, archiveDays) { vm.peekSchedule(ch, from, to) }
    val items by produceState<List<Programme>?>(cachedSchedule, ch.key, epgVersion, day, archiveDays) {
        if (cachedSchedule == null) {
            delay(120) // не дёргаем базу при быстрой прокрутке каналов
            value = vm.scheduleCached(ch, from, to)
        } else {
            value = cachedSchedule
        }
    }
    val state = rememberLazyListState()
    val internalFr = remember { FocusRequester() }
    val fr = externalFocusRequester ?: internalFr
    val list = items ?: emptyList()
    var focusedStart by rememberSaveable(ch.key) { mutableStateOf<Long?>(null) }
    val curIdx = list.indexOfFirst { it.isNow(now) }.let { if (it < 0) list.indexOfFirst { p -> p.start > now } else it }
    val target = (focusedStart ?: selectedProgramme?.start)?.let { start ->
        list.indexOfFirst { it.start == start }.takeIf { it >= 0 }
    } ?: curIdx
    LaunchedEffect(items, focusCurrent, selectedProgramme) {
        if (list.isNotEmpty() && target >= 0) {
            state.scrollToItem((target - 2).coerceAtLeast(0))
            if (focusCurrent) {
                delay(80)
                runCatching { fr.requestFocus() }
            }
        }
    }
    val archiveUnavailableMsg = stringResource(R.string.archive_unavailable)
    val notStartedMsg = stringResource(R.string.programme_not_started)

    Column(
        modifier
            .clip(RoundedCornerShape(AppRadius.card))
            .background(C.PanelSolid)
            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
            .then(
                if (onLeftToChannels != null) {
                    Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                            onLeftToChannels()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(AppSpace.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoImage(vm.logoOf(ch), ch.name, 40.dp)
            Column(Modifier.padding(start = AppSpace.md).weight(1f)) {
                Text(ch.name, color = C.Text, fontSize = AppType.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = AppSpace.xs),
                ) {
                    if (archiveDays > 0) {
                        Pill(stringResource(R.string.archive_label, formatDays(archiveDays)), color = C.Blue, textColor = C.OnInfo)
                    }
                    if (ch.variants.size > 1) {
                        Text(pluralStringResource(R.plurals.variants_count, ch.variants.size, ch.variants.size), color = C.Text3, fontSize = AppType.caption)
                    }
                }
            }
        }
        // Ручной выбор источника: OK по источнику — играть канал с него (выбор запоминается)
        val playback by vm.controller.state.collectAsState()
        val playingThis = playback.channel?.key == ch.key
        if (ch.variants.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
                contentPadding = PaddingValues(vertical = AppSpace.xs),
                modifier = Modifier.padding(top = AppSpace.sm),
            ) {
                items(ch.variants.size, key = { i -> ch.variants.getOrNull(i)?.url ?: i }) { i ->
                    val v = ch.variants[i]
                    val active = playingThis && playback.channel?.variants?.getOrNull(playback.variantIndex)?.url == v.url
                    FocusItem(
                        containerColor = if (active) C.Accent else C.Card,
                        borderColor = if (active) Color.Transparent else C.Border,
                        prominent = active,
                        liftOnFocus = true,
                        shape = RoundedCornerShape(50),
                        onClick = { vm.selectVariant(ch, i) },
                    ) { _ ->
                        Text(
                            "${i + 1}. ${v.sourceName}",
                            color = if (active) C.OnAccent else C.Text2,
                            fontSize = AppType.caption,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.xs),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpace.sm))
        val scope = rememberCoroutineScope()
        val selected = list.firstOrNull { it.start == focusedStart }
            ?: selectedProgramme
            ?: list.getOrNull(curIdx)?.takeIf { it.isNow(now) }
        if (!compact && selected != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.panel))
                    .background(C.Card)
                    .border(1.dp, C.Border, RoundedCornerShape(AppRadius.panel))
                    .padding(AppSpace.md),
            ) {
                Text(selected.title, color = C.Text, fontSize = AppType.subtitle, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${hhmm(selected.start)} – ${hhmm(selected.stop)}", color = C.Accent2, fontSize = AppType.caption, modifier = Modifier.padding(top = AppSpace.xs))
                selected.desc?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = C.Text2, fontSize = AppType.body, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = AppSpace.xs))
                }
            }
            Spacer(Modifier.height(AppSpace.sm))
        }
        val playableSelected = selected?.takeIf { s ->
            ch.variants.any { StreamResolver.catchupUrl(it, s.start, s.stop, now) != null }
        }
        val showBackToLive = !playingThis || playback.archive != null
        val firstActionFr = remember { FocusRequester() }
        val firstActionKey = when {
            curIdx >= 0 -> "now"
            playableSelected != null -> "from_start"
            showBackToLive -> "live"
            else -> null
        }
        LazyRow(
            Modifier.onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                    runCatching { fr.requestFocus() }.isSuccess
                } else {
                    false
                }
            },
            horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
            contentPadding = PaddingValues(vertical = AppSpace.xs),
        ) {
            if (curIdx >= 0) {
                item {
                    FocusItem(
                        containerColor = C.Card,
                        borderColor = C.Border,
                        shape = RoundedCornerShape(AppRadius.control),
                        focusRequester = if (firstActionKey == "now") firstActionFr else null,
                        onClick = {
                            focusedStart = list[curIdx].start
                            scope.launch { state.scrollToItem(curIdx) }
                        },
                    ) { _ ->
                        Text(stringResource(R.string.action_now), color = C.Text, fontSize = AppType.caption, modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm))
                    }
                }
            }
            if (playableSelected != null) {
                item {
                    FocusItem(
                        containerColor = C.Accent,
                        prominent = true,
                        shape = RoundedCornerShape(AppRadius.control),
                        focusRequester = if (firstActionKey == "from_start") firstActionFr else null,
                        onClick = { onPlayArchive(playableSelected) },
                    ) { _ ->
                        Text(
                            stringResource(R.string.action_from_start),
                            color = C.OnAccent,
                            fontSize = AppType.caption,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                        )
                    }
                }
            }
            if (showBackToLive) {
                item {
                    FocusItem(
                        containerColor = C.Card,
                        borderColor = C.Border,
                        shape = RoundedCornerShape(AppRadius.control),
                        focusRequester = if (firstActionKey == "live") firstActionFr else null,
                        onClick = onPlayLive,
                    ) { _ ->
                        Text(stringResource(R.string.action_back_to_live), color = C.Accent2, fontSize = AppType.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm))
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpace.xs))
        when {
            items == null -> Text(stringResource(R.string.loading_guide), color = C.Text3, fontSize = AppType.body, modifier = Modifier.padding(vertical = AppSpace.sm))
            list.isEmpty() -> Text(
                if (vm.epg.isEmpty) stringResource(R.string.guide_not_loaded) else stringResource(R.string.no_programme),
                color = C.Text3,
                fontSize = AppType.body,
                modifier = Modifier.padding(vertical = AppSpace.sm),
            )
        }
        val firstVisibleEpgIdx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        val effectiveEpgTargetIdx = if (state.layoutInfo.visibleItemsInfo.isEmpty() || state.layoutInfo.visibleItemsInfo.any { it.index == target }) {
            target
        } else {
            firstVisibleEpgIdx
        }
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(AppSpace.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(list, key = { _, p -> "${p.start}_${p.stop}_${p.title}" }) { i, p ->
                Column {
                    val prevDay = list.getOrNull(i - 1)?.let { dayTitle(it.start) }
                    val thisDay = dayTitle(p.start)
                    if (prevDay != thisDay) {
                        Text(
                            thisDay,
                            color = C.Accent2,
                            fontSize = AppType.caption,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = AppSpace.sm, top = AppSpace.md, bottom = AppSpace.xs),
                        )
                    }
                    val isNow = p.isNow(now)
                    val past = p.stop <= now
                    val canArchive = past && ch.variants.any {
                        StreamResolver.catchupUrl(it, p.start, p.stop, now) != null
                    }
                    FocusItem(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (i == 0 && firstActionKey != null) {
                                    Modifier.onPreviewKeyEvent { e ->
                                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                                            runCatching { firstActionFr.requestFocus() }.isSuccess
                                        } else {
                                            false
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        selected = isNow,
                        shape = RoundedCornerShape(AppRadius.control),
                        focusRequester = if (i == effectiveEpgTargetIdx) fr else null,
                        onFocus = { focusedStart = p.start },
                        onClick = {
                            when {
                                isNow -> onPlayLive()
                                canArchive -> onPlayArchive(p)
                                past -> vm.show(archiveUnavailableMsg)
                                else -> vm.show(notStartedMsg)
                            }
                        },
                    ) { focused ->
                        Column(Modifier.padding(horizontal = AppSpace.sm, vertical = AppSpace.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SelectedBar(isNow)
                                Spacer(Modifier.width(AppSpace.xs))
                                Text(
                                    hhmm(p.start),
                                    color = if (isNow) C.Accent2 else if (past) C.Text3 else C.Text2,
                                    fontSize = AppType.body,
                                    modifier = Modifier.width(48.dp),
                                )
                                Text(
                                    p.title,
                                    color = if (past && !canArchive) C.Text3 else C.Text,
                                    fontSize = AppType.body,
                                    fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = if (focused) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (canArchive) {
                                    Icon(
                                        painterResource(R.drawable.ic_archive),
                                        stringResource(R.string.archive_available),
                                        tint = C.Blue,
                                        modifier = Modifier.padding(start = AppSpace.xs).size(14.dp),
                                    )
                                }
                                if (isNow) {
                                    Pill(
                                        stringResource(R.string.pill_live),
                                        color = C.LiveBg,
                                        textColor = C.OnLive,
                                        modifier = Modifier.padding(start = AppSpace.xs),
                                    )
                                }
                            }
                            if (isNow) {
                                ProgressLine(p.progress(now), Modifier.fillMaxWidth().padding(start = 54.dp, top = AppSpace.xs))
                            }
                            if (compact && focused && !p.desc.isNullOrBlank()) {
                                Text(
                                    p.desc.orEmpty(),
                                    color = C.Text3,
                                    fontSize = AppType.caption,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 54.dp, top = AppSpace.xs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun formatDays(d: Double): String = when {
    d >= 1 -> stringResource(R.string.archive_days, d.toInt())
    else -> stringResource(R.string.archive_hours, (d * 24).toInt())
}
