package io.mediacast.simpletv.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccentPaletteTest {
    private fun luminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
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
    fun everyPresetKeepsTextReadableOnBothGradientStopsAndSurfaces() {
        val darkSurfaces = listOf(C.Bg, C.PanelSolid, C.Card, C.CardHover)
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
            for (surface in darkSurfaces) {
                assertTrue(
                    "accentSoft on surface: ${palette.id}",
                    contrast(palette.accentSoft.toArgb(), surface.toArgb()) >= 4.5,
                )
            }
        }
    }

    @Test
    fun textAndSemanticPairsMeetWcagAa() {
        val surfaces = listOf(C.Bg, C.PanelSolid, C.Card, C.CardHover)
        val textColors = listOf(C.Text, C.Text2, C.Text3)
        for (fg in textColors) {
            for (bg in surfaces) {
                assertTrue(
                    "contrast $fg on $bg",
                    contrast(fg.toArgb(), bg.toArgb()) >= 4.5,
                )
            }
        }
        assertTrue("OnLive on LiveBg", contrast(C.OnLive.toArgb(), C.LiveBg.toArgb()) >= 4.5)
        assertTrue("OnInfo on Blue", contrast(C.OnInfo.toArgb(), C.Blue.toArgb()) >= 4.5)
    }

    @Test
    fun designTokensStayOnExpectedScale() {
        assertEquals(
            setOf(12.sp, 14.sp, 16.sp, 20.sp, 26.sp, 48.sp),
            setOf(AppType.caption, AppType.body, AppType.subtitle, AppType.title, AppType.display, AppType.hero),
        )
        assertEquals(
            setOf(4.dp, 8.dp, 12.dp, 16.dp),
            setOf(AppRadius.chip, AppRadius.control, AppRadius.panel, AppRadius.card),
        )
        assertEquals(
            setOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp),
            setOf(AppSpace.xs, AppSpace.sm, AppSpace.md, AppSpace.lg, AppSpace.xl, AppSpace.xxl),
        )
        assertEquals(48.dp, TvSafe.horizontal)
        assertEquals(28.dp, TvSafe.vertical)
    }
}
