package io.mediacast.simpletv.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccentPaletteTest {
    private fun luminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(foreground: Int, background: Int): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    @Test
    fun idsAreUniqueAndDefaultIsViolet() {
        assertEquals(AccentPalette.entries.size, AccentPalette.entries.map { it.id }.toSet().size)
        assertEquals("violet", C.accentId)
        C.apply("teal")
        assertEquals("teal", C.accentId)
        C.apply("violet")
    }

    @Test
    fun everyPresetKeepsTextReadableOnBothGradientStops() {
        for (palette in AccentPalette.entries) {
            val text = palette.onAccent.toArgb()
            assertTrue(
                "text on accent: ${palette.id}",
                contrast(text, palette.accent.toArgb()) >= 4.5,
            )
            assertTrue(
                "text on gradient end: ${palette.id}",
                contrast(text, palette.gradientEnd.toArgb()) >= 4.5,
            )
        }
    }
}
