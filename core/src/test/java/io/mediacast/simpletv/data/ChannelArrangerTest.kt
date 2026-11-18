package io.mediacast.simpletv.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelArrangerTest {
    private fun testChannel(
        key: String,
        name: String,
    ): Channel =
        Channel(
            key = key,
            name = name,
            group = "Общие",
            logo = null,
            tvgId = null,
            tvgName = null,
            variants = listOf(StreamVariant("http://live/$key", "wink", "Wink")),
        )

    @Test
    fun detectsTimezonesAndAdultChannels() {
        assertTrue(ChannelArranger.isOtherTimezone("Первый канал +4"))
        assertTrue(ChannelArranger.isOtherTimezone("НТВ (Восток)"))
        assertFalse(ChannelArranger.isOtherTimezone("Первый канал HD"))
        assertTrue(ChannelArranger.isAdult("Brazzers TV", null))
        assertTrue(ChannelArranger.isAdult("Канал", "18+"))
        assertFalse(ChannelArranger.isAdult("Первый канал", "Общие"))
    }

    @Test
    fun testSlot3IsMatchTvNotMatchPremier() {
        val channels =
            listOf(
                testChannel("первый канал hd", "Первый канал HD"),
                testChannel("россия 1 hd", "Россия 1 HD"),
                testChannel("матч премьер hd", "Матч! Премьер HD"),
                testChannel("матч тв hd", "Матч ТВ HD"),
                testChannel("нтв hd", "НТВ HD"),
            )

        val arranged =
            ChannelArranger.arrange(
                channels,
                ChannelArranger.Options(
                    hideSdDuplicates = false,
                    hideTimezones = false,
                    standardOrder = true,
                    preferMaxQuality = true,
                ),
            )

        assertEquals("Первый канал HD", arranged[0].name)
        assertEquals("Россия 1 HD", arranged[1].name)
        assertEquals("Матч ТВ HD", arranged[2].name)
        assertEquals("НТВ HD", arranged[3].name)
        assertFalse(arranged[2].name.contains("Премьер"))
        assertTrue(arranged.any { it.name == "Матч! Премьер HD" })
    }

    @Test
    fun prefersBeelineThenWinkOttWhileExcludingExplicitSdFromHd() {
        val hd =
            testChannel("первый канал hd", "Первый канал HD").copy(
                variants =
                    listOf(
                        StreamVariant("https://example.test/tricolor", "tricolor", "Триколор"),
                        StreamVariant("https://example.test/CH_1TVHD/manifest.mpd", "wink", "Wink TV"),
                    ),
            )
        val ott =
            testChannel("первый канал hd ott", "Первый канал HD (OTT)").copy(
                variants = listOf(StreamVariant("https://example.test/CH_1TVHD_OTT/manifest.mpd", "wink", "Wink TV")),
            )
        val sd =
            testChannel("первый канал", "Первый канал").copy(
                variants =
                    listOf(
                        StreamVariant("https://example.test/CH_1TVSD/variant.m3u8", "wink", "Wink TV"),
                        StreamVariant("https://example.test/channel535/manifest.mpd", "beeline", "Beeline"),
                    ),
            )
        val arranged =
            ChannelArranger.arrange(
                listOf(hd, ott, sd),
                ChannelArranger.Options(hideSdDuplicates = true, hideTimezones = true, standardOrder = true),
            )
        assertEquals(listOf("Beeline", "Wink TV (OTT)", "Wink TV", "Триколор"), arranged.single().variants.map { it.sourceName })
        assertFalse(arranged.single().variants.any { "CH_1TVSD" in it.url })
    }
}
