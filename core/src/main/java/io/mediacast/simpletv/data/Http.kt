package io.mediacast.simpletv.data

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.zip.GZIPInputStream

/** Минимальный HTTP-клиент: свой User-Agent, ручные редиректы (http↔https), ETag. */
object Http {
    class Response(
        val code: Int,
        val url: String,
        val etag: String?,
        val lastModified: String?,
        val contentType: String?,
        private val conn: HttpURLConnection?,
        val body: InputStream?,
    ) : AutoCloseable {
        val notModified get() = code == 304
        val ok get() = code in 200..299
        val contentLength: Long get() = conn?.contentLengthLong ?: -1L

        override fun close() {
            runCatching { body?.close() }
            runCatching { conn?.disconnect() }
        }
    }

    fun open(
        url: String,
        userAgent: String?,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        etag: String? = null,
        lastModified: String? = null,
        connectTimeoutMs: Int = 15000,
        readTimeoutMs: Int = 30000,
        followRedirects: Boolean = true,
        gunzip: Boolean = true,
    ): Response {
        var current = url
        repeat(8) {
            val base =
                try {
                    java.net.URI(current).toURL()
                } catch (e: Exception) {
                    throw IOException("Bad URL: $current", e)
                }
            val conn =
                (base.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = method
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    if (etag != null) setRequestProperty("If-None-Match", etag)
                    if (lastModified != null) setRequestProperty("If-Modified-Since", lastModified)
                }
            val code =
                try {
                    conn.responseCode
                } catch (e: IOException) {
                    conn.disconnect()
                    throw e
                }
            if (followRedirects && code in 300..399 && code != 304) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrEmpty()) throw IOException("Redirect without Location")
                current =
                    try {
                        java.net.URI(current).resolve(loc).toString()
                    } catch (e: Exception) {
                        throw IOException("Bad redirect: $loc", e)
                    }
                return@repeat
            }
            val stream =
                if (method == "HEAD") {
                    null
                } else {
                    try {
                        if (code >= 400) conn.errorStream else conn.inputStream
                    } catch (e: IOException) {
                        null
                    }
                }
            return Response(
                code,
                current,
                conn.getHeaderField("ETag"),
                conn.getHeaderField("Last-Modified"),
                conn.contentType,
                conn,
                stream?.let { if (gunzip) maybeGunzip(it) else it },
            )
        }
        throw IOException("Too many redirects")
    }

    /** XMLTV часто отдают как .xml.gz без Content-Encoding — распаковываем по сигнатуре. */
    fun maybeGunzip(input: InputStream): InputStream {
        val bis = BufferedInputStream(input, 64 * 1024)
        bis.mark(4)
        val b1 = bis.read()
        val b2 = bis.read()
        bis.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(bis, 64 * 1024), 64 * 1024) else bis
    }

    fun getBytes(
        url: String,
        userAgent: String?,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray =
        open(url, userAgent, headers).use { r ->
            if (!r.ok) throw IOException("HTTP ${r.code}")
            r.body?.readBytes() ?: ByteArray(0)
        }

    fun getText(
        url: String,
        userAgent: String?,
        headers: Map<String, String> = emptyMap(),
    ): String =
        Text.decodePlaylist(getBytes(url, userAgent, headers))
}
