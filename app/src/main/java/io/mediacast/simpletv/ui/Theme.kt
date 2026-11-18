package io.mediacast.simpletv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Пресеты акцента. Один и тот же набор используется в настройках Android
 * и десктопа; id сохраняется в SharedPreferences.
 */
enum class AccentPalette(
    val id: String,
    val accent: Color,
    val accentSoft: Color,
    val gradientEnd: Color,
    val onAccent: Color,
) {
    VIOLET("violet", Color(0xFF5B4FE8), Color(0xFF8B7CFF), Color(0xFF6D5DF6), Color.White),
    ORANGE("orange", Color(0xFFFF6B3D), Color(0xFFFFB347), Color(0xFFFFB347), Color(0xFF1A0B04)),
    BLUE("blue", Color(0xFF2563EB), Color(0xFF60A5FA), Color(0xFF1D4ED8), Color.White),
    TEAL("teal", Color(0xFF0F766E), Color(0xFF5EEAD4), Color(0xFF115E59), Color.White),
    PINK("pink", Color(0xFFDB2777), Color(0xFFF9A8D4), Color(0xFFBE185D), Color.White),
    RED("red", Color(0xFFDC2626), Color(0xFFFCA5A5), Color(0xFFB91C1C), Color.White);

    companion object {
        fun fromId(id: String?): AccentPalette = entries.firstOrNull { it.id == id } ?: VIOLET
    }
}

object C {
    private var palette by mutableStateOf(AccentPalette.VIOLET)

    val accentId: String get() = palette.id
    val Accent: Color get() = palette.accent
    val Accent2: Color get() = palette.accentSoft
    val AccentGradientEnd: Color get() = palette.gradientEnd
    val OnAccent: Color get() = palette.onAccent
    val Selected: Color get() = Accent.copy(alpha = 0.14f)

    fun apply(id: String?) {
        palette = AccentPalette.fromId(id)
    }

    val Bg = Color(0xFF060A13)
    val Panel = Color(0xF20B1120)
    val PanelSolid = Color(0xFF0E1527)
    val Card = Color(0x12FFFFFF)
    val CardHover = Color(0x26FFFFFF)
    val Blue = Color(0xFF4DA3FF)
    val Green = Color(0xFF4CD08A)
    val Red = Color(0xFFFF4D5E)
    val Text = Color(0xFFF4F6FB)
    val Text2 = Color(0xB8E6EAF5)
    val Text3 = Color(0x80E6EAF5)
    val Divider = Color(0x1AFFFFFF)

    val accentBrush: Brush get() = Brush.horizontalGradient(listOf(Accent, AccentGradientEnd))
    val panelBrush = Brush.horizontalGradient(
        0f to Color(0xFA080D1A), 0.72f to Color(0xF00B1222), 1f to Color(0xD90B1222)
    )
    val bottomBrush = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6050912), Color(0xFA050912)))
}

@Composable
fun StvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            secondary = C.Accent2,
            background = C.Bg,
            surface = C.PanelSolid,
            onPrimary = C.OnAccent,
            onSurface = C.Text,
            onBackground = C.Text,
        ),
        content = content
    )
}
