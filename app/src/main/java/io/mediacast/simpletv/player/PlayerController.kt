package io.mediacast.simpletv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.Channel
import io.mediacast.simpletv.data.Programme
import io.mediacast.simpletv.data.Settings
import io.mediacast.simpletv.data.StreamChecker
import io.mediacast.simpletv.data.StreamResolver
import io.mediacast.simpletv.data.StreamVariant
import io.mediacast.simpletv.data.PlaybackState
import io.mediacast.simpletv.data.Quality
import io.mediacast.simpletv.data.PublicTv

@OptIn(UnstableApi::class)
class PlayerController(
    context: Context,
    private val settings: Settings,
) {
    private val appContext = context.applicationContext

    val player: ExoPlayer
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var forceHls = false
    private val playbackScope = MainScope()
    private var resolution: Job? = null
    private var renderedFrame = false
    private var confirmedPlayback = false
    private val failedVariants = HashSet<Int>()
    var onMessage: ((String) -> Unit)? = null
    var onStreamSuccess: ((Channel, Int) -> Unit)? = null
    val stats: StatsCollector

    // Сторож: быстрая реакция на зависание (3.5 с) или застывшую картинку → переход на следующий источник
    private val watchdog = android.os.Handler(android.os.Looper.getMainLooper())
    private var bufferingSince = 0L
    private var lastPos = -1L
    private var frozenSince = 0L
    private var watchdogRetries = 0
    private val tick = object : Runnable {
        override fun run() {
            checkStall()
            watchdog.postDelayed(this, 1000)
        }
    }

    init {
        val buffer = settings.bufferMs.coerceIn(1000, 30000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(maxOf(15000, buffer * 3), 60000, buffer.coerceAtMost(2500), buffer)
            .build()
        // ffmpeg (AC-3, E-AC-3, DTS, MP2…) подключается после системных декодеров — только если устройство само не умеет
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        player = ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING, error = if (playbackState == Player.STATE_READY) null else it.error) }
                if (playbackState == Player.STATE_READY && player.isPlaying) {
                    checkAndNotifySuccess()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying && player.playbackState == Player.STATE_READY) {
                    checkAndNotifySuccess()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _state.update { it.copy(paused = !playWhenReady) }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0) {
                    _state.update { it.copy(videoInfo = "${videoSize.width}×${videoSize.height}") }
                }
            }

            override fun onRenderedFirstFrame() {
                renderedFrame = true
                checkAndNotifySuccess()
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
        stats = StatsCollector(player)
        player.addAnalyticsListener(stats)
        watchdog.postDelayed(tick, 1000)
    }

    private fun checkAndNotifySuccess() {
        val s = _state.value
        val ch = s.channel ?: return
        if (confirmedPlayback || s.archive != null || !player.isPlaying) return
        val variant = ch.variants.getOrNull(s.variantIndex) ?: return
        val tracks = player.currentTracks
        val audioOnly = tracks.isTypeSelected(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        if (!renderedFrame && !audioOnly) return
        confirmedPlayback = true
        StreamChecker.recordPlayback(variant.url, success = true)
        onStreamSuccess?.invoke(ch, s.variantIndex)
    }

    private fun nextAfterFailure(s: PlaybackState, ch: Channel): Int? {
        failedVariants += s.variantIndex
        if (s.archive == null) {
            ch.variants.getOrNull(s.variantIndex)?.let { StreamChecker.recordPlayback(it.url, success = false) }
        }
        val candidates = (1 until ch.variants.size)
            .map { (s.variantIndex + it) % ch.variants.size }
            .filterNot { it in failedVariants }
            .filter { index -> s.archive?.let {
                StreamResolver.catchupUrl(ch.variants[index], it.start, it.stop) != null
            } ?: true }
        return candidates.firstOrNull { !StreamChecker.isUnavailable(ch.variants[it].url) }
            ?: candidates.firstOrNull()
    }

    private fun checkStall() {
        val s = _state.value
        if (s.channel == null || !player.playWhenReady || s.error != null) { bufferingSince = 0; frozenSince = 0; return }
        val now = android.os.SystemClock.elapsedRealtime()
        when (player.playbackState) {
            Player.STATE_BUFFERING -> {
                frozenSince = 0
                val source = s.channel?.variants?.getOrNull(s.variantIndex)?.sourceId
                val slowStart = !confirmedPlayback && source in publicCatalogSources
                val timeout = if (slowStart) 12_000L else 3_500L
                if (bufferingSince == 0L) bufferingSince = now
                else if (now - bufferingSince > timeout) {
                    bufferingSince = 0
                    stalled(appContext.getString(if (slowStart) R.string.watchdog_slow_start else R.string.watchdog_slow_stream))
                }
            }
            Player.STATE_READY -> {
                bufferingSince = 0
                val pos = player.currentPosition
                if (pos == lastPos && player.isPlaying) {
                    if (frozenSince == 0L) frozenSince = now
                    else if (now - frozenSince > 5_000) { frozenSince = 0; stalled(appContext.getString(R.string.watchdog_frozen_frame)) }
                } else { frozenSince = 0; watchdogRetries = 0 }
                lastPos = pos
            }
            else -> { bufferingSince = 0; frozenSince = 0 }
        }
    }

    private fun stalled(reason: String) {
        val s = _state.value
        val ch = s.channel ?: return
        val next = nextAfterFailure(s, ch)
        if (next != null) {
            watchdogRetries = 0
            forceHls = false
            onMessage?.invoke(
                appContext.getString(
                    R.string.watchdog_switch,
                    reason,
                    ch.variants[next].sourceName,
                    next + 1,
                    ch.variants.size,
                ),
            )
            start(ch, next, s.archive)
        } else if (watchdogRetries == 0) {
            watchdogRetries++
            onMessage?.invoke(appContext.getString(R.string.watchdog_restart, reason))
            start(ch, s.variantIndex, s.archive)
        } else {
            watchdogRetries = 0
            _state.update { it.copy(buffering = false, error = reason) }
        }
    }

    fun play(channel: Channel, variantIndex: Int = 0, archive: Programme? = null) {
        forceHls = false
        failedVariants.clear()
        watchdogRetries = 0
        start(channel, variantIndex.coerceIn(0, (channel.variants.size - 1).coerceAtLeast(0)), archive)
    }

    private fun start(channel: Channel, index: Int, archive: Programme?) {
        val v = channel.variants.getOrNull(index) ?: return
        val url = if (archive != null) StreamResolver.catchupUrl(v, archive.start, archive.stop) else v.url
        if (url == null) {
            onMessage?.invoke(appContext.getString(R.string.archive_unavailable_source, v.sourceName))
            return
        }
        resolution?.cancel()
        resolution = null
        val needsResolution = PublicTv.needsResolution(url)
        if (needsResolution) player.stop()
        val ua = v.userAgent ?: settings.globalUserAgent
        _state.value = PlaybackState(channel, index, archive, buffering = true, paused = false, userAgent = ua)
        stats.reset()
        confirmedPlayback = false
        renderedFrame = false
        bufferingSince = 0; frozenSince = 0; lastPos = -1
        if (!needsResolution) {
            prepare(v, url, ua)
            return
        }
        resolution = playbackScope.launch {
            val resolved = withContext(Dispatchers.IO) { runCatching { PublicTv.resolve(url) }.getOrNull() }
            resolution = null
            if (resolved != null) {
                prepare(v, resolved, ua)
            } else if (!switchAfterFailure(_state.value, channel)) {
                _state.update { it.copy(buffering = false, error = appContext.getString(R.string.error_no_connection)) }
            }
        }
    }

    private fun prepare(v: StreamVariant, url: String, ua: String) {
        val headers = LinkedHashMap<String, String>()
        headers["User-Agent"] = ua
        v.referer?.let { headers["Referer"] = it }
        headers.putAll(v.headers)
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(headers)

        val mime = when {
            url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            url.contains(".m3u8", true) || url.contains("m3u8", true) || forceHls -> MimeTypes.APPLICATION_M3U8
            else -> null
        }
        stats.protocol = when (mime) {
            MimeTypes.APPLICATION_MPD -> "DASH"
            MimeTypes.APPLICATION_M3U8 -> "HLS"
            else -> if (url.substringBefore('?').substringBefore('#').endsWith(".ts", ignoreCase = true)) "MPEG-TS" else appContext.getString(R.string.protocol_progressive)
        }
        stats.drm = if (v.clearKey != null) "ClearKey CENC" else null
        val item = MediaItem.Builder().setUri(url).setMimeType(mime).apply {
            if (v.clearKey != null) setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build())
        }.build()
        val factory = DefaultMediaSourceFactory(http)
        v.clearKey?.let { key ->
            factory.setDrmSessionManagerProvider {
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(ClearKeyCallback(key))
            }
        }
        player.setMediaSource(factory.createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
    }

    private fun handleError(error: PlaybackException) {
        if (resolution != null) return
        val s = _state.value
        val ch = s.channel ?: return
        val cause = error.cause
        if (cause is BehindLiveWindowException || error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition(); player.prepare(); return
        }
        val v = ch.variants.getOrNull(s.variantIndex)
        // Ссылки без расширения часто оказываются HLS
        if (!forceHls && v != null && !v.url.contains(".mpd", ignoreCase = true) && !v.url.contains("m3u8", ignoreCase = true) &&
            (cause is UnrecognizedInputFormatException || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        ) {
            forceHls = true
            start(ch, s.variantIndex, s.archive)
            return
        }
        // TVSources: при ошибке переходим к следующему источнику этого же канала
        if (!switchAfterFailure(s, ch)) _state.update { it.copy(buffering = false, error = describe(error)) }
    }

    private fun switchAfterFailure(s: PlaybackState, ch: Channel): Boolean {
        val next = nextAfterFailure(s, ch) ?: return false
        onMessage?.invoke(appContext.getString(R.string.stream_error_switch, ch.variants[next].sourceName, next + 1, ch.variants.size))
        forceHls = false
        start(ch, next, s.archive)
        return true
    }

    private fun describe(e: PlaybackException): String {
        val http = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        return when {
            http != null -> appContext.getString(R.string.error_http_code, http.responseCode)
            e.errorCode in 2000..2999 -> appContext.getString(R.string.error_no_connection)
            e.errorCode in 6000..6999 -> appContext.getString(R.string.error_drm)
            e.errorCode in 4000..4999 -> appContext.getString(R.string.error_format_unsupported)
            else -> e.errorCodeName.removePrefix("ERROR_CODE_").lowercase(java.util.Locale.ROOT).replace('_', ' ')
        }
    }

    fun retry() {
        val s = _state.value
        s.channel?.let { play(it, s.variantIndex, s.archive) }
    }

    fun qualities(): List<Quality> {
        val out = ArrayList<Quality>()
        player.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                if (f.height <= 0) continue
                val br = if (f.bitrate > 0) " • ${appContext.getString(R.string.quality_kbps, f.bitrate / 1000)}" else ""
                out += Quality("${f.height}p$br", f.height, gi, ti)
            }
        }
        return out.distinctBy { it.label }.sortedByDescending { it.height }
    }

    fun setQuality(q: Quality?) {
        val params = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (q != null) {
            val group: Tracks.Group = player.currentTracks.groups.getOrNull(q.groupIndex) ?: return
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, q.trackIndex))
        }
        player.trackSelectionParameters = params.build()
    }

    fun isQualitySelected(q: Quality?): Boolean {
        val videoOverride = player.trackSelectionParameters.overrides.values
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        if (q == null) return videoOverride == null
        val group = player.currentTracks.groups.getOrNull(q.groupIndex) ?: return false
        return videoOverride != null &&
            videoOverride.mediaTrackGroup == group.mediaTrackGroup &&
            q.trackIndex in videoOverride.trackIndices
    }

    fun seekBy(deltaMs: Long): Long? {
        val s = _state.value
        val archiveDur = s.archive?.let { (it.stop - it.start).takeIf { d -> d > 0 } }
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: archiveDur ?: return null
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        return target
    }

    fun release() {
        playbackScope.cancel()
        watchdog.removeCallbacks(tick)
        player.release()
    }

    private companion object {
        val publicCatalogSources = setOf("cinerama", "sputnik24", "vitrina")
    }
}
