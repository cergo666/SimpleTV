package io.mediacast.simpletv.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Uses the shared fixture in testdata/ that the Flutter parser test reads too,
 * so the Kotlin and Dart implementations cannot silently drift apart.
 */
class M3uParserTest {
    private val source = SourceConfig("fixture", "Fixture", emptyList())

    private fun parseFixture() =
        M3uParser.parse(
            File("../testdata/m3u/sample.m3u").readText(),
            source,
            skipWebPages = false,
        )

    @Test
    fun parsesTheSharedFixture() {
        val result = parseFixture()
        assertEquals("https://example.test/epg.xml", result.tvgUrl)
        assertEquals(listOf("Channel One", "Movie Two", "Channel Three"), result.channels.map { it.name })
        assertEquals(listOf("News", "Movies", "Sport"), result.channels.map { it.group })
        assertEquals(
            listOf("one", null, null),
            result.channels.map { it.tvgId },
        )
    }

    @Test
    fun keepsVlcOptionsPipeHeadersAndCatchup() {
        val channels = parseFixture().channels
        val one = channels[0].variant
        assertEquals("https://example.test/one.m3u8", one.url)
        assertEquals("Agent/1.0", one.userAgent)
        assertEquals("https://example.test/ref", one.referer)
        assertEquals(3.0, one.catchup?.days)

        val two = channels[1].variant
        assertEquals("https://example.test/two.m3u8", two.url)
        assertEquals("PipeAgent", two.userAgent)
        assertEquals("https://example.test/pipe", two.referer)
        assertNull(two.catchup)

        assertNull(channels[2].variant.userAgent)
    }
}
