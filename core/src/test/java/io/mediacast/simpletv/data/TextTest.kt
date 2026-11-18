package io.mediacast.simpletv.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test
    fun channelKeyNormalizesNamesForMerging() {
        assertEquals("матч тв", Text.channelKey("Матч! ТВ"))
        assertEquals("тв 3 hd", Text.channelKey("ТВ-3 HD"))
        assertEquals("channel hd", Text.channelKey("Channel ᴴᴰ"))
        assertEquals("первый hd", Text.channelKey("Первый ᴴᴰ"))
        // Буквы и цифры разделяются: "тв3" и "ТВ 3" — один канал
        assertEquals(Text.channelKey("ТВ 3"), Text.channelKey("ТВ3"))
    }

    @Test
    fun looseKeyDropsQualityAndOttSuffixes() {
        assertEquals("россия 1", Text.looseKey("Россия 1 ᴼᵀᵀ"))
        assertEquals("канал", Text.looseKey("Канал HD (OTT)"))
        assertEquals("канал", Text.looseKey("Канал FHD"))
        // HD и SD остаются разными каналами в обычном ключе
        assertEquals("channel hd", Text.channelKey("Channel HD"))
    }

    @Test
    fun base64RoundTripHandlesUtf8() {
        val text = "привет мир 123 !?"
        assertEquals(text, Text.decode64(Text.bytesToB64Url(text.toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun decode64AcceptsUrlSafeAndMissingPadding() {
        // 2 байта дают base64 «aGk=», bytesToB64Url отбрасывает «=», decode64 восстанавливает
        assertEquals("hi", Text.decode64(Text.bytesToB64Url("hi".toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun hexToBytesDecodesPairs() {
        assertEquals(byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()).toList(), Text.hexToBytes("007F80FF").toList())
    }

    @Test
    fun decodePlaylistPrefersUtf8AndFallsBackToWin1251() {
        val utf8 = "#EXTM3U\n#EXTINF:-1,Первый канал\nhttp://example.test/1.m3u8"
        assertEquals(utf8, Text.decodePlaylist(utf8.toByteArray(Charsets.UTF_8)))
        assertEquals(utf8, Text.decodePlaylist(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8.toByteArray(Charsets.UTF_8)))
        // windows-1251: «Каналы игры ТВ» — при разборе как UTF-8 получается больше 10 битых символов
        val cp1251 = "#EXTINF:-1,Каналы игры ТВ".toByteArray(charset("windows-1251"))
        assertEquals("#EXTINF:-1,Каналы игры ТВ", Text.decodePlaylist(cp1251))
    }
}
