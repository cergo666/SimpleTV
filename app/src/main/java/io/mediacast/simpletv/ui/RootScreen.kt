package io.mediacast.simpletv.ui

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import io.mediacast.simpletv.data.StreamChecker
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.Programme
import io.mediacast.simpletv.data.PlaybackState
import io.mediacast.simpletv.data.StreamStats
import io.mediacast.simpletv.data.channelsLabel
import io.mediacast.simpletv.data.fmtFps
import io.mediacast.simpletv.data.fmtRate
import io.mediacast.simpletv.data.StreamKind
import io.mediacast.simpletv.data.StreamVariant
import io.mediacast.simpletv.StvApp.enforceReleaseDebuggerPolicy
import androidx.compose.ui.text.font.FontFamily

@OptIn(UnstableApi::class)
@Composable
fun StvRoot(vm: MainViewModel, onExit: () -> Unit, inPip: Boolean = false, onPip: (() -> Unit)? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val isTv = remember(context) { isTelevision(context) }
    val channels by vm.channels.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val playback by vm.controller.state.collectAsState()
    val plsBusy by vm.playlistBusy.collectAsState()
    val epgBusy by vm.epgBusy.collectAsState()
    val toast by vm.toast.collectAsState()

    val overlay by vm.overlay.collectAsState()
    var group by rememberSaveable { mutableStateOf(MainViewModel.ALL) }
    var infoTrigger by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf("") }
    var lastBack by remember { mutableLongStateOf(0L) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val rootFocus = remember { FocusRequester() }
    val browseState = rememberSaveableStateHolder()
    val video = remember(vm) { movableContentOf<Int, Modifier> { mode, modifier -> VideoSurface(vm, mode, modifier) } }
    var startupHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(loaded, channels.isNotEmpty()) {
        if (loaded && channels.isNotEmpty() && !startupHandled) {
            startupHandled = true
            if (vm.settings.showHomeAtStartup && !inPip && overlay == Overlay.NONE) vm.setOverlay(Overlay.HOME)
        }
    }

    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(15_000) }
    }

    fun showInfo() {
        infoVisible = true
        infoTrigger++
    }

    LaunchedEffect(playback.channel?.key, playback.archive) {
        if (playback.channel != null) showInfo()
    }
    LaunchedEffect(playback.channel != null) {
        if (!isTv && playback.channel != null) controlsVisible = true
    }
    LaunchedEffect(infoTrigger) {
        if (infoTrigger == 0) return@LaunchedEffect
        delay(5000)
        infoVisible = false
    }
    LaunchedEffect(controlsVisible, playback.channel?.key) {
        if (!controlsVisible) return@LaunchedEffect
        delay(5000)
        controlsVisible = false
    }
    LaunchedEffect(overlay) {
        if (overlay == Overlay.NONE) { delay(60); runCatching { rootFocus.requestFocus() } }
    }
    LaunchedEffect(number) {
        if (number.isEmpty()) return@LaunchedEffect
        delay(1600)
        if (!vm.playNumber(number.toInt())) vm.show(resources.getString(R.string.channel_not_found, number))
        number = ""
    }

    BackHandler {
        when {
            overlay != Overlay.NONE -> vm.closeOverlay()
            number.isNotEmpty() -> number = ""
            playback.archive != null -> vm.backToLive()
            infoVisible || controlsVisible -> { infoVisible = false; controlsVisible = false }
            System.currentTimeMillis() - lastBack < 2500 -> onExit()
            else -> { lastBack = System.currentTimeMillis(); vm.show(resources.getString(R.string.press_back_again)) }
        }
    }

    fun onKey(e: KeyEvent): Boolean {
        if (overlay != Overlay.NONE || e.type != KeyEventType.KeyDown) return false
        val digit = when (e.key) {
            Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2
            Key.Three, Key.NumPad3 -> 3; Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5
            Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7; Key.Eight, Key.NumPad8 -> 8
            Key.Nine, Key.NumPad9 -> 9; else -> -1
        }
        if (digit >= 0) { if (number.length < 4) number += digit; return true }
        when (e.key) {
            Key.DirectionUp -> vm.zapForward()
            Key.DirectionDown -> vm.zapBackward()
            Key.ChannelUp, Key.PageUp -> vm.zapForward()
            Key.ChannelDown, Key.PageDown -> vm.zapBackward()
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft -> vm.setOverlay(Overlay.PLAYLIST)
            Key.DirectionRight, Key.Guide, Key.TvDataService -> vm.setOverlay(if (isTv) Overlay.EPG else Overlay.GUIDE)
            Key.Menu, Key.Settings -> vm.setOverlay(Overlay.STREAM)
            Key.Info -> showInfo()
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
                with(vm.controller.player) { if (isPlaying) pause() else play() }
            Key.ProgramRed -> playback.channel?.let { vm.toggleFavorite(it) }
            Key.ProgramGreen, Key.MediaNext -> vm.nextVariant()
            Key.ProgramBlue, Key.MediaPrevious -> vm.playPreviousChannel()
            Key.ProgramYellow -> vm.toggleStats()
            else -> return false
        }
        return true
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent(::onKey)
    ) {
        val portrait = !inPip && !isTv && maxHeight > maxWidth
        val wide = !portrait && (isTv || maxWidth >= 840.dp)
        val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val portraitBar = 56.dp + topInset
        val videoArea = if (portrait) Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.37f).padding(top = portraitBar)
            else Modifier.fillMaxSize()

        val usePreview = isTv && wide && !inPip && overlay == Overlay.PLAYLIST && vm.settings.tvPreviewFirst
        if (!usePreview) video(resize, videoArea)
        if (inPip) return@BoxWithConstraints

        // жесты телефона: касание — панель, свайп — переключение каналов
        Box(
            videoArea
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) showInfo() else infoVisible = false
                    })
                }
                .pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { if (acc < -120) vm.zapForward() else if (acc > 120) vm.zapBackward() },
                        onVerticalDrag = { _, d -> acc += d },
                    )
                }
        )

        PlaybackStatus(vm, playback, isTv, if (portrait) {
            Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.13f)
        } else Modifier.align(Alignment.Center))

        if (loaded && channels.isEmpty()) {
            Welcome(plsBusy, onRefresh = { vm.refreshPlaylists() }, onSettings = { vm.setOverlay(Overlay.SETTINGS) }, modifier = Modifier.align(Alignment.Center))
        }

        val showChannelButtons = !isTv && !portrait && controlsVisible && channels.size > 1
        AnimatedVisibility(
            visible = !portrait && (infoVisible || showChannelButtons || playback.archive != null && controlsVisible) &&
                overlay == Overlay.NONE && playback.channel != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            playback.channel?.let { InfoBar(vm, it, playback, now, wide, showChannelButtons) }
        }

        AnimatedVisibility(
            visible = (controlsVisible || portrait) && overlay == Overlay.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = if (portrait) {
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black)
            } else {
                Modifier.align(Alignment.TopEnd)
            },
        ) {
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .then(if (portrait) Modifier.fillMaxWidth().heightIn(min = 56.dp) else Modifier.padding(16.dp))
                    .padding(horizontal = if (portrait) 16.dp else 0.dp),
                horizontalArrangement = if (portrait) Arrangement.Center else Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Default.Home, stringResource(R.string.browse_home)) { vm.setOverlay(Overlay.HOME) }
                if (!portrait) RoundButton(Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_channels)) { vm.setOverlay(Overlay.PLAYLIST) }
                if (portrait && vm.previousChannel() != null) {
                    RoundButton(ImageVector.vectorResource(R.drawable.ic_last_channel), stringResource(R.string.nav_last_channel), vm::playPreviousChannel)
                }
                RoundButton(Icons.Default.DateRange, stringResource(R.string.nav_programme)) { vm.setOverlay(if (wide) Overlay.EPG else Overlay.GUIDE) }
                RoundButton(Icons.Default.Refresh, stringResource(R.string.nav_stream)) { vm.setOverlay(Overlay.STREAM) }
                RoundButton(Icons.Default.Settings, stringResource(R.string.nav_settings)) { vm.setOverlay(Overlay.SETTINGS) }
                if (onPip != null && playback.channel != null && playback.isPlaying) {
                    RoundButton(ImageVector.vectorResource(R.drawable.ic_pip), stringResource(R.string.nav_pip), onPip)
                }
            }
        }

        if (overlay == Overlay.EPG && isTv) browseState.SaveableStateProvider("tvGuide") {
            TvGuide(vm, now, playback.channel)
        }

        if (overlay == Overlay.PLAYLIST || (overlay == Overlay.EPG && !isTv)) browseState.SaveableStateProvider("browse") {
            PlaylistOverlay(
                vm, wide, now, group, { group = it },
                focusEpg = overlay == Overlay.EPG,
                onClose = { vm.closeOverlay() },
                onSettings = { vm.setOverlay(Overlay.SETTINGS) },
                onHome = { vm.setOverlay(Overlay.HOME) },
                videoPreview = if (usePreview) ({
                    Box(Modifier.fillMaxWidth().height(minOf(maxHeight * 0.28f, 180.dp)).background(Color.Black)) {
                        video(resize, Modifier.fillMaxSize())
                        PlaybackStatus(vm, playback, true, Modifier.align(Alignment.Center))
                    }
                }) else null,
            )
        }

        if (overlay == Overlay.HOME) {
            BrowseHome(vm, now, onChannels = { vm.setOverlay(Overlay.PLAYLIST) })
        }

        if (overlay == Overlay.GUIDE) {
            playback.channel?.let { ch ->
                Box(Modifier.fillMaxSize().background(C.Panel).padding(12.dp)) {
                    EpgPanel(
                        vm, ch, now, focusCurrent = true,
                        onPlayLive = { vm.backToLive(); vm.closeOverlay() },
                        onPlayArchive = { p -> vm.playArchive(ch, p); vm.closeOverlay() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (overlay == Overlay.STREAM) {
            StreamPanel(vm, playback, resize, { resize = it }, onSettings = { vm.setOverlay(Overlay.SETTINGS) }, onClose = { vm.closeOverlay() }, modifier = Modifier.align(Alignment.CenterEnd))
        }

        if (overlay == Overlay.SETTINGS) SettingsScreen(vm, wide, onClose = { vm.closeOverlay() })

        if (portrait && overlay == Overlay.NONE && channels.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.63f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                PlaylistOverlay(
                    vm, false, now, group, { group = it },
                    focusEpg = false,
                    onClose = {},
                    onSettings = { vm.setOverlay(Overlay.SETTINGS) },
                    embedded = true,
                )
            }
        }

        if (number.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(32.dp).clip(RoundedCornerShape(16.dp)).background(C.Panel)
                    .padding(horizontal = 26.dp, vertical = 12.dp)
            ) { Text(number, color = C.Text, fontSize = 44.sp, fontWeight = FontWeight.Bold) }
        }

        // статус фонового обновления (как OSD-сообщения TVSources)
        val busy = plsBusy ?: epgBusy
        if (busy != null && overlay == Overlay.NONE) {
            Row(
                Modifier.align(Alignment.TopStart).padding(16.dp).clip(RoundedCornerShape(50)).background(C.Panel)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = C.Accent2, strokeWidth = 2.dp)
                Text("  $busy", color = C.Text2, fontSize = 13.sp, maxLines = 1)
            }
        }

        val statsShown by vm.showStats.collectAsState()
        if (statsShown && playback.channel != null && overlay == Overlay.NONE) {
            StatsOverlay(vm, playback, Modifier.align(Alignment.TopStart).padding(top = if (busy != null && overlay == Overlay.NONE) 64.dp else 16.dp, start = 16.dp))
        }

        ToastHost(toast, Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(vm: MainViewModel, resize: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                keepScreenOn = true
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                clearFocus()
                player = vm.controller.player
            }
        },
        update = { it.resizeMode = resize },
        onRelease = { it.player = null },
        modifier = modifier
    )
}

@Composable
private fun PlaybackStatus(vm: MainViewModel, s: PlaybackState, isTv: Boolean, modifier: Modifier) {
    val channel = s.channel
    when {
        s.error != null -> Column(
            modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(20.dp)).background(C.Panel).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(s.channel?.name ?: "", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.playback_failed, s.error.orEmpty()), color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            Text(stringResource(R.string.playback_sources_checked, s.channel?.variants?.size ?: 0), color = C.Text3, fontSize = 13.sp)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusItem(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Accent), onClick = { vm.controller.retry() }) { _ ->
                    Text(stringResource(R.string.action_retry), color = C.OnAccent, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
            Text(
                if (isTv) stringResource(R.string.hint_tv_keys) else stringResource(R.string.hint_touch),
                color = C.Text3,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        s.buffering && channel != null -> Column(
            modifier.clip(RoundedCornerShape(16.dp)).background(C.Panel).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(Modifier.size(30.dp), color = C.Accent, strokeWidth = 3.dp)
            val source = channel.variants.getOrNull(s.variantIndex)?.sourceName
            Text(stringResource(R.string.connecting, source ?: channel.name, s.variantIndex + 1, channel.variants.size),
                color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun InfoBar(vm: MainViewModel, ch: Channel, s: PlaybackState, now: Long, wide: Boolean, showChannelButtons: Boolean) {
    val epgVersion by vm.epgVersion.collectAsState()
    val nn by produceState(emptyList<Programme>(), ch.key, epgVersion, now / 60_000) { value = vm.nowNext(ch) }
    val fav by vm.favorites.collectAsState()
    val cur = s.archive ?: nn.firstOrNull { it.isNow(now) }
    val next = if (s.archive == null) nn.firstOrNull { it.start >= (cur?.stop ?: now) } else null
    val variant = ch.variants.getOrNull(s.variantIndex)
    val source = variant?.let { "${it.sourceName}${if (ch.variants.size > 1) " ${s.variantIndex + 1}/${ch.variants.size}" else ""}" }
    Column(
        Modifier.fillMaxWidth().background(C.bottomBrush)
            .padding(horizontal = if (wide) 32.dp else 16.dp)
            .padding(top = if (wide) 60.dp else 24.dp, bottom = if (showChannelButtons) 12.dp else 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(C.accentBrush).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(ch.number.toString(), color = C.OnAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            LogoImage(vm.logoOf(ch), ch.name, if (wide) 52.dp else 40.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ch.name, color = C.Text, fontSize = if (wide) 26.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (ch.key in fav) Icon(Icons.Default.Star, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                    if (s.archive != null) Pill(stringResource(R.string.pill_archive), color = C.Blue, textColor = C.OnAccent) else Pill(stringResource(R.string.pill_live), color = C.Red, textColor = C.OnAccent)
                    if (wide && source != null) Pill(source)
                }
                if (!wide && source != null) {
                    Text(source, color = C.Text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val st by rememberStats(vm, 2000)
                st.summary().takeIf { it.isNotBlank() }?.let {
                    Text(it, color = C.Text3, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
                if (cur != null) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${hhmm(cur.start)} – ${hhmm(cur.stop)}", color = C.Accent2, fontSize = 15.sp)
                        Text("   ${cur.title}", color = C.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val progress = if (s.archive != null) {
                        val pos = vm.controller.player.currentPosition
                        val dur = vm.controller.player.duration.takeIf { it > 0 }
                            ?: (cur.stop - cur.start).coerceAtLeast(1)
                        (pos.toFloat() / dur).coerceIn(0f, 1f)
                    } else cur.progress(now)
                    ProgressLine(progress, Modifier.fillMaxWidth(if (wide) 0.75f else 1f).padding(top = 8.dp), height = 4.dp)
                } else {
                    Text(groupLabel(ch.group), color = C.Text3, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                }
                if (next != null) Text(stringResource(R.string.next_programme, hhmm(next.start), next.title), color = C.Text3, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                if (s.archive != null) FocusItem(onClick = vm::backToLive) { _ ->
                    Text(stringResource(R.string.action_back_to_live), color = C.Blue, fontSize = 13.sp, modifier = Modifier.padding(6.dp))
                }
            }
            if (wide) Text(hhmm(now), color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 16.dp))
        }
        if (showChannelButtons) {
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RoundButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.zap_previous), vm::zapBackward)
                if (vm.previousChannel() != null) {
                    RoundButton(ImageVector.vectorResource(R.drawable.ic_last_channel), stringResource(R.string.nav_last_channel), vm::playPreviousChannel)
                }
                RoundButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.zap_next), vm::zapForward)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun StreamPanel(
    vm: MainViewModel, s: PlaybackState, resize: Int, onResize: (Int) -> Unit,
    onSettings: () -> Unit, onClose: () -> Unit, modifier: Modifier,
) {
    val ch = s.channel
    // Дешёвый вызов (несколько групп треков) — читаем напрямую, иначе список
    // протухает при смене треков без смены videoInfo.
    val qualities = vm.controller.qualities()
    val fr = remember { FocusRequester() }
    val modes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to stringResource(R.string.image_fit),
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to stringResource(R.string.image_zoom),
        AspectRatioFrameLayout.RESIZE_MODE_FILL to stringResource(R.string.image_fill),
    )
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    Column(
        modifier.fillMaxHeight().width(400.dp).background(C.Panel).padding(20.dp)
    ) {
        Text(stringResource(R.string.stream_title), color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        s.userAgent?.let { Text("UA: $it", color = C.Text3, fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 4.dp)) }
        LazyColumn(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (ch != null && ch.variants.size > 1) {
                item { Section(stringResource(R.string.section_source)) }
                items(ch.variants.size, key = { i -> ch.variants.getOrNull(i)?.url ?: i }) { i ->
                    val v = ch.variants[i]
                    FocusItem(Modifier.fillMaxWidth(), selected = i == s.variantIndex, focusRequester = if (i == s.variantIndex) fr else null,
                        onClick = { vm.selectVariant(ch, i); onClose() }) { _ ->
                        Column(Modifier.padding(12.dp)) {
                            Text("${i + 1}. ${v.sourceName}" + (if (i == s.variantIndex) "  ▶" else ""), color = C.Text, fontSize = 15.sp)
                            val status = when (StreamChecker.status(v.url)) {
                                StreamChecker.Health.PLAYING -> stringResource(R.string.stream_status_playing)
                                StreamChecker.Health.REACHABLE -> stringResource(R.string.stream_status_reachable)
                                StreamChecker.Health.UNAVAILABLE -> stringResource(R.string.stream_status_unavailable)
                                null -> stringResource(R.string.stream_status_unknown)
                            }
                            Text("${variantInfo(v)} · $status", color = C.Text2, fontSize = 12.sp, maxLines = 1)
                            Text(v.url, color = C.Text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { Section(stringResource(R.string.section_stream_info)) }
            item { StatsTable(vm, s) }
            item {
                val shown by vm.showStats.collectAsState()
                FocusItem(Modifier.fillMaxWidth(), onClick = { vm.toggleStats() }) { _ ->
                    Text(if (shown) stringResource(R.string.stats_toggle_hide) else stringResource(R.string.stats_toggle_show), color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                }
            }
            item { Section(stringResource(R.string.section_quality)) }
            item {
                FocusItem(Modifier.fillMaxWidth(), focusRequester = if (ch == null || ch.variants.size < 2) fr else null,
                    onClick = { vm.controller.setQuality(null); onClose() }) { _ ->
                    Text(stringResource(R.string.quality_auto), color = C.Text, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                }
            }
            items(qualities) { q ->
                FocusItem(Modifier.fillMaxWidth(), onClick = { vm.controller.setQuality(q); onClose() }) { _ ->
                    Text(q.label, color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                }
            }
            item { Section(stringResource(R.string.section_image)) }
            items(modes) { (m, label) ->
                FocusItem(Modifier.fillMaxWidth(), selected = m == resize, onClick = { onResize(m) }) { _ ->
                    Text(label, color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                }
            }
            item { Section(stringResource(R.string.section_other)) }
            if (ch != null) item {
                FocusItem(Modifier.fillMaxWidth(), onClick = { vm.toggleFavorite(ch) }) { _ ->
                    Text(stringResource(R.string.action_favorite_toggle), color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                }
            }
            item {
                FocusItem(Modifier.fillMaxWidth(), onClick = onSettings) { _ ->
                    Text(stringResource(R.string.nav_settings), color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, color = C.Accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Welcome(busy: String?, onRefresh: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(busy == null) { if (busy == null) { delay(100); runCatching { fr.requestFocus() } } }
    Column(
        modifier.widthIn(max = 560.dp).padding(24.dp).clip(RoundedCornerShape(24.dp)).background(C.Panel).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(C.accentBrush))
        Text(stringResource(R.string.app_name), color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.welcome_subtitle), color = C.Text3, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))
        if (busy != null) {
            CircularProgressIndicator(color = C.Accent)
            Text(busy, color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
        } else {
            Text(stringResource(R.string.channels_not_loaded), color = C.Text2, fontSize = 15.sp)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusItem(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Accent), focusRequester = fr, onClick = onRefresh) { _ ->
                    Text(stringResource(R.string.action_load_playlists), color = C.OnAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
                FocusItem(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Card), onClick = onSettings) { _ ->
                    Text(stringResource(R.string.nav_settings), color = C.Text2, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FocusItem(Modifier.size(48.dp), shape = RoundedCornerShape(50), onClick = onClick) { _ ->
        Box(Modifier.size(48.dp).clip(CircleShape).background(C.Panel), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = C.Text)
        }
    }
}

@Composable
private fun ToastHost(toast: Toast?, modifier: Modifier) {
    var visible by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(toast?.id) {
        if (toast == null) return@LaunchedEffect
        visible = toast
        delay(3500)
        if (visible?.id == toast.id) visible = null
    }
    AnimatedVisibility(visible != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut(), modifier = modifier) {
        Box(Modifier.padding(16.dp).clip(RoundedCornerShape(50)).background(C.PanelSolid).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(visible?.text ?: "", color = C.Text, fontSize = 14.sp, maxLines = 2)
        }
    }
}


@Composable
private fun rememberStats(vm: MainViewModel, periodMs: Long) = produceState(StreamStats()) {
    while (true) {
        value = vm.controller.stats.snapshot()
        delay(periodMs)
    }
}

@Composable
private fun variantInfo(v: StreamVariant): String = listOfNotNull(
    when (v.kind) { StreamKind.WINK -> "Wink"; StreamKind.BEELINE -> "Beeline"; else -> null },
    when {
        v.url.contains(".mpd", ignoreCase = true) -> "DASH"
        v.url.contains("m3u8", ignoreCase = true) -> "HLS"
        else -> null
    },
    v.clearKey?.let { "ClearKey" },
    v.catchup?.let { stringResource(R.string.archive_label, formatDays(it.days)) },
    v.userAgent?.let { stringResource(R.string.variant_own_ua) },
).joinToString(" · ").ifEmpty { stringResource(R.string.variant_stream) }

/** Подробные параметры видео/аудио/сети. */
@Composable
private fun statsRows(st: StreamStats, s: PlaybackState): List<Pair<String, String>> {
    val ch = s.channel
    val v = ch?.variants?.getOrNull(s.variantIndex)
    val hwVideo = when (st.videoHw) {
        true -> stringResource(R.string.decoder_hw)
        false -> stringResource(R.string.decoder_sw)
        null -> ""
    }
    val hwAudio = when (st.audioHw) {
        true -> stringResource(R.string.decoder_hw)
        false -> stringResource(R.string.decoder_sw)
        null -> ""
    }
    return listOfNotNull(
        stringResource(R.string.stats_protocol) to (st.protocol + (st.drm?.let { " • $it" } ?: "")),
        stringResource(R.string.stats_source) to (if (v != null) stringResource(R.string.stats_source_value, v.sourceName, s.variantIndex + 1, ch.variants.size) else "—"),
        stringResource(R.string.stats_video) to listOfNotNull(st.videoCodec, st.resolution, st.frameRate?.let { "${fmtFps(it)} fps" }, st.hdr).joinToString(" • ").ifEmpty { "—" },
        st.videoProfile?.let { stringResource(R.string.stats_profile) to it },
        stringResource(R.string.stats_video_bitrate) to fmtRate(st.videoBitrate?.toLong()),
        st.videoDecoder?.let { stringResource(R.string.stats_video_decoder) to "$it ($hwVideo)" },
        stringResource(R.string.stats_frames) to stringResource(
            R.string.stats_frames_value,
            st.renderFps?.let { fmtFps((it * 10).toInt() / 10f) } ?: "—",
            st.droppedFrames,
        ),
        stringResource(R.string.stats_audio) to listOfNotNull(
            st.audioCodec,
            st.audioChannels?.let { channelsLabel(it) },
            st.sampleRate?.let { stringResource(R.string.stats_sample_rate, (it / 1000.0).toString().removeSuffix(".0")) },
            st.audioLang,
        ).joinToString(" • ").ifEmpty { "—" },
        stringResource(R.string.stats_audio_bitrate) to fmtRate(st.audioBitrate?.toLong()),
        st.audioDecoder?.let { stringResource(R.string.stats_audio_decoder) to "$it ($hwAudio)" },
        stringResource(R.string.stats_network) to stringResource(R.string.stats_network_value, fmtRate(st.bandwidth), fmtRate(st.throughput)),
        stringResource(R.string.stats_buffer) to (
            st.liveOffsetSec?.let { offset ->
                stringResource(R.string.stats_buffer_offset, "%.1f".format(java.util.Locale.US, st.bufferSec), "%.0f".format(java.util.Locale.US, offset))
            } ?: stringResource(R.string.stats_buffer_value, "%.1f".format(java.util.Locale.US, st.bufferSec))
            ),
        stringResource(R.string.stats_loaded) to stringResource(R.string.stats_loaded_value, "%.1f".format(java.util.Locale.US, st.bytesLoaded / 1_048_576.0)),
        stringResource(R.string.stats_user_agent) to (s.userAgent ?: "—"),
    )
}

@Composable
private fun StatsTable(vm: MainViewModel, s: PlaybackState) {
    val st by rememberStats(vm, 1000)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card).padding(12.dp)) {
        statsRows(st, s).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(k, color = C.Text3, fontSize = 12.sp, modifier = Modifier.width(118.dp))
                Text(v, color = C.Text2, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatsOverlay(vm: MainViewModel, s: PlaybackState, modifier: Modifier) {
    val st by rememberStats(vm, 1000)
    Column(modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xB3000000)).padding(10.dp)) {
        val userAgentLabel = stringResource(R.string.stats_user_agent)
        val profileLabel = stringResource(R.string.stats_profile)
        statsRows(st, s).filter { it.first != userAgentLabel && it.first != profileLabel }.forEach { (k, v) ->
            Text("$k: $v", color = Color(0xFFB8F5C8), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
