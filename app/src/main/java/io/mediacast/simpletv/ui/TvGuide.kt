package io.mediacast.simpletv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import io.mediacast.simpletv.data.StreamResolver
import kotlinx.coroutines.delay

private const val HALF_HOUR = 30 * 60 * 1000L
private const val GUIDE_WINDOW = 4 * HALF_HOUR

@Composable
fun TvGuide(vm: MainViewModel, now: Long, active: Channel?) {
    val channels by vm.channels.collectAsState()
    val epgVersion by vm.epgVersion.collectAsState()
    var anchor by rememberSaveable { mutableLongStateOf(0L) }
    val start = if (anchor == 0L) now / HALF_HOUR * HALF_HOUR else anchor
    val end = start + GUIDE_WINDOW
    var channelKey by rememberSaveable { mutableStateOf<String?>(active?.key) }
    var programmeStart by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedChannel = channels.firstOrNull { it.key == channelKey } ?: channels.firstOrNull()
    val programmes by produceState(emptyList<Programme>(), selectedChannel?.key, start, epgVersion) {
        value = selectedChannel?.let { vm.schedule(it, start, end) }.orEmpty()
    }
    val selected = programmes.firstOrNull { it.start == programmeStart }
        ?: programmes.firstOrNull { it.isNow(now) }
        ?: programmes.firstOrNull()
    val firstFocus = remember { FocusRequester() }
    val focusNow = remember { FocusRequester() }
    val state = rememberLazyListState(channels.indexOfFirst { it.key == channelKey }.coerceAtLeast(0))
    LaunchedEffect(Unit) { focusNow.requestFocus() }
    LaunchedEffect(selected?.start) {
        if (programmeStart == null && selected != null) programmeStart = selected.start
    }
    LaunchedEffect(epgVersion, channels.size) {
        if (programmeStart != null) {
            delay(100)
            runCatching { firstFocus.requestFocus() }
        }
    }

    fun play(channel: Channel, programme: Programme) {
        if (programme.isNow(now)) vm.play(channel)
        else if (channel.variants.any { StreamResolver.catchupUrl(it, programme.start, programme.stop, now) != null }) vm.playArchive(channel, programme)
        else return
        vm.closeOverlay()
    }

    Column(Modifier.fillMaxSize().background(C.panelBrush).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(selected?.title ?: stringResource(R.string.nav_programme), color = C.Text, fontSize = 24.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (selected != null) {
            Text("${hhmm(selected.start)} – ${hhmm(selected.stop)} · ${selectedChannel?.name.orEmpty()}", color = C.Text2, fontSize = 14.sp)
            selected.desc?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = C.Text2, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusItem(onClick = { anchor = start - HALF_HOUR }) { _ -> Text("‹  ${hhmm(start - HALF_HOUR)}", color = C.Text, modifier = Modifier.padding(12.dp)) }
            FocusItem(focusRequester = focusNow, onClick = { anchor = 0L; programmeStart = null }) { _ ->
                Text(stringResource(R.string.action_now), color = C.Text, modifier = Modifier.padding(12.dp))
            }
            FocusItem(onClick = { anchor = start + HALF_HOUR }) { _ -> Text("${hhmm(start + HALF_HOUR)}  ›", color = C.Text, modifier = Modifier.padding(12.dp)) }
            if (selectedChannel != null && selected != null && selectedChannel.variants.any {
                    StreamResolver.catchupUrl(it, selected.start, selected.stop, now) != null
                }) FocusItem(onClick = { vm.playArchive(selectedChannel, selected); vm.closeOverlay() }) { _ ->
                Text(stringResource(R.string.action_from_start), color = C.Accent2, modifier = Modifier.padding(12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 112.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(5) { Text(hhmm(start + it * HALF_HOUR), color = C.Text2, fontSize = 12.sp) }
        }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(channels, key = Channel::key) { channel ->
                val entries by produceState(emptyList<Programme>(), channel.key, start, epgVersion) {
                    value = vm.schedule(channel, start, end)
                }
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(112.dp), verticalAlignment = Alignment.CenterVertically) {
                        LogoImage(vm.logoOf(channel), channel.name, 32.dp)
                        Text(channel.name, color = C.Text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val trackWidth = maxWidth
                        fun fraction(time: Long) = ((time - start).toFloat() / GUIDE_WINDOW).coerceIn(0f, 1f)
                        for (programme in entries) {
                            val left = fraction(programme.start)
                            val width = trackWidth * (fraction(programme.stop) - left)
                            if (width <= 0.dp) continue
                            val focusedSlot = channel.key == channelKey && programme.start == programmeStart
                            FocusItem(
                                Modifier.offset(x = trackWidth * left).width(width).fillMaxHeight().padding(end = 2.dp).background(C.Card),
                                selected = programme.isNow(now),
                                focusRequester = if (focusedSlot) firstFocus else null,
                                onFocus = { channelKey = channel.key; programmeStart = programme.start },
                                onClick = { play(channel, programme) },
                            ) { _ ->
                                Text(programme.title, color = C.Text, fontSize = 14.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(4.dp))
                            }
                        }
                        if (now in start until end) {
                            Box(Modifier.offset(x = trackWidth * fraction(now)).width(2.dp).fillMaxHeight().background(C.Green))
                        }
                    }
                }
            }
        }
    }
}
