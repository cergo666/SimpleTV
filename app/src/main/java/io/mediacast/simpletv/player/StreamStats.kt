package io.mediacast.simpletv.player

import android.media.MediaCodecList
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import io.mediacast.simpletv.data.StreamStats
import io.mediacast.simpletv.data.fmtFps
import io.mediacast.simpletv.data.fmtRate
import io.mediacast.simpletv.data.channelsLabel

@OptIn(UnstableApi::class)
class StatsCollector(private val player: ExoPlayer) : AnalyticsListener {
    private var videoDecoder: String? = null
    private var audioDecoder: String? = null
    private var dropped = 0
    private var bandwidth: Long? = null
    private var bytes = 0L
    private var lastBytes = 0L
    private var lastBytesTime = 0L
    private var throughput: Long? = null
    private var lastRendered = 0
    private var lastRenderTime = 0L
    private var renderFps: Float? = null
    var protocol = "—"
    var drm: String? = null

    fun reset() {
        videoDecoder = null; audioDecoder = null; dropped = 0; bytes = 0; lastBytes = 0
        throughput = null; renderFps = null; lastRendered = 0; lastRenderTime = 0
    }

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        videoDecoder = decoderName
    }

    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        audioDecoder = decoderName
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        dropped += droppedFrames
    }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        bandwidth = bitrateEstimate
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        bytes += loadEventInfo.bytesLoaded
    }

    @Synchronized
    fun snapshot(): StreamStats {
        val now = SystemClock.elapsedRealtime()
        if (lastBytesTime > 0 && now - lastBytesTime >= 5000) { // сегменты приходят пачками — усредняем за 5 с
            throughput = (bytes - lastBytes) * 8_000 / (now - lastBytesTime)
            lastBytes = bytes; lastBytesTime = now
        } else if (lastBytesTime == 0L) { lastBytesTime = now; lastBytes = bytes }

        val counters = player.videoDecoderCounters
        if (counters != null) {
            counters.ensureUpdated()
            val rendered = counters.renderedOutputBufferCount
            if (lastRenderTime > 0 && now - lastRenderTime >= 1000 && rendered >= lastRendered) {
                renderFps = (rendered - lastRendered) * 1000f / (now - lastRenderTime)
            }
            if (lastRenderTime == 0L || now - lastRenderTime >= 1000) { lastRendered = rendered; lastRenderTime = now }
        }

        val v: Format? = player.videoFormat
        val a: Format? = player.audioFormat
        val live = if (player.isCurrentMediaItemLive) player.currentLiveOffset.takeIf { it != C.TIME_UNSET }?.div(1000f) else null
        return StreamStats(
            protocol = protocol,
            drm = drm,
            videoCodec = v?.let { codecName(it.sampleMimeType, it.codecs) },
            videoProfile = v?.codecs,
            resolution = v?.takeIf { it.width > 0 }?.let { "${it.width}×${it.height}" },
            frameRate = v?.frameRate?.takeIf { it > 0 },
            renderFps = renderFps,
            videoBitrate = v?.let { bitrate(it) },
            hdr = v?.colorInfo?.let { hdrName(it.colorTransfer) },
            videoDecoder = videoDecoder,
            videoHw = videoDecoder?.let { isHardware(it) },
            droppedFrames = dropped,
            audioCodec = a?.let { codecName(it.sampleMimeType, it.codecs) },
            audioChannels = a?.channelCount?.takeIf { it > 0 },
            sampleRate = a?.sampleRate?.takeIf { it > 0 },
            audioBitrate = a?.let { bitrate(it) },
            audioLang = a?.language,
            audioDecoder = audioDecoder,
            audioHw = audioDecoder?.let { isHardware(it) },
            bandwidth = bandwidth,
            throughput = throughput,
            bufferSec = (player.totalBufferedDuration / 1000f).coerceAtLeast(0f),
            liveOffsetSec = live,
            bytesLoaded = bytes,
        )
    }

    private fun bitrate(f: Format): Int? = listOf(f.averageBitrate, f.bitrate, f.peakBitrate).firstOrNull { it > 0 }

    companion object {
        private val hwCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

        /** Аппаратный ли декодер: на Android 10+ спрашиваем систему, иначе — по имени. */
        fun isHardware(name: String): Boolean = hwCache.getOrPut(name) {
            val lower = name.lowercase(java.util.Locale.ROOT)
            if ("ffmpeg" in lower || "libgav1" in lower || "libvpx" in lower || "libopus" in lower) return@getOrPut false
            if (Build.VERSION.SDK_INT >= 29) {
                runCatching {
                    MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name }?.isHardwareAccelerated
                }.getOrNull()?.let { return@getOrPut it }
            }
            !(lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || ".sw." in lower || lower.endsWith(".sw"))
        }

        fun codecName(mime: String?, codecs: String?): String? = when (mime) {
            MimeTypes.VIDEO_H264 -> "H.264"
            MimeTypes.VIDEO_H265 -> "HEVC"
            MimeTypes.VIDEO_AV1 -> "AV1"
            MimeTypes.VIDEO_VP9 -> "VP9"
            MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
            MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
            MimeTypes.AUDIO_AAC -> if (codecs?.contains("mp4a.40.5") == true || codecs?.contains("mp4a.40.29") == true) "HE-AAC" else "AAC"
            MimeTypes.AUDIO_AC3 -> "AC-3"
            MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_MPEG_L2 -> "MP2"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
            null -> null
            else -> mime.substringAfter('/').uppercase(java.util.Locale.ROOT)
        }

        fun hdrName(transfer: Int): String? = when (transfer) {
            C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> null
        }
    }
}
