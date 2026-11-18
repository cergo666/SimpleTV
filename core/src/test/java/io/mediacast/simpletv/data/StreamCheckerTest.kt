package io.mediacast.simpletv.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamCheckerTest {
    private var now = 1_000L

    @AfterTest
    fun reset() {
        StreamChecker.clock = System::currentTimeMillis
        StreamChecker.retainOnly(emptySet())
    }

    @Test
    fun cachedResultsExpireSoTransientFailuresDoNotHideChannelsForever() {
        StreamChecker.clock = { now }
        StreamChecker.recordPlayback("https://example.test/live.m3u8", success = false)
        assertEquals(StreamChecker.Health.UNAVAILABLE, StreamChecker.status("https://example.test/live.m3u8"))

        now += 5 * 60 * 1000L
        assertNull(StreamChecker.status("https://example.test/live.m3u8"))

        StreamChecker.recordPlayback("https://example.test/live.m3u8", success = true)
        assertEquals(StreamChecker.Health.PLAYING, StreamChecker.status("https://example.test/live.m3u8"))
        now += 30 * 60 * 1000L
        assertNull(StreamChecker.status("https://example.test/live.m3u8"))
    }

    @Test
    fun retainOnlyForgetsVariantsThatLeftThePlaylist() {
        StreamChecker.clock = { now }
        StreamChecker.recordPlayback("https://example.test/old.m3u8", success = true)
        StreamChecker.recordPlayback("https://example.test/current.m3u8", success = true)

        StreamChecker.retainOnly(setOf("https://example.test/current.m3u8"))

        assertNull(StreamChecker.status("https://example.test/old.m3u8"))
        assertEquals(StreamChecker.Health.PLAYING, StreamChecker.status("https://example.test/current.m3u8"))
    }

    @Test
    fun verifiesRelativeHlsKeyWithoutTreatingHttpSuccessAsPlayback() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val (code, body) =
                when (exchange.requestURI.path) {
                    "/start" -> {
                        exchange.responseHeaders.add("Location", "/hls/master")
                        302 to byteArrayOf()
                    }
                    "/hls/master" -> 200 to "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3000000\nmedia\n".toByteArray()
                    "/hls/media" -> 200 to "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"blocked-key\"\n#EXTINF:6,\nsegment.ts\n".toByteArray()
                    "/hls/valid" -> 200 to "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"valid-key\"\n#EXTINF:6,\nsegment.ts\n".toByteArray()
                    "/hls/valid-key" -> {
                        if (exchange.requestHeaders.getFirst("User-Agent") == "SimpleTV test") {
                            200 to ByteArray(16)
                        } else {
                            403 to byteArrayOf()
                        }
                    }
                    else -> 403 to byteArrayOf()
                }
            exchange.sendResponseHeaders(code, if (body.isEmpty()) -1L else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val root = "http://127.0.0.1:${server.address.port}"
            val blocked = StreamVariant("$root/start", "test", "Test")
            val accessible = blocked.copy(url = "$root/hls/valid")
            StreamChecker.probeUrl(blocked, "SimpleTV test")
            StreamChecker.probeUrl(accessible, "SimpleTV test")
            assertEquals(StreamChecker.Health.UNAVAILABLE, StreamChecker.status(blocked.url))
            assertEquals(StreamChecker.Health.REACHABLE, StreamChecker.status(accessible.url))
            StreamChecker.recordPlayback(accessible.url, success = true)
            assertEquals(StreamChecker.Health.PLAYING, StreamChecker.status(accessible.url))
        } finally {
            server.stop(0)
        }
    }
}
