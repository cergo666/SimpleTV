package io.mediacast.simpletv.data

/** Как поток обрабатывается перед воспроизведением (аналог видеоскриптов SimpleTV). */
enum class StreamKind { GENERIC, WINK, BEELINE }

/** Параметры архива из тегов catchup / catchup-days / catchup-source. */
data class Catchup(
    // append, default, shift, flussonic, wink, beeline
    val type: String,
    // глубина архива в днях
    val days: Double,
    // шаблон catchup-source
    val source: String? = null,
)

/**
 * Один вариант потока канала. Один и тот же канал может прийти из нескольких источников
 * (Wink, Beeline, Помойка+) — как в TVSources, при ошибке переключаемся на следующий вариант.
 */
data class StreamVariant(
    val url: String,
    val sourceId: String,
    val sourceName: String,
    val kind: StreamKind = StreamKind.GENERIC,
    /** UA для потока; null — глобальный UA из настроек (как http-user-agent в vlcrc). */
    val userAgent: String? = null,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    /** Ключ ClearKey (hex) для DASH CENC — аналог avdemux-options={decryption_key=…}. */
    val clearKey: String? = null,
    val catchup: Catchup? = null,
)

data class Channel(
    // нормализованное имя (NameKey в tvs.db)
    val key: String,
    val name: String,
    val group: String,
    val logo: String?,
    val tvgId: String?,
    val tvgName: String?,
    val variants: List<StreamVariant>,
    val number: Int = 0,
) {
    val hasArchive: Boolean get() = variants.any { it.catchup != null && it.catchup.days > 0 }
}

/** Канал, как он записан в одном источнике (до объединения). */
data class SourceChannel(
    val name: String,
    val group: String,
    val logo: String?,
    val tvgId: String?,
    val tvgName: String?,
    val variant: StreamVariant,
)

data class Programme(
    // unix ms
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String?,
    val category: String?,
) {
    fun isNow(now: Long) = now in start until stop

    fun progress(now: Long): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

data class PlaybackState(
    val channel: Channel? = null,
    val variantIndex: Int = 0,
    val archive: Programme? = null,
    val buffering: Boolean = false,
    val isPlaying: Boolean = true,
    val error: String? = null,
    val videoInfo: String? = null,
    val userAgent: String? = null,
)

data class Quality(val label: String, val height: Int, val groupIndex: Int, val trackIndex: Int)

data class StreamStats(
    val protocol: String = "—",
    val drm: String? = null,
    val videoCodec: String? = null,
    val videoProfile: String? = null,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val renderFps: Float? = null,
    val videoBitrate: Int? = null,
    val hdr: String? = null,
    val videoDecoder: String? = null,
    val videoHw: Boolean? = null,
    val droppedFrames: Int = 0,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val sampleRate: Int? = null,
    val audioBitrate: Int? = null,
    val audioLang: String? = null,
    val audioDecoder: String? = null,
    val audioHw: Boolean? = null,
    val bandwidth: Long? = null,
    val throughput: Long? = null,
    val bufferSec: Float = 0f,
    val liveOffsetSec: Float? = null,
    val bytesLoaded: Long = 0,
) {
    fun summary(): String =
        listOfNotNull(
            resolution,
            videoCodec,
            frameRate?.takeIf { it > 0 }?.let { "${fmtFps(it)} fps" },
            audioCodec?.let { a -> a + (audioChannels?.let { " " + channelsLabel(it) } ?: "") },
            videoHw?.let { if (it) "HW" else "SW" },
        ).joinToString(" · ")
}

fun fmtFps(f: Float): String = if (f % 1f == 0f) f.toInt().toString() else "%.2f".format(java.util.Locale.US, f).trimEnd('0').trimEnd('.')

fun fmtRate(bps: Long?): String =
    when {
        bps == null || bps <= 0 -> "—"
        bps >= 1_000_000 -> Messages[Messages.Key.RATE_MBPS, bps / 1_000_000.0]
        else -> Messages[Messages.Key.RATE_KBPS, bps / 1000]
    }

fun channelsLabel(n: Int): String =
    when (n) {
        1 -> Messages[Messages.Key.CHANNELS_MONO]
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${n}ch"
    }
