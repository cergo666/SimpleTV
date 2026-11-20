package io.mediacast.simpletv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.Programme
import io.mediacast.simpletv.data.StreamResolver
import kotlinx.coroutines.delay

private const val HALF_HOUR = 30 * 60 * 1000L
private const val GUIDE_WINDOW = 4 * HALF_HOUR
private val CHANNEL_COL_WIDTH = 204.dp

@Composable
fun TvGuide(
    vm: MainViewModel,
    now: Long,
    active: Channel?,
    isTv: Boolean,
    group: String,
    onGroup: (String) -> Unit,
    onChannels: () -> Unit,
    onHome: () -> Unit,
) {
    val allChannels by vm.channels.collectAsState()
    val fav by vm.favorites.collectAsState()
    val epgVersion by vm.epgVersion.collectAsState()
    val availableGroups = remember(allChannels, fav) {
        vm.groups(allChannels, fav).map { it.name }
    }
    val activeGroup = if (group in availableGroups) group else MainViewModel.ALL
    val channels = remember(allChannels, fav, activeGroup) {
        vm.channelsOf(activeGroup, allChannels, fav)
    }

    var anchor by rememberSaveable { mutableLongStateOf(0L) }
    val start = if (anchor == 0L) now / HALF_HOUR * HALF_HOUR else anchor
    val end = start + GUIDE_WINDOW
    var channelKey by rememberSaveable { mutableStateOf<String?>(active?.key) }
    var programmeStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingEdgeFocus by remember { mutableStateOf<Int?>(null) }
    var pageFocusNonce by remember { mutableIntStateOf(0) }
    var initialFocused by remember { mutableStateOf(false) }

    val selectedChannel = channels.firstOrNull { it.key == channelKey } ?: channels.firstOrNull()
    val cachedSelectedSchedule = remember(selectedChannel?.key, start, end, epgVersion) {
        selectedChannel?.let { vm.peekSchedule(it, start, end) }
    }
    val programmes by produceState(cachedSelectedSchedule.orEmpty(), selectedChannel?.key, start, epgVersion) {
        value = selectedChannel?.let { vm.scheduleCached(it, start, end) }.orEmpty()
    }
    val selected = programmes.firstOrNull { it.start == programmeStart }
        ?: programmes.firstOrNull { it.isNow(now) }
        ?: programmes.firstOrNull()

    val firstFocus = remember { FocusRequester() }
    val focusNow = remember { FocusRequester() }
    val state = rememberLazyListState(channels.indexOfFirst { it.key == selectedChannel?.key }.coerceAtLeast(0))

    val rowLoaded by produceState(false, selectedChannel?.key, start, epgVersion) {
        value = if (selectedChannel == null) {
            true
        } else {
            vm.scheduleCached(selectedChannel, start, end)
            true
        }
    }

    LaunchedEffect(rowLoaded, channels.isEmpty()) {
        if (!initialFocused && rowLoaded) {
            initialFocused = true
            delay(80)
            val focused = runCatching { firstFocus.requestFocus() }.isSuccess
            if (!focused) runCatching { focusNow.requestFocus() }
        }
    }

    LaunchedEffect(selected?.start) {
        if (programmeStart == null && selected != null) programmeStart = selected.start
    }

    LaunchedEffect(pageFocusNonce) {
        if (pageFocusNonce == 0) return@LaunchedEffect
        delay(90)
        runCatching { firstFocus.requestFocus() }
    }

    val archiveUnavailableMsg = stringResource(R.string.archive_unavailable)
    val notStartedMsg = stringResource(R.string.programme_not_started)

    fun play(channel: Channel, programme: Programme) {
        val canArchive = programme.stop <= now &&
            channel.variants.any { StreamResolver.catchupUrl(it, programme.start, programme.stop, now) != null }
        when {
            programme.isNow(now) -> {
                vm.play(channel)
                vm.closeOverlay()
            }
            canArchive -> {
                vm.playArchive(channel, programme)
                vm.closeOverlay()
            }
            programme.stop <= now -> vm.show(archiveUnavailableMsg)
            else -> vm.show(notStartedMsg)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.panelBrush)
            .then(
                if (isTv) Modifier.padding(horizontal = TvSafe.horizontal, vertical = TvSafe.vertical)
                else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpace.lg),
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppSpace.lg),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected?.title ?: stringResource(R.string.nav_programme),
                    color = C.Text,
                    fontSize = AppType.display,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = if (selected != null) {
                    "${dayTitle(selected.start)} · ${hhmm(selected.start)} – ${hhmm(selected.stop)} · ${selectedChannel?.name.orEmpty()}"
                } else {
                    dayTitle(start)
                }
                Text(
                    meta,
                    color = C.Accent2,
                    fontSize = AppType.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = AppSpace.xs),
                )
                selected?.desc?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = C.Text2,
                        fontSize = AppType.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = AppSpace.xs),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusItem(
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(50),
                    onClick = onChannels,
                ) { _ ->
                    Row(
                        Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, null, tint = C.Text2, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.nav_channels), color = C.Text, fontSize = AppType.caption, fontWeight = FontWeight.Medium)
                    }
                }
                FocusItem(
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(50),
                    onClick = onHome,
                ) { _ ->
                    Row(
                        Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
                    ) {
                        Icon(Icons.Default.Home, null, tint = C.Text2, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.browse_home), color = C.Text, fontSize = AppType.caption, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(AppSpace.sm))
                Text(
                    hhmm(now),
                    color = C.Text,
                    fontSize = AppType.display,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        val archiveChannel = selectedChannel
        val archiveProgramme = selected?.takeIf { s ->
            archiveChannel?.variants?.any {
                StreamResolver.catchupUrl(it, s.start, s.stop, now) != null
            } == true
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
            contentPadding = PaddingValues(vertical = AppSpace.xs),
        ) {
            item {
                FocusItem(
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = {
                        pendingEdgeFocus = null
                        anchor = start - HALF_HOUR
                    },
                ) { _ ->
                    Row(
                        Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = C.Text2, modifier = Modifier.size(16.dp))
                        Text(hhmm(start - HALF_HOUR), color = C.Text, fontSize = AppType.body)
                    }
                }
            }
            item {
                FocusItem(
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    focusRequester = focusNow,
                    onClick = {
                        pendingEdgeFocus = null
                        anchor = 0L
                        programmeStart = null
                    },
                ) { _ ->
                    Text(
                        stringResource(R.string.action_now),
                        color = C.Text,
                        fontSize = AppType.body,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                    )
                }
            }
            item {
                FocusItem(
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = {
                        pendingEdgeFocus = null
                        anchor = start + HALF_HOUR
                    },
                ) { _ ->
                    Row(
                        Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
                    ) {
                        Text(hhmm(start + HALF_HOUR), color = C.Text, fontSize = AppType.body)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Text2, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (availableGroups.size > 1) {
                item {
                    FocusItem(
                        containerColor = C.Card,
                        borderColor = C.Border,
                        shape = RoundedCornerShape(AppRadius.control),
                        onClick = {
                            val idx = availableGroups.indexOf(activeGroup).coerceAtLeast(0)
                            onGroup(availableGroups[(idx + 1) % availableGroups.size])
                        },
                    ) { _ ->
                        Row(
                            Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = C.Accent2, modifier = Modifier.size(16.dp))
                            Text(groupLabel(activeGroup), color = C.Text, fontSize = AppType.body)
                        }
                    }
                }
            }
            if (archiveChannel != null && archiveProgramme != null) {
                item {
                    FocusItem(
                        containerColor = C.Accent,
                        prominent = true,
                        liftOnFocus = true,
                        shape = RoundedCornerShape(AppRadius.control),
                        onClick = {
                            vm.playArchive(archiveChannel, archiveProgramme)
                            vm.closeOverlay()
                        },
                    ) { _ ->
                        Text(
                            stringResource(R.string.action_from_start),
                            color = C.OnAccent,
                            fontSize = AppType.body,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                        )
                    }
                }
            }
        }

        // Выровненная временная шкала ровно по 30-минутным делениям трека
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(CHANNEL_COL_WIDTH))
            BoxWithConstraints(Modifier.weight(1f).height(20.dp)) {
                val rulerWidth = maxWidth
                repeat(4) { i ->
                    Text(
                        hhmm(start + i * HALF_HOUR),
                        color = C.Text3,
                        fontSize = AppType.caption,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.offset(x = rulerWidth * (i * 0.25f)),
                    )
                }
            }
        }
        HorizontalDivider(color = C.Divider)

        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = AppSpace.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpace.xs),
        ) {
            items(channels, key = Channel::key) { channel ->
                val cached = remember(channel.key, start, end, epgVersion) { vm.peekSchedule(channel, start, end) }
                val entries by produceState(cached.orEmpty(), channel.key, start, epgVersion) {
                    if (cached == null) {
                        value = vm.scheduleCached(channel, start, end)
                    } else {
                        value = cached
                    }
                }
                val isPlayingChannel = channel.key == active?.key
                val isSelectedRow = channel.key == selectedChannel?.key

                Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.width(CHANNEL_COL_WIDTH).padding(end = AppSpace.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            channel.number.toString(),
                            color = if (isPlayingChannel) C.Accent2 else C.Text3,
                            fontSize = AppType.caption,
                            fontWeight = if (isPlayingChannel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.widthIn(min = 32.dp).padding(end = AppSpace.xs),
                            maxLines = 1,
                        )
                        LogoImage(vm.logoOf(channel), channel.name, 32.dp)
                        Spacer(Modifier.width(AppSpace.sm))
                        Text(
                            channel.name,
                            color = if (isPlayingChannel || isSelectedRow) C.Text else C.Text2,
                            fontSize = AppType.body,
                            fontWeight = if (isPlayingChannel) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val trackWidth = maxWidth
                        repeat(4) { i ->
                            Box(
                                Modifier
                                    .offset(x = trackWidth * (i * 0.25f))
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(C.Divider),
                            )
                        }
                        fun fraction(time: Long) = ((time - start).toFloat() / GUIDE_WINDOW).coerceIn(0f, 1f)

                        if (entries.isEmpty()) {
                            FocusItem(
                                Modifier.fillMaxSize().padding(vertical = 2.dp),
                                selected = isPlayingChannel,
                                containerColor = C.Card,
                                borderColor = C.Border,
                                shape = RoundedCornerShape(AppRadius.control),
                                focusRequester = if (isSelectedRow) firstFocus else null,
                                onFocus = {
                                    channelKey = channel.key
                                    programmeStart = null
                                },
                                onClick = {
                                    vm.play(channel)
                                    vm.closeOverlay()
                                },
                            ) { _ ->
                                Text(
                                    stringResource(R.string.no_programme),
                                    color = C.Text3,
                                    fontSize = AppType.body,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = AppSpace.md),
                                )
                            }
                        } else {
                            val targetIdx = if (isSelectedRow) {
                                when (pendingEdgeFocus) {
                                    1 -> entries.lastIndex
                                    -1 -> 0
                                    else -> entries.indexOfFirst { it.start == programmeStart }
                                        .takeIf { it >= 0 }
                                        ?: entries.indexOfFirst { it.isNow(now) }.takeIf { it >= 0 }
                                        ?: 0
                                }
                            } else {
                                -1
                            }

                            entries.forEachIndexed { i, programme ->
                                val left = fraction(programme.start)
                                val right = fraction(programme.stop)
                                val width = trackWidth * (right - left)
                                if (width <= 2.dp) return@forEachIndexed
                                val isNow = programme.isNow(now)
                                val past = programme.stop <= now
                                val canArchive = past && channel.variants.any {
                                    StreamResolver.catchupUrl(it, programme.start, programme.stop, now) != null
                                }
                                val slotFocusRequester = if (isSelectedRow && i == targetIdx) firstFocus else null
                                FocusItem(
                                    Modifier
                                        .offset(x = trackWidth * left)
                                        .width(width)
                                        .fillMaxHeight()
                                        .padding(end = 2.dp, top = 2.dp, bottom = 2.dp)
                                        .onKeyEvent { e ->
                                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                            when {
                                                e.key == Key.DirectionRight && i == entries.lastIndex -> {
                                                    channelKey = channel.key
                                                    pendingEdgeFocus = 1
                                                    anchor = start + HALF_HOUR
                                                    pageFocusNonce++
                                                    true
                                                }
                                                e.key == Key.DirectionLeft && i == 0 -> {
                                                    channelKey = channel.key
                                                    pendingEdgeFocus = -1
                                                    anchor = start - HALF_HOUR
                                                    pageFocusNonce++
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                    selected = isNow,
                                    containerColor = C.Card,
                                    borderColor = C.Border,
                                    shape = RoundedCornerShape(AppRadius.control),
                                    focusRequester = slotFocusRequester,
                                    onFocus = {
                                        channelKey = channel.key
                                        programmeStart = programme.start
                                        if (pendingEdgeFocus != null) pendingEdgeFocus = null
                                    },
                                    onClick = { play(channel, programme) },
                                ) { _ ->
                                    Row(
                                        Modifier.fillMaxSize().padding(horizontal = AppSpace.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isNow) {
                                            SelectedBar(true)
                                            Spacer(Modifier.width(AppSpace.xs))
                                        }
                                        Text(
                                            programme.title,
                                            color = if (past && !canArchive) C.Text3 else C.Text,
                                            fontSize = AppType.body,
                                            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (canArchive && width >= 56.dp) {
                                            Icon(
                                                painterResource(R.drawable.ic_archive),
                                                stringResource(R.string.archive_available),
                                                tint = C.Blue,
                                                modifier = Modifier.padding(start = AppSpace.xs).size(12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (now in start until end) {
                            Box(
                                Modifier
                                    .offset(x = trackWidth * fraction(now))
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(C.Green),
                            )
                        }
                    }
                }
            }
        }

        if (isTv) {
            RemoteHintBar(
                listOf(
                    stringResource(R.string.hint_key_ok) to stringResource(R.string.hint_watch),
                    stringResource(R.string.hint_key_lr) to stringResource(R.string.hint_time),
                    stringResource(R.string.hint_key_back) to stringResource(R.string.hint_back),
                ),
            )
        }
    }
}
