package io.mediacast.simpletv.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.update.AppUpdate
import io.mediacast.simpletv.data.PlaybackState
import io.mediacast.simpletv.data.Programme
import io.mediacast.simpletv.data.StreamChecker
import io.mediacast.simpletv.data.StreamKind
import io.mediacast.simpletv.data.StreamStats
import io.mediacast.simpletv.data.StreamVariant
import io.mediacast.simpletv.data.channelsLabel
import io.mediacast.simpletv.data.fmtFps
import io.mediacast.simpletv.data.fmtRate
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun StvRoot(vm: MainViewModel, onExit: () -> Unit, inPip: Boolean = false, onPip: (() -> Unit)? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiMode by vm.uiMode.collectAsState()
    val isTv = remember(context, uiMode) { effectiveIsTv(context, uiMode) }
    val channels by vm.channels.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val playback by vm.controller.state.collectAsState()
    val plsBusy by vm.playlistBusy.collectAsState()
    val epgBusy by vm.epgBusy.collectAsState()
    val toast by vm.toast.collectAsState()
    val update by vm.update.collectAsState()
    val updateBusy by vm.updateBusy.collectAsState()
    val updateProgress by vm.updateProgress.collectAsState()

    val overlay by vm.overlay.collectAsState()
    var group by rememberSaveable { mutableStateOf(MainViewModel.ALL) }
    var infoTrigger by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf("") }
    var lastBack by remember { mutableLongStateOf(0L) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var rootFocused by remember { mutableStateOf(false) }
    var seekHudText by remember { mutableStateOf<String?>(null) }
    var seekHudForward by remember { mutableStateOf(true) }
    var seekHudTick by remember { mutableIntStateOf(0) }

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
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }

    fun showInfo() {
        infoVisible = true
        infoTrigger++
    }

    fun triggerSeek(deltaMs: Long) {
        if (vm.seekArchive(deltaMs) != null) {
            val sec = abs(deltaMs / 1000).toInt()
            seekHudForward = deltaMs >= 0
            seekHudText = if (deltaMs >= 0) {
                resources.getString(R.string.seek_forward_fmt, sec)
            } else {
                resources.getString(R.string.seek_backward_fmt, sec)
            }
            seekHudTick++
            showInfo()
        }
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
    LaunchedEffect(seekHudTick) {
        if (seekHudTick == 0) return@LaunchedEffect
        delay(1500)
        seekHudText = null
    }
    val shouldFocusRoot = overlay == Overlay.NONE && playback.error == null && !(loaded && channels.isEmpty())
    LaunchedEffect(shouldFocusRoot) {
        if (shouldFocusRoot) {
            delay(60)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(number) {
        if (number.isEmpty()) return@LaunchedEffect
        delay(1600)
        if (!vm.playNumber(number.toInt())) vm.show(resources.getString(R.string.channel_not_found, number))
        number = ""
    }

    fun onBack() {
        when {
            overlay != Overlay.NONE -> vm.closeOverlay()
            number.isNotEmpty() -> number = ""
            playback.archive != null -> vm.backToLive()
            infoVisible || controlsVisible -> {
                infoVisible = false
                controlsVisible = false
            }
            System.currentTimeMillis() - lastBack < 2500 -> onExit()
            else -> {
                lastBack = System.currentTimeMillis()
                vm.show(resources.getString(R.string.press_back_again))
            }
        }
    }

    BackHandler { onBack() }

    fun onKey(e: KeyEvent): Boolean {
        if (overlay != Overlay.NONE || e.type != KeyEventType.KeyDown) return false
        val emptyPlaylist = loaded && channels.isEmpty()
        if (!emptyPlaylist) {
            val digit = when (e.key) {
                Key.Zero, Key.NumPad0 -> 0
                Key.One, Key.NumPad1 -> 1
                Key.Two, Key.NumPad2 -> 2
                Key.Three, Key.NumPad3 -> 3
                Key.Four, Key.NumPad4 -> 4
                Key.Five, Key.NumPad5 -> 5
                Key.Six, Key.NumPad6 -> 6
                Key.Seven, Key.NumPad7 -> 7
                Key.Eight, Key.NumPad8 -> 8
                Key.Nine, Key.NumPad9 -> 9
                else -> -1
            }
            if (digit >= 0) {
                if (number.length < 4) number += digit
                return true
            }
        }
        when (e.key) {
            Key.ChannelUp, Key.PageUp -> vm.zapForward()
            Key.ChannelDown, Key.PageDown -> vm.zapBackward()
            Key.Guide, Key.TvDataService -> vm.setOverlay(if (isTv) Overlay.EPG else Overlay.GUIDE)
            Key.Menu, Key.Settings -> vm.setOverlay(Overlay.STREAM)
            Key.Info -> showInfo()
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                vm.togglePlayPause()
                if (playback.channel != null) showInfo()
            }
            Key.MediaRewind, Key.MediaSkipBackward ->
                if (playback.archive != null) triggerSeek(-30_000L) else return false
            Key.MediaFastForward, Key.MediaSkipForward ->
                if (playback.archive != null) triggerSeek(30_000L) else return false
            Key.ProgramRed -> playback.channel?.let { vm.toggleFavorite(it) }
            Key.ProgramGreen, Key.MediaNext -> vm.nextVariant()
            Key.ProgramBlue, Key.MediaPrevious -> vm.playPreviousChannel()
            Key.ProgramYellow -> vm.toggleStats()
            Key.DirectionUp -> if (rootFocused) vm.zapForward() else return false
            Key.DirectionDown -> if (rootFocused) vm.zapBackward() else return false
            Key.DirectionLeft -> if (rootFocused) {
                if (playback.archive != null) triggerSeek(-30_000L) else vm.setOverlay(Overlay.PLAYLIST)
            } else {
                return false
            }
            Key.DirectionRight -> if (rootFocused) {
                if (playback.archive != null) triggerSeek(30_000L) else vm.setOverlay(if (isTv) Overlay.EPG else Overlay.GUIDE)
            } else {
                return false
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (rootFocused) {
                vm.setOverlay(Overlay.PLAYLIST)
            } else {
                return false
            }
            else -> return false
        }
        return true
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onKeyEvent(::onKey),
    ) {
        val portrait = !inPip && !isTv && maxHeight > maxWidth
        val wide = !portrait && maxWidth >= 600.dp && (isTv || maxWidth >= 840.dp)
        val topInset = maxOf(
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
            if (portrait) AppSpace.md else 0.dp,
        )
        val portraitBar = 56.dp + topInset
        val videoArea = if (portrait) {
            Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.37f).padding(top = portraitBar)
        } else {
            Modifier.fillMaxSize()
        }

        val usePreview = isTv && wide && !inPip && overlay == Overlay.PLAYLIST && vm.settings.tvPreviewFirst
        if (!usePreview) video(resize, videoArea)
        if (inPip) return@BoxWithConstraints

        Box(videoArea) {
            // жесты телефона: касание — панель, свайп — переключение каналов
            Box(
                Modifier
                    .fillMaxSize()
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
                    },
            )

            PlaybackStatus(
                vm = vm,
                s = playback,
                isTv = isTv,
                compact = portrait,
                onChannels = { vm.setOverlay(Overlay.PLAYLIST) },
                modifier = Modifier.align(Alignment.Center).padding(horizontal = AppSpace.lg),
            )

            if (overlay == Overlay.NONE && playback.channel != null && playback.error == null && !playback.buffering) {
                val hudText = seekHudText
                when {
                    hudText != null -> SeekHud(
                        text = hudText,
                        forward = seekHudForward,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    playback.paused -> PauseHud(
                        isTv = isTv,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        if (loaded && channels.isEmpty() && overlay == Overlay.NONE) {
            Welcome(
                busy = plsBusy,
                onRefresh = { vm.refreshPlaylists() },
                onSettings = { vm.setOverlay(Overlay.SETTINGS) },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val showChannelButtons = !isTv && !portrait && controlsVisible && channels.size > 1
        val showInfoBar = !portrait && overlay == Overlay.NONE && playback.channel != null &&
            (infoVisible || showChannelButtons || (playback.archive != null && controlsVisible) || playback.paused)
        AnimatedVisibility(
            visible = showInfoBar,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isTv) Modifier
                    else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                ),
        ) {
            playback.channel?.let { InfoBar(vm, it, playback, now, isTv, wide, showChannelButtons, seekHudTick) }
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
                    .then(
                        if (isTv) {
                            Modifier.padding(horizontal = TvSafe.horizontal, vertical = TvSafe.vertical)
                        } else {
                            Modifier
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                .padding(top = topInset)
                        },
                    )
                    .then(if (portrait) Modifier.fillMaxWidth().heightIn(min = 56.dp) else if (!isTv) Modifier.padding(AppSpace.lg) else Modifier)
                    .padding(horizontal = if (portrait) AppSpace.lg else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(if (portrait) AppSpace.md else AppSpace.sm, Alignment.CenterHorizontally),
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
            TvGuide(
                vm = vm,
                now = now,
                active = playback.channel,
                isTv = isTv,
                group = group,
                onGroup = { group = it },
                onChannels = { vm.setOverlay(Overlay.PLAYLIST) },
                onHome = { vm.setOverlay(Overlay.HOME) },
            )
        }

        if (overlay == Overlay.PLAYLIST || (overlay == Overlay.EPG && !isTv)) browseState.SaveableStateProvider("browse") {
            PlaylistOverlay(
                vm = vm,
                isTv = isTv,
                wide = wide,
                now = now,
                group = group,
                onGroup = { group = it },
                focusEpg = overlay == Overlay.EPG,
                onClose = { vm.closeOverlay() },
                onSettings = { vm.setOverlay(Overlay.SETTINGS) },
                onHome = { vm.setOverlay(Overlay.HOME) },
                onGuide = if (isTv) ({ vm.setOverlay(Overlay.EPG) }) else null,
                videoPreview = if (usePreview) ({
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(minOf(maxHeight * 0.28f, 168.dp))
                            .clip(RoundedCornerShape(AppRadius.panel))
                            .background(Color.Black)
                            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.panel)),
                    ) {
                        video(resize, Modifier.fillMaxSize())
                        PlaybackStatus(
                            vm = vm,
                            s = playback,
                            isTv = true,
                            compact = true,
                            onChannels = {},
                            modifier = Modifier.align(Alignment.Center).padding(AppSpace.sm),
                        )
                    }
                }) else null,
            )
        }

        if (overlay == Overlay.HOME) {
            BrowseHome(
                vm = vm,
                now = now,
                isTv = isTv,
                onChannels = { vm.setOverlay(Overlay.PLAYLIST) },
                onGuide = { vm.setOverlay(if (isTv) Overlay.EPG else Overlay.GUIDE) },
                onSettings = { vm.setOverlay(Overlay.SETTINGS) },
                onClose = { vm.closeOverlay() },
            )
        }

        if (overlay == Overlay.GUIDE) {
            playback.channel?.let { ch ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(C.Panel)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(AppSpace.md),
                ) {
                    EpgPanel(
                        vm, ch, now, focusCurrent = true,
                        onPlayLive = { vm.backToLive(); vm.closeOverlay() },
                        onPlayArchive = { p -> vm.playArchive(ch, p); vm.closeOverlay() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (overlay == Overlay.STREAM) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (wide) Color.Black.copy(alpha = 0.4f) else Color.Transparent)
                    .pointerInput(Unit) { detectTapGestures { vm.closeOverlay() } },
            ) {
                StreamPanel(
                    vm = vm,
                    s = playback,
                    isTv = isTv,
                    wide = wide,
                    resize = resize,
                    onResize = { resize = it },
                    onSettings = { vm.setOverlay(Overlay.SETTINGS) },
                    onClose = { vm.closeOverlay() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .pointerInput(Unit) { detectTapGestures { /* absorb */ } },
                )
            }
        }

        if (overlay == Overlay.SETTINGS) SettingsScreen(vm, isTv, wide, onClose = { vm.closeOverlay() })

        if (portrait && overlay == Overlay.NONE && channels.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.63f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                PlaylistOverlay(
                    vm = vm,
                    isTv = false,
                    wide = false,
                    now = now,
                    group = group,
                    onGroup = { group = it },
                    focusEpg = false,
                    onClose = {},
                    onSettings = { vm.setOverlay(Overlay.SETTINGS) },
                    embedded = true,
                )
            }
        }

        if (number.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .then(
                        if (isTv) Modifier.padding(horizontal = TvSafe.horizontal, vertical = TvSafe.vertical)
                        else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpace.xl),
                    )
                    .clip(RoundedCornerShape(AppRadius.card))
                    .background(C.PanelSolid)
                    .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
                    .padding(horizontal = AppSpace.xl, vertical = AppSpace.md),
            ) { Text(number, color = C.Text, fontSize = AppType.hero, fontWeight = FontWeight.Bold) }
        }

        val busy = plsBusy ?: epgBusy
        if (busy != null && overlay == Overlay.NONE) {
            Row(
                Modifier
                    .align(if (portrait) Alignment.TopCenter else Alignment.TopStart)
                    .then(
                        when {
                            portrait -> Modifier.padding(top = portraitBar + AppSpace.sm, start = AppSpace.lg, end = AppSpace.lg)
                            isTv -> Modifier.padding(horizontal = TvSafe.horizontal, vertical = TvSafe.vertical)
                            else -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpace.lg)
                        },
                    )
                    .clip(RoundedCornerShape(50))
                    .background(C.PanelSolid)
                    .border(1.dp, C.Border, RoundedCornerShape(50))
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = C.Accent2, strokeWidth = 2.dp)
                Spacer(Modifier.width(AppSpace.sm))
                Text(busy, color = C.Text2, fontSize = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        val statsShown by vm.showStats.collectAsState()
        if (statsShown && playback.channel != null && overlay == Overlay.NONE) {
            val baseTop = if (portrait) portraitBar + AppSpace.sm else if (isTv) TvSafe.vertical else AppSpace.lg
            val statsTop = if (busy != null) baseTop + 44.dp else baseTop
            StatsOverlay(
                vm,
                playback,
                Modifier
                    .align(Alignment.TopStart)
                    .then(if (isTv || portrait) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                    .padding(
                        start = if (isTv) TvSafe.horizontal else AppSpace.lg,
                        top = statsTop,
                    ),
            )
        }

        update?.let { info ->
            UpdateDialog(
                update = info,
                busy = updateBusy,
                progress = updateProgress,
                onInstall = { vm.downloadAndInstallUpdate() },
                onDismiss = { if (updateBusy == null) vm.dismissUpdate() },
            )
        }

        ToastHost(
            toast = toast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    when {
                        portrait -> Modifier.padding(top = portraitBar + (if (busy != null) 48.dp else AppSpace.xs))
                        isTv -> Modifier.padding(top = TvSafe.vertical)
                        else -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    },
                ),
        )
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
        modifier = modifier,
    )
}

@Composable
private fun PauseHud(isTv: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(AppRadius.card))
            .background(C.PanelSolid)
            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
            .padding(horizontal = AppSpace.xl, vertical = AppSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.md),
    ) {
        Icon(painterResource(R.drawable.ic_pause), null, tint = C.Text, modifier = Modifier.size(28.dp))
        Column {
            Text(stringResource(R.string.paused_label), color = C.Text, fontSize = AppType.title, fontWeight = FontWeight.Bold)
            if (isTv) Text(stringResource(R.string.pause_hint_tv), color = C.Text3, fontSize = AppType.caption)
        }
    }
}

@Composable
private fun SeekHud(text: String, forward: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(AppRadius.card))
            .background(C.PanelSolid)
            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
            .padding(horizontal = AppSpace.xl, vertical = AppSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        Icon(
            painterResource(if (forward) R.drawable.ic_fast_forward else R.drawable.ic_rewind),
            null,
            tint = C.Accent2,
            modifier = Modifier.size(24.dp),
        )
        Text(text, color = C.Text, fontSize = AppType.title, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlaybackStatus(
    vm: MainViewModel,
    s: PlaybackState,
    isTv: Boolean,
    compact: Boolean,
    onChannels: () -> Unit,
    modifier: Modifier,
) {
    val channel = s.channel
    when {
        s.error != null -> {
            val retryFr = remember { FocusRequester() }
            LaunchedEffect(s.error) {
                delay(80)
                runCatching { retryFr.requestFocus() }
            }
            Column(
                modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(AppRadius.card))
                    .background(C.PanelSolid)
                    .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionUp -> { vm.zapForward(); true }
                            Key.DirectionDown -> { vm.zapBackward(); true }
                            else -> false
                        }
                    }
                    .padding(if (compact) AppSpace.lg else AppSpace.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    s.channel?.name ?: "",
                    color = C.Text,
                    fontSize = if (compact) AppType.subtitle else AppType.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.playback_failed, s.error.orEmpty()),
                    color = C.Text2,
                    fontSize = AppType.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = AppSpace.xs),
                )
                if (!compact) {
                    Text(
                        stringResource(R.string.playback_sources_checked, s.channel?.variants?.size ?: 0),
                        color = C.Text3,
                        fontSize = AppType.caption,
                        modifier = Modifier.padding(top = AppSpace.xs),
                    )
                }
                Row(Modifier.padding(top = AppSpace.md), horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                    FocusItem(
                        containerColor = C.Accent,
                        prominent = true,
                        liftOnFocus = true,
                        focusRequester = retryFr,
                        onClick = { vm.controller.retry() },
                    ) { _ ->
                        Text(
                            stringResource(R.string.action_retry),
                            color = C.OnAccent,
                            fontSize = AppType.body,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                        )
                    }
                    FocusItem(
                        containerColor = C.Card,
                        borderColor = C.Border,
                        liftOnFocus = true,
                        onClick = onChannels,
                    ) { _ ->
                        Text(
                            stringResource(R.string.nav_channels),
                            color = C.Text,
                            fontSize = AppType.body,
                            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                        )
                    }
                }
                if (!compact) {
                    Text(
                        if (isTv) stringResource(R.string.hint_tv_keys) else stringResource(R.string.hint_touch),
                        color = C.Text3,
                        fontSize = AppType.caption,
                        modifier = Modifier.padding(top = AppSpace.md),
                    )
                }
            }
        }
        s.buffering && channel != null -> Column(
            modifier
                .clip(RoundedCornerShape(AppRadius.card))
                .background(C.PanelSolid)
                .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), color = C.Accent2, strokeWidth = 3.dp)
            val source = channel.variants.getOrNull(s.variantIndex)?.sourceName
            Text(
                stringResource(R.string.connecting, source ?: channel.name, s.variantIndex + 1, channel.variants.size),
                color = C.Text,
                fontSize = AppType.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AppSpace.sm),
            )
        }
    }
}

@Composable
private fun InfoBar(
    vm: MainViewModel,
    ch: Channel,
    s: PlaybackState,
    now: Long,
    isTv: Boolean,
    wide: Boolean,
    showChannelButtons: Boolean,
    seekTick: Int,
) {
    val epgVersion by vm.epgVersion.collectAsState()
    val nn by produceState(vm.peekNowNext(ch, now).orEmpty(), ch.key, epgVersion, now / 60_000) {
        value = vm.nowNextCached(ch, now)
    }
    var archivePosMs by remember(s.archive, seekTick) {
        mutableLongStateOf(vm.controller.player.currentPosition)
    }
    LaunchedEffect(s.archive != null, seekTick) {
        if (s.archive == null) return@LaunchedEffect
        while (true) {
            archivePosMs = vm.controller.player.currentPosition
            delay(1000)
        }
    }
    val fav by vm.favorites.collectAsState()
    val cur = s.archive ?: nn.firstOrNull { it.isNow(now) }
    val next = if (s.archive == null) nn.firstOrNull { it.start >= (cur?.stop ?: now) } else null
    val variant = ch.variants.getOrNull(s.variantIndex)
    val source = variant?.let { "${it.sourceName}${if (ch.variants.size > 1) " ${s.variantIndex + 1}/${ch.variants.size}" else ""}" }

    Column(
        Modifier
            .fillMaxWidth()
            .background(C.bottomBrush)
            .padding(horizontal = if (isTv) TvSafe.horizontal else AppSpace.lg)
            .padding(
                top = if (wide) AppSpace.xxl else AppSpace.xl,
                bottom = if (isTv) TvSafe.vertical else if (showChannelButtons) AppSpace.md else AppSpace.xl,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(AppRadius.panel)).background(C.accentBrush).padding(horizontal = AppSpace.md, vertical = AppSpace.sm)) {
                Text(ch.number.toString(), color = C.OnAccent, fontSize = AppType.title, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(AppSpace.md))
            LogoImage(vm.logoOf(ch), ch.name, if (wide) 52.dp else 40.dp)
            Column(Modifier.weight(1f).padding(start = AppSpace.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                    Text(
                        ch.name,
                        color = C.Text,
                        fontSize = if (wide) AppType.display else AppType.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false),
                    )
                    if (ch.key in fav) Icon(Icons.Default.Star, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                    if (s.archive != null) {
                        Pill(stringResource(R.string.pill_archive), color = C.Blue, textColor = C.OnInfo)
                    } else {
                        Pill(stringResource(R.string.pill_live), color = C.LiveBg, textColor = C.OnLive)
                    }
                    if (wide && source != null) Pill(source, color = C.Card, textColor = C.Text2)
                }
                if (!wide && source != null) {
                    Text(source, color = C.Text2, fontSize = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val st by rememberStats(vm, 2000)
                st.summary().takeIf { it.isNotBlank() }?.let {
                    Text(it, color = C.Text3, fontSize = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = AppSpace.xs))
                }
                if (cur != null) {
                    Row(Modifier.padding(top = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text("${hhmm(cur.start)} – ${hhmm(cur.stop)}", color = C.Accent2, fontSize = AppType.subtitle, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(AppSpace.md))
                        Text(cur.title, color = C.Text, fontSize = AppType.subtitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val progress = if (s.archive != null) {
                        val dur = vm.controller.player.duration.takeIf { it > 0 }
                            ?: (cur.stop - cur.start).coerceAtLeast(1)
                        (archivePosMs.toFloat() / dur).coerceIn(0f, 1f)
                    } else {
                        cur.progress(now)
                    }
                    ProgressLine(progress, Modifier.fillMaxWidth(if (wide) 0.78f else 1f).padding(top = AppSpace.sm), height = 4.dp)
                } else {
                    Text(groupLabel(ch.group), color = C.Text3, fontSize = AppType.body, modifier = Modifier.padding(top = AppSpace.xs))
                }
                if (next != null) {
                    Text(
                        stringResource(R.string.next_programme, hhmm(next.start), next.title),
                        color = C.Text3,
                        fontSize = AppType.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = AppSpace.xs),
                    )
                }
                if (s.archive != null && !isTv) {
                    FocusItem(
                        containerColor = C.Card,
                        borderColor = C.Border,
                        shape = RoundedCornerShape(AppRadius.control),
                        modifier = Modifier.padding(top = AppSpace.sm),
                        onClick = vm::backToLive,
                    ) { _ ->
                        Text(
                            stringResource(R.string.action_back_to_live),
                            color = C.Accent2,
                            fontSize = AppType.caption,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.xs),
                        )
                    }
                }
            }
            if (wide) {
                Text(
                    hhmm(now),
                    color = C.Text,
                    fontSize = AppType.display,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = AppSpace.lg),
                )
            }
        }
        if (isTv) {
            val hints = if (s.archive != null) {
                listOf(
                    stringResource(R.string.hint_key_ok) to stringResource(R.string.hint_channels),
                    stringResource(R.string.hint_key_lr) to stringResource(R.string.hint_seek_30s),
                    stringResource(R.string.hint_key_menu) to stringResource(R.string.hint_stream),
                    stringResource(R.string.hint_key_back) to stringResource(R.string.hint_back_live),
                )
            } else {
                listOf(
                    stringResource(R.string.hint_key_ud) to stringResource(R.string.hint_switch_channel),
                    stringResource(R.string.hint_key_ok) to stringResource(R.string.hint_channels),
                    stringResource(R.string.hint_key_lr) to stringResource(R.string.hint_guide),
                    stringResource(R.string.hint_key_menu) to stringResource(R.string.hint_stream),
                )
            }
            RemoteHintBar(hints, Modifier.padding(top = AppSpace.md))
        }
        if (showChannelButtons) {
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(top = AppSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.lg),
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
    vm: MainViewModel,
    s: PlaybackState,
    isTv: Boolean,
    wide: Boolean,
    resize: Int,
    onResize: (Int) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val ch = s.channel
    val qualityRev by vm.qualityRevision.collectAsState()
    val fav by vm.favorites.collectAsState()
    val shown by vm.showStats.collectAsState()
    val qualities = remember(s.channel?.key, s.variantIndex, s.videoInfo, qualityRev) {
        vm.controller.qualities()
    }
    val fr = remember { FocusRequester() }
    val modes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to stringResource(R.string.image_fit),
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to stringResource(R.string.image_zoom),
        AspectRatioFrameLayout.RESIZE_MODE_FILL to stringResource(R.string.image_fill),
    )
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fr.requestFocus() }
    }

    val drawerShape = if (wide) RoundedCornerShape(AppRadius.card) else RoundedCornerShape(0.dp)
    val drawerContainer = if (wide) {
        modifier
            .then(
                if (isTv) Modifier.padding(end = TvSafe.horizontal, top = TvSafe.vertical, bottom = TvSafe.vertical)
                else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpace.lg),
            )
            .fillMaxHeight()
            .width(420.dp)
            .clip(drawerShape)
            .background(C.PanelSolid)
            .border(1.dp, C.Border, drawerShape)
            .padding(AppSpace.xl)
    } else {
        modifier
            .fillMaxSize()
            .background(C.PanelSolid)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(AppSpace.lg)
    }

    Column(drawerContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.stream_title),
                color = C.Text,
                fontSize = AppType.title,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (!isTv) {
                FocusItem(shape = CircleShape, modifier = Modifier.size(48.dp), onClick = onClose) { _ ->
                    Icon(
                        Icons.Default.Close,
                        stringResource(R.string.action_close),
                        tint = C.Text2,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(top = AppSpace.sm),
            contentPadding = PaddingValues(AppSpace.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpace.xs),
        ) {
            if (ch != null && ch.variants.size > 1) {
                item { Section(stringResource(R.string.section_source)) }
                items(ch.variants.size, key = { i -> ch.variants.getOrNull(i)?.url ?: i }) { i ->
                    val v = ch.variants[i]
                    val active = i == s.variantIndex
                    FocusItem(
                        Modifier.fillMaxWidth(),
                        selected = active,
                        containerColor = C.Card,
                        borderColor = C.Border,
                        shape = RoundedCornerShape(AppRadius.control),
                        focusRequester = if (active) fr else null,
                        onClick = { vm.selectVariant(ch, i); onClose() },
                    ) { _ ->
                        Row(
                            Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SelectedBar(active)
                            Spacer(Modifier.width(AppSpace.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${i + 1}. ${v.sourceName}",
                                    color = C.Text,
                                    fontSize = AppType.body,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val status = when (StreamChecker.status(v.url)) {
                                    StreamChecker.Health.PLAYING -> stringResource(R.string.stream_status_playing)
                                    StreamChecker.Health.REACHABLE -> stringResource(R.string.stream_status_reachable)
                                    StreamChecker.Health.UNAVAILABLE -> stringResource(R.string.stream_status_unavailable)
                                    null -> stringResource(R.string.stream_status_unknown)
                                }
                                Text("${variantInfo(v)} · $status", color = C.Text3, fontSize = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (active) {
                                Icon(Icons.Default.Check, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            item { Section(stringResource(R.string.section_quality)) }
            item {
                val autoSelected = remember(qualityRev, qualities) { vm.controller.isQualitySelected(null) }
                FocusItem(
                    Modifier.fillMaxWidth(),
                    selected = autoSelected,
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    focusRequester = if (ch == null || ch.variants.size < 2) fr else null,
                    onClick = { vm.selectQuality(null); onClose() },
                ) { _ ->
                    Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        SelectedBar(autoSelected)
                        Spacer(Modifier.width(AppSpace.sm))
                        Text(stringResource(R.string.quality_auto), color = C.Text, fontSize = AppType.body, modifier = Modifier.weight(1f))
                        if (autoSelected) Icon(Icons.Default.Check, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                    }
                }
            }
            items(qualities, key = { it.label }) { q ->
                val qSelected = remember(qualityRev, q) { vm.controller.isQualitySelected(q) }
                FocusItem(
                    Modifier.fillMaxWidth(),
                    selected = qSelected,
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = { vm.selectQuality(q); onClose() },
                ) { _ ->
                    Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        SelectedBar(qSelected)
                        Spacer(Modifier.width(AppSpace.sm))
                        Text(q.label, color = if (qSelected) C.Text else C.Text2, fontSize = AppType.body, modifier = Modifier.weight(1f))
                        if (qSelected) Icon(Icons.Default.Check, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Section(stringResource(R.string.section_image)) }
            items(modes, key = { it.first }) { (m, label) ->
                val selected = m == resize
                FocusItem(
                    Modifier.fillMaxWidth(),
                    selected = selected,
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = { onResize(m) },
                ) { _ ->
                    Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        SelectedBar(selected)
                        Spacer(Modifier.width(AppSpace.sm))
                        Text(label, color = if (selected) C.Text else C.Text2, fontSize = AppType.body, modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Default.Check, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Section(stringResource(R.string.section_other)) }
            if (ch != null) item {
                val isFav = ch.key in fav
                FocusItem(
                    Modifier.fillMaxWidth(),
                    selected = isFav,
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = { vm.toggleFavorite(ch) },
                ) { _ ->
                    Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = if (isFav) C.Accent2 else C.Text3, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpace.sm))
                        Text(
                            stringResource(if (isFav) R.string.action_favorite_remove else R.string.action_favorite_add),
                            color = C.Text,
                            fontSize = AppType.body,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                FocusItem(
                    Modifier.fillMaxWidth(),
                    selected = shown,
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = { vm.toggleStats() },
                ) { _ ->
                    Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (shown) stringResource(R.string.stats_toggle_hide) else stringResource(R.string.stats_toggle_show),
                            color = C.Text,
                            fontSize = AppType.body,
                            modifier = Modifier.weight(1f),
                        )
                        if (shown) Icon(Icons.Default.Check, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item {
                FocusItem(
                    Modifier.fillMaxWidth(),
                    containerColor = C.Card,
                    borderColor = C.Border,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = onSettings,
                ) { _ ->
                    Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = C.Text2, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpace.sm))
                        Text(stringResource(R.string.nav_settings), color = C.Text, fontSize = AppType.body)
                    }
                }
            }
            item { Section(stringResource(R.string.section_stream_info)) }
            item { StatsTable(vm, s) }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        color = C.Accent2,
        fontSize = AppType.caption,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = AppSpace.xs, top = AppSpace.md, bottom = AppSpace.xs),
    )
}

@Composable
private fun Welcome(busy: String?, onRefresh: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(busy == null) {
        if (busy == null) {
            delay(100)
            runCatching { fr.requestFocus() }
        }
    }
    Column(
        modifier
            .widthIn(max = 560.dp)
            .padding(AppSpace.xl)
            .clip(RoundedCornerShape(AppRadius.card))
            .background(C.PanelSolid)
            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
            .padding(AppSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(AppRadius.card))
                .background(C.Card)
                .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(64.dp),
            )
        }
        Text(
            stringResource(R.string.app_name),
            color = C.Text,
            fontSize = AppType.display,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = AppSpace.lg),
        )
        Text(
            stringResource(R.string.welcome_subtitle),
            color = C.Text3,
            fontSize = AppType.body,
            modifier = Modifier.padding(top = AppSpace.xs),
        )
        Spacer(Modifier.height(AppSpace.xl))
        if (busy != null) {
            CircularProgressIndicator(color = C.Accent2)
            Text(busy, color = C.Text2, fontSize = AppType.body, modifier = Modifier.padding(top = AppSpace.md))
        } else {
            Text(stringResource(R.string.channels_not_loaded), color = C.Text2, fontSize = AppType.body)
            Row(Modifier.padding(top = AppSpace.lg), horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                FocusItem(
                    containerColor = C.Accent,
                    prominent = true,
                    liftOnFocus = true,
                    focusRequester = fr,
                    onClick = onRefresh,
                ) { _ ->
                    Text(
                        stringResource(R.string.action_load_playlists),
                        color = C.OnAccent,
                        fontSize = AppType.body,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                    )
                }
                FocusItem(
                    containerColor = C.Card,
                    borderColor = C.Border,
                    liftOnFocus = true,
                    onClick = onSettings,
                ) { _ ->
                    Text(
                        stringResource(R.string.nav_settings),
                        color = C.Text,
                        fontSize = AppType.body,
                        modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FocusItem(
        Modifier.size(48.dp),
        containerColor = C.PanelSolid,
        borderColor = C.Border,
        liftOnFocus = true,
        shape = CircleShape,
        onClick = onClick,
    ) { _ ->
        Icon(icon, label, tint = C.Text, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun UpdateDialog(
    update: AppUpdate,
    busy: String?,
    progress: Float?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                Text(stringResource(R.string.update_available_text, update.version))
                if (busy != null) {
                    Text(busy, color = C.Text2, fontSize = AppType.body)
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = C.Accent2,
                            trackColor = C.Border,
                        )
                        Text(
                            stringResource(R.string.update_progress, (progress * 100).toInt()),
                            color = C.Text3,
                            fontSize = AppType.caption,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = busy == null) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = busy == null) {
                Text(stringResource(R.string.update_later))
            }
        },
        containerColor = C.PanelSolid,
        titleContentColor = C.Text,
        textContentColor = C.Text2,
    )
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
        Box(
            Modifier
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm)
                .clip(RoundedCornerShape(50))
                .background(C.PanelSolid)
                .border(1.dp, C.Border, RoundedCornerShape(50))
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm),
        ) {
            Text(visible?.text ?: "", color = C.Text, fontSize = AppType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    when (v.kind) {
        StreamKind.WINK -> stringResource(R.string.variant_wink)
        StreamKind.BEELINE -> stringResource(R.string.variant_beeline)
        else -> null
    },
    when {
        v.url.contains(".mpd", ignoreCase = true) -> stringResource(R.string.variant_dash)
        v.url.contains("m3u8", ignoreCase = true) -> stringResource(R.string.variant_hls)
        else -> null
    },
    v.clearKey?.let { stringResource(R.string.variant_clearkey) },
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
        stringResource(R.string.stats_source) to (if (v != null) stringResource(R.string.stats_source_value, v.sourceName, s.variantIndex + 1, ch.variants.size) else stringResource(R.string.stats_empty)),
        stringResource(R.string.stats_video) to listOfNotNull(st.videoCodec, st.resolution, st.frameRate?.let { stringResource(R.string.stats_fps, fmtFps(it)) }, st.hdr).joinToString(" • ").ifEmpty { stringResource(R.string.stats_empty) },
        st.videoProfile?.let { stringResource(R.string.stats_profile) to it },
        stringResource(R.string.stats_video_bitrate) to fmtRate(st.videoBitrate?.toLong()),
        st.videoDecoder?.let { stringResource(R.string.stats_video_decoder) to "$it ($hwVideo)" },
        stringResource(R.string.stats_frames) to stringResource(
            R.string.stats_frames_value,
            st.renderFps?.let { fmtFps((it * 10).toInt() / 10f) } ?: stringResource(R.string.stats_empty),
            st.droppedFrames,
        ),
        stringResource(R.string.stats_audio) to listOfNotNull(
            st.audioCodec,
            st.audioChannels?.let { channelsLabel(it) },
            st.sampleRate?.let { stringResource(R.string.stats_sample_rate, (it / 1000.0).toString().removeSuffix(".0")) },
            st.audioLang,
        ).joinToString(" • ").ifEmpty { stringResource(R.string.stats_empty) },
        stringResource(R.string.stats_audio_bitrate) to fmtRate(st.audioBitrate?.toLong()),
        st.audioDecoder?.let { stringResource(R.string.stats_audio_decoder) to "$it ($hwAudio)" },
        stringResource(R.string.stats_network) to stringResource(R.string.stats_network_value, fmtRate(st.bandwidth), fmtRate(st.throughput)),
        stringResource(R.string.stats_buffer) to (
            st.liveOffsetSec?.let { offset ->
                stringResource(R.string.stats_buffer_offset, "%.1f".format(java.util.Locale.US, st.bufferSec), "%.0f".format(java.util.Locale.US, offset))
            } ?: stringResource(R.string.stats_buffer_value, "%.1f".format(java.util.Locale.US, st.bufferSec))
            ),
        stringResource(R.string.stats_loaded) to stringResource(R.string.stats_loaded_value, "%.1f".format(java.util.Locale.US, st.bytesLoaded / 1_048_576.0)),
        stringResource(R.string.stats_user_agent) to (s.userAgent ?: stringResource(R.string.stats_empty)),
    )
}

@Composable
private fun StatsTable(vm: MainViewModel, s: PlaybackState) {
    val st by rememberStats(vm, 1000)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.panel))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.panel))
            .padding(AppSpace.md),
    ) {
        statsRows(st, s).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(k, color = C.Text3, fontSize = AppType.caption, modifier = Modifier.width(118.dp))
                Text(v, color = C.Text2, fontSize = AppType.caption, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatsOverlay(vm: MainViewModel, s: PlaybackState, modifier: Modifier) {
    val st by rememberStats(vm, 1000)
    Column(
        modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(AppRadius.panel))
            .background(C.Panel)
            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.panel))
            .padding(AppSpace.md),
    ) {
        val userAgentLabel = stringResource(R.string.stats_user_agent)
        val profileLabel = stringResource(R.string.stats_profile)
        statsRows(st, s).filter { it.first != userAgentLabel && it.first != profileLabel }.forEach { (k, v) ->
            Text("$k: $v", color = C.Green, fontSize = AppType.caption, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
