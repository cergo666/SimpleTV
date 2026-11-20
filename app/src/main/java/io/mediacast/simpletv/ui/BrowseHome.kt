package io.mediacast.simpletv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.Programme
import kotlinx.coroutines.delay

@Composable
fun BrowseHome(
    vm: MainViewModel,
    now: Long,
    isTv: Boolean,
    onChannels: () -> Unit,
    onGuide: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val channels by vm.channels.collectAsState()
    val recent by vm.recent.collectAsState()
    val favourites by vm.favorites.collectAsState()
    val playback by vm.controller.state.collectAsState()
    val epgVersion by vm.epgVersion.collectAsState()
    val byKey = remember(channels) { channels.associateBy(Channel::key) }
    val history = recent.mapNotNull(byKey::get).take(8)
    val favouritesRow = channels.filter { it.key in favourites }.take(12)
    val guide by produceState(emptyMap<String, Programme>(), channels, epgVersion, now / 60_000) {
        value = vm.guideMatches(channels, "", now)
    }
    val live = channels.filter { it.key in guide }.take(12)
    val rows = remember(history, favouritesRow, live, channels) {
        buildList {
            if (history.isNotEmpty()) add(R.string.browse_continue to history)
            if (favouritesRow.isNotEmpty()) add(R.string.group_favourites to favouritesRow)
            if (live.isNotEmpty()) add(R.string.group_now to live)
            if (isEmpty() && channels.isNotEmpty()) add(R.string.group_all to channels.take(12))
        }
    }

    val firstCardFocus = remember { FocusRequester() }
    val channelsBtnFocus = remember { FocusRequester() }
    var initialFocused by remember { mutableStateOf(false) }
    LaunchedEffect(rows.isNotEmpty()) {
        if (!initialFocused) {
            initialFocused = true
            delay(80)
            val target = if (rows.isNotEmpty()) firstCardFocus else channelsBtnFocus
            runCatching { target.requestFocus() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.panelBrush)) {
        val wide = isTv || maxWidth >= 700.dp
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (isTv) Modifier.padding(horizontal = TvSafe.horizontal, vertical = TvSafe.vertical)
                    else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpace.lg),
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = if (wide) AppSpace.lg else AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpace.md),
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    stringResource(R.string.browse_home),
                    color = C.Text,
                    fontSize = if (wide) AppType.display else AppType.title,
                    fontWeight = FontWeight.Bold,
                )
                if (wide) {
                    Spacer(Modifier.width(AppSpace.md))
                    HomeNavPill(Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_channels), channelsBtnFocus, onChannels)
                    HomeNavPill(Icons.Default.DateRange, stringResource(R.string.nav_programme), null, onGuide)
                    HomeNavPill(Icons.Default.Settings, stringResource(R.string.nav_settings), null, onSettings)
                }
                Spacer(Modifier.weight(1f))
                if (wide) {
                    Text(hhmm(now), color = C.Text, fontSize = AppType.display, fontWeight = FontWeight.SemiBold)
                }
                if (!isTv) {
                    FocusItem(Modifier.size(48.dp), shape = CircleShape, onClick = onClose) { _ ->
                        Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = C.Text, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }

            if (!wide) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
                    contentPadding = PaddingValues(vertical = AppSpace.xs),
                    modifier = Modifier.padding(bottom = AppSpace.sm),
                ) {
                    item { HomeNavPill(Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_channels), channelsBtnFocus, onChannels) }
                    item { HomeNavPill(Icons.Default.DateRange, stringResource(R.string.nav_programme), null, onGuide) }
                    item { HomeNavPill(Icons.Default.Settings, stringResource(R.string.nav_settings), null, onSettings) }
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = AppSpace.xs),
                verticalArrangement = Arrangement.spacedBy(AppSpace.lg),
            ) {
                itemsIndexed(rows, key = { _, (titleRes, _) -> titleRes }) { rowIdx, (titleRes, list) ->
                    BrowseRow(
                        title = titleRes,
                        channels = list,
                        allChannels = channels,
                        playingKey = playback.channel?.key,
                        guide = guide,
                        now = now,
                        vm = vm,
                        firstFocus = if (rowIdx == 0) firstCardFocus else null,
                    )
                }
            }

            if (isTv) {
                RemoteHintBar(
                    listOf(
                        stringResource(R.string.hint_key_ok) to stringResource(R.string.hint_watch),
                        stringResource(R.string.hint_key_ud) to stringResource(R.string.hint_navigate),
                        stringResource(R.string.hint_key_back) to stringResource(R.string.hint_back),
                    ),
                    modifier = Modifier.padding(top = AppSpace.sm),
                )
            }
        }
    }
}

@Composable
private fun HomeNavPill(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    FocusItem(
        containerColor = C.Card,
        borderColor = C.Border,
        liftOnFocus = true,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) { _ ->
        Row(
            Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
        ) {
            Icon(icon, null, tint = C.Text2, modifier = Modifier.size(18.dp))
            Text(label, color = C.Text, fontSize = AppType.body, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun BrowseRow(
    title: Int,
    channels: List<Channel>,
    allChannels: List<Channel>,
    playingKey: String?,
    guide: Map<String, Programme>,
    now: Long,
    vm: MainViewModel,
    firstFocus: FocusRequester?,
) {
    Column {
        Text(
            stringResource(title),
            color = C.Text,
            fontSize = AppType.title,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = AppSpace.xs, bottom = AppSpace.sm),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppSpace.sm, vertical = AppSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpace.md),
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.key }) { idx, channel ->
                val isPlaying = channel.key == playingKey
                val prog = guide[channel.key]
                FocusItem(
                    Modifier.width(224.dp).height(140.dp),
                    selected = isPlaying,
                    containerColor = C.Card,
                    borderColor = C.Border,
                    liftOnFocus = true,
                    focusRequester = if (idx == 0) firstFocus else null,
                    shape = RoundedCornerShape(AppRadius.panel),
                    onClick = {
                        if (vm.controller.state.value.channel?.key != channel.key || vm.controller.state.value.archive != null) {
                            vm.play(channel, allChannels)
                        }
                        vm.closeOverlay()
                    },
                ) { _ ->
                    Column(Modifier.fillMaxSize().padding(AppSpace.md)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            LogoImage(vm.logoOf(channel), channel.name, 40.dp)
                            Spacer(Modifier.weight(1f))
                            if (isPlaying) {
                                Pill(stringResource(R.string.pill_live), color = C.LiveBg, textColor = C.OnLive)
                            } else if (channel.hasArchive) {
                                Icon(
                                    painterResource(R.drawable.ic_archive),
                                    stringResource(R.string.archive_available),
                                    tint = C.Blue,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(AppSpace.xs))
                            Text(channel.number.toString(), color = C.Text3, fontSize = AppType.caption)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            channel.name,
                            color = C.Text,
                            fontSize = AppType.subtitle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            prog?.title ?: groupLabel(channel.group),
                            color = if (prog != null) C.Text2 else C.Text3,
                            fontSize = AppType.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Spacer(Modifier.height(AppSpace.xs))
                        if (prog != null) {
                            ProgressLine(prog.progress(now), Modifier.fillMaxWidth(), height = 3.dp)
                        } else {
                            Spacer(Modifier.height(3.dp))
                        }
                    }
                }
            }
        }
    }
}
