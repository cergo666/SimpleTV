package io.mediacast.simpletv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Типографическая шкала для Android и Android TV (10-foot UI). */
object AppType {
    val caption = 12.sp
    val body = 14.sp
    val subtitle = 16.sp
    val title = 20.sp
    val display = 26.sp
    val hero = 48.sp
}

/** Шкала скруглений, общая с desktop-клиентом. */
object AppRadius {
    val chip = 4.dp
    val control = 8.dp
    val panel = 12.dp
    val card = 16.dp
}

/** Сетка отступов 4 / 8 / 12 / 16 / 24 / 32. */
object AppSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Безопасная зона Android TV (~5 % при 1080p), чтобы оверлеи не срезались overscan. */
object TvSafe {
    val horizontal = 48.dp
    val vertical = 28.dp
}

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
    VIOLET("violet", Color(0xFF5B4FE8), Color(0xFF9D91FF), Color(0xFF6D5DF6), Color.White),
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
    val Selected: Color get() = Accent.copy(alpha = 0.18f)

    fun apply(id: String?) {
        palette = AccentPalette.fromId(id)
    }

    val Bg = Color(0xFF070B14)
    val Panel = Color(0xF20B1120)
    val PanelSolid = Color(0xFF111829)
    val PanelCard = Color(0xEB0B1120)
    val Card = Color(0xFF192238)
    val CardHover = Color(0xFF253252)
    val Border = Color(0xFF26324D)
    val Divider = Color(0xFF1E293B)
    val Blue = Color(0xFF4DA3FF)
    val OnInfo = Color(0xFF1A0B04)
    val Green = Color(0xFF4CD08A)
    val Red = Color(0xFFFF4D5E)
    val LiveBg = Color(0xFFB91C1C)
    val OnLive = Color.White
    val Text = Color(0xFFF8FAFC)
    val Text2 = Color(0xFFCBD5E1)
    val Text3 = Color(0xFF94A3B8)

    val accentBrush: Brush get() = Brush.horizontalGradient(listOf(Accent, AccentGradientEnd))
    val panelBrush = Brush.horizontalGradient(
        0f to Color(0xFA070B14), 0.72f to Color(0xF20B1120), 1f to Color(0xEB0B1120)
    )
    val bottomBrush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.22f to Color(0xA6050912),
        0.62f to Color(0xEB050912),
        1f to Color(0xFC050912),
    )
}

@Composable
fun StvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            secondary = C.Accent2,
            background = C.Bg,
            surface = C.PanelSolid,
            surfaceVariant = C.Card,
            outline = C.Border,
            onPrimary = C.OnAccent,
            onSurface = C.Text,
            onBackground = C.Text,
        ),
        content = content
    )
}
