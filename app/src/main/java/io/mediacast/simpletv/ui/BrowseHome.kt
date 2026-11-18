package io.mediacast.simpletv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.Programme

@Composable
fun BrowseHome(vm: MainViewModel, now: Long, onChannels: () -> Unit) {
    val channels by vm.channels.collectAsState()
    val recent by vm.recent.collectAsState()
    val favourites by vm.favorites.collectAsState()
    val epgVersion by vm.epgVersion.collectAsState()
    val byKey = remember(channels) { channels.associateBy(Channel::key) }
    val history = recent.mapNotNull(byKey::get).take(8)
    val favouritesRow = channels.filter { it.key in favourites }.take(12)
    val guide by produceState(emptyMap<String, Programme>(), channels, epgVersion, now / 60_000) {
        value = vm.guideMatches(channels, "", now)
    }
    val live = channels.filter { it.key in guide }.take(12)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LazyColumn(
        Modifier.fillMaxSize().background(C.panelBrush).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.browse_home), color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            FocusItem(focusRequester = focus, onClick = onChannels) { _ ->
                Text(stringResource(R.string.nav_channels), color = C.Text, modifier = Modifier.padding(16.dp))
            }
        }
        if (history.isNotEmpty()) item { BrowseRow(R.string.browse_continue, history, guide, now, vm) }
        if (favouritesRow.isNotEmpty()) item { BrowseRow(R.string.group_favourites, favouritesRow, guide, now, vm) }
        if (live.isNotEmpty()) item { BrowseRow(R.string.group_now, live, guide, now, vm) }
    }
}

@Composable
private fun BrowseRow(title: Int, channels: List<Channel>, guide: Map<String, Programme>, now: Long, vm: MainViewModel) {
    Text(stringResource(title), color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(channels, key = Channel::key) { channel ->
            FocusItem(Modifier.width(176.dp).background(C.Card), onClick = {
                if (vm.controller.state.value.channel?.key != channel.key || vm.controller.state.value.archive != null) {
                    vm.play(channel, channels)
                }
                vm.closeOverlay()
            }) { _ ->
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LogoImage(vm.logoOf(channel), channel.name, 48.dp)
                    Text(channel.name, color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    guide[channel.key]?.let {
                        Text(it.title, color = C.Text2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ProgressLine(it.progress(now), Modifier.fillMaxWidth(), height = 2.dp)
                    }
                }
            }
        }
    }
}
