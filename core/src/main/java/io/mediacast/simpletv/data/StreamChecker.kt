package io.mediacast.simpletv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

object StreamChecker {
    enum class Health { REACHABLE, UNAVAILABLE, PLAYING }

    private class Entry(val health: Health, val expiresAt: Long)

    /** Results are cached so probing is not repeated on every channel switch, but never forever. */
    private const val PLAYING_TTL_MS = 30 * 60 * 1000L
    private const val REACHABLE_TTL_MS = 15 * 60 * 1000L
    private const val UNAVAILABLE_TTL_MS = 5 * 60 * 1000L
    private const val TRANSIENT_FAILURE_TTL_MS = 45 * 1000L

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Overridable in tests to avoid sleeping. */
    internal var clock: () -> Long = System::currentTimeMillis

    fun status(url: String): Health? {
        val entry = entries[url] ?: return null
        if (entry.expiresAt <= clock()) {
            entries.remove(url, entry)
            return null
        }
        return entry.health
    }

    fun isUnavailable(url: String): Boolean = status(url) == Health.UNAVAILABLE

    fun recordPlayback(
        url: String,
        success: Boolean,
    ) {
        val ttl = if (success) PLAYING_TTL_MS else UNAVAILABLE_TTL_MS
        entries[url] = Entry(if (success) Health.PLAYING else Health.UNAVAILABLE, clock() + ttl)
    }

    /** Drops cache entries for URLs that are no longer part of any playlist. */
    fun retainOnly(urls: Set<String>) {
        entries.keys.retainAll(urls)
    }

    fun probeUrl(
        variant: StreamVariant,
        defaultUserAgent: String,
    ) {
        if (status(variant.url) != null) return
        if (PublicTv.needsResolution(variant.url)) return
        val uri = runCatching { URI(variant.url) }.getOrNull() ?: return
        if (uri.scheme !in setOf("http", "https")) return
        val headers =
            buildMap {
                variant.referer?.let { put("Referer", it) }
                putAll(variant.headers)
            }
        val userAgent = variant.userAgent ?: defaultUserAgent
        val (result, ttl) =
            try {
                checkResources(uri, userAgent, headers)
                Health.REACHABLE to REACHABLE_TTL_MS
            } catch (_: SocketTimeoutException) {
                // A timeout says nothing about the stream itself: retry soon instead of hiding it.
                Health.UNAVAILABLE to TRANSIENT_FAILURE_TTL_MS
            } catch (_: IOException) {
                Health.UNAVAILABLE to UNAVAILABLE_TTL_MS
            } catch (_: RuntimeException) {
                Health.UNAVAILABLE to UNAVAILABLE_TTL_MS
            }
        entries.putIfAbsent(variant.url, Entry(result, clock() + ttl))
    }

    private val mediaPlaylist = Regex("""^#EXT-X-STREAM-INF:[^\r\n]*\r?\n([^#\r\n][^\r\n]*)""", RegexOption.MULTILINE)
    private val keyTag = Regex("""^#EXT-X-KEY:([^\r\n]*)""", RegexOption.MULTILINE)
    private val aes128 = Regex("""(?:^|,)METHOD=AES-128(?:,|$)""")
    private val keyFormat = Regex("""(?:^|,)KEYFORMAT="([^"]+)"(?=,|$)""")
    private val keyUri = Regex("""(?:^|,)URI="([^"]+)"(?=,|$)""")

    private fun checkResources(
        uri: URI,
        userAgent: String,
        headers: Map<String, String>,
    ) {
        var (base, bytes) = fetch(uri, userAgent, headers, playlist = true)
        var text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF').trimStart()
        if (!text.startsWith("#EXTM3U")) return
        val child = mediaPlaylist.find(text)?.groupValues?.get(1)
        if (child != null) {
            val media = fetch(base.resolve(child.trim()), userAgent, headers, playlist = true)
            base = media.first
            bytes = media.second
            text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF').trimStart()
            if (!text.startsWith("#EXTM3U")) throw IOException("Invalid HLS playlist")
        }
        val key = keyTag.findAll(text).lastOrNull()?.groupValues?.get(1) ?: return
        if (!aes128.containsMatchIn(key)) return
        val format = keyFormat.find(key)?.groupValues?.get(1)
        if (format != null && format != "identity") return
        val url = keyUri.find(key)?.groupValues?.get(1) ?: throw IOException("Missing HLS key URI")
        val keyAddress = base.resolve(url)
        if (keyAddress.scheme == "data") return
        val (_, keyBytes) = fetch(keyAddress, userAgent, headers, playlist = false)
        if (keyBytes.size != 16) throw IOException("Invalid HLS key length")
    }

    private fun fetch(
        uri: URI,
        userAgent: String,
        headers: Map<String, String>,
        playlist: Boolean,
    ): Pair<URI, ByteArray> {
        if (uri.scheme !in setOf("http", "https")) throw IOException("Unsupported stream protocol")
        return Http.open(uri.toString(), userAgent, headers, connectTimeoutMs = 2500, readTimeoutMs = 2500).use { response ->
            val body = response.body
            if (!response.ok || body == null || response.contentType?.startsWith("text/html", ignoreCase = true) == true) {
                throw IOException("Stream request failed")
            }
            val prefix = body.readLimited(if (playlist) 16 else 17)
            if (prefix.isEmpty()) throw IOException("Empty stream response")
            val text = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF').trimStart()
            if (text.startsWith("<html", true) || text.startsWith("<!doctype html", true)) throw IOException("HTML response")
            val bytes = if (playlist && text.startsWith("#EXTM3U")) prefix + body.readLimited(65536 - prefix.size) else prefix
            URI(response.url) to bytes
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val bytes = ByteArray(limit)
        var count = 0
        while (count < limit) {
            val read = read(bytes, count, limit - count)
            if (read < 0) break
            count += read
        }
        return bytes.copyOf(count)
    }

    suspend fun probeChannels(
        channels: List<Channel>,
        defaultUserAgent: String,
        maxConcurrency: Int = 12,
    ) = withContext(Dispatchers.IO) {
        val variants = channels.flatMap { it.variants }.distinctBy { it.url }
        retainOnly(variants.mapTo(HashSet()) { it.url })
        val semaphore = Semaphore(maxConcurrency)
        variants.filter { status(it.url) == null }
            .map { variant -> async { semaphore.withPermit { probeUrl(variant, defaultUserAgent) } } }
            .awaitAll()
    }
}
