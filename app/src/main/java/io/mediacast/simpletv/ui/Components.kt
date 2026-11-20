package io.mediacast.simpletv.ui

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.mediacast.simpletv.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Элемент, удобный и для пульта (D-pad), и для касания: подсветка и контрастная рамка при фокусе,
 * OK — нажатие, долгое OK или кнопка «Меню» — дополнительное действие.
 *
 * Фон и скругление применяются после [modifier] и `scale`, чтобы углы подложки не выглядывали
 * из-под скруглённой рамки фокуса. Для акцентных кнопок ([prominent] = true) рисуется
 * внешний белый контур с зазором, читаемый при любой цветовой теме.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusItem(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    containerColor: Color = Color.Transparent,
    borderColor: Color = Color.Transparent,
    prominent: Boolean = false,
    liftOnFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    shape: RoundedCornerShape = RoundedCornerShape(AppRadius.panel),
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus?.invoke() }

    val scale by animateFloatAsState(
        targetValue = if (focused && liftOnFocus) 1.04f else 1f,
        animationSpec = tween(120),
        label = "focusScale",
    )

    val base = modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .scale(scale)

    if (prominent) {
        Box(
            base
                .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
                .padding(3.dp)
                .clip(shape)
                .background(containerColor)
                .onKeyEvent { e ->
                    if (onLongClick != null && e.type == KeyEventType.KeyUp && e.key == Key.Menu) {
                        onLongClick()
                        true
                    } else {
                        false
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
        ) { content(focused) }
    } else {
        val selectedBg = if (containerColor == Color.Transparent) {
            C.Selected
        } else {
            C.Selected.compositeOver(containerColor)
        }
        val bg by animateColorAsState(
            targetValue = when {
                focused -> C.CardHover
                selected -> selectedBg
                else -> containerColor
            },
            animationSpec = tween(120),
            label = "focusBg",
        )
        val strokeModifier = when {
            focused -> Modifier.border(2.dp, C.Accent2, shape)
            borderColor != Color.Transparent -> Modifier.border(1.dp, borderColor, shape)
            else -> Modifier
        }
        Box(
            base
                .clip(shape)
                .background(bg)
                .then(strokeModifier)
                .onKeyEvent { e ->
                    if (onLongClick != null && e.type == KeyEventType.KeyUp && e.key == Key.Menu) {
                        onLongClick()
                        true
                    } else {
                        false
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
        ) { content(focused) }
    }
}

private val logoColors = listOf(
    Color(0xFF3D5AFE), Color(0xFF00897B), Color(0xFF8E24AA), Color(0xFFD84315),
    Color(0xFF5D4037), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFC2185B),
)

@Composable
fun LogoImage(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val urls = remember(url, name) { listOfNotNull(url, ImageLoader.fallback(name)).distinct() }
    var bmp by remember(urls) { mutableStateOf(urls.firstNotNullOfOrNull(ImageLoader::cached)) }
    LaunchedEffect(urls) {
        if (bmp == null) {
            for (candidate in urls) {
                bmp = ImageLoader.load(candidate)
                if (bmp != null) break
            }
        }
    }
    val shape = RoundedCornerShape(AppRadius.control)
    val fallbackBg = logoColors[name.hashCode().absoluteValue % logoColors.size].copy(alpha = 0.72f)
    Box(
        modifier
            .size(width = size * 1.5f, height = size)
            .clip(shape)
            .background(if (bmp != null) Color(0xFF1F2A44) else fallbackBg),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                b,
                null,
                Modifier.padding(size / 10).fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                initials(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).coerceAtLeast(12f).sp,
                maxLines = 1,
            )
        }
    }
}

private fun initials(name: String): String {
    val words = name.replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "TV"
        words.size == 1 -> words[0].take(3)
        else -> (words[0].take(1) + words[1].take(1))
    }.uppercase(Locale.ROOT)
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, brush: Brush = C.accentBrush) {
    Box(modifier.height(height).clip(RoundedCornerShape(AppRadius.chip)).background(C.Border)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(brush))
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = C.Card, textColor: Color = C.Text2) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = AppSpace.sm, vertical = 2.dp)) {
        Text(text, color = textColor, fontSize = AppType.caption, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Тонкая вертикальная метка активного элемента — отделяет выбор (playing/active) от фокуса пульта. */
@Composable
fun SelectedBar(visible: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(3.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(AppRadius.chip))
            .background(if (visible) C.Accent2 else Color.Transparent),
    )
}

/** Строка подсказок для кнопок пульта в нижней части TV-экранов. */
@Composable
fun RemoteHintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (keyLabel, actionLabel) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(AppRadius.chip))
                        .background(C.Card)
                        .border(1.dp, C.Border, RoundedCornerShape(AppRadius.chip))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(keyLabel, color = C.Text, fontSize = AppType.caption, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(actionLabel, color = C.Text3, fontSize = AppType.caption, maxLines = 1)
            }
        }
    }
}

/**
 * Локаль для дат и времени — должна совпадать с тем, как разрешаются строки приложения:
 * значения по умолчанию русские, values-en английские, остальные языки откатываются на русский.
 */
fun dateLocale(language: String): Locale =
    if (language == "en") Locale.ENGLISH else Locale.forLanguageTag("ru")

/** Основная локаль конфигурации: LocaleList доступен с API 24, на Android 6 — одиночная locale. */
fun primaryLocale(configuration: Configuration): Locale =
    if (Build.VERSION.SDK_INT >= 24) configuration.locales[0]
    else @Suppress("DEPRECATION") configuration.locale

/**
 * Подпись группы: специальные ключи фильтров и группы по умолчанию из парсера
 * переводятся через ресурсы, остальные группы — имена из плейлиста как есть.
 */
@Composable
fun groupLabel(name: String): String = when (name) {
    MainViewModel.ALL -> stringResource(R.string.group_all)
    MainViewModel.FAV -> stringResource(R.string.group_favourites)
    MainViewModel.NOW -> stringResource(R.string.group_now)
    MainViewModel.RECENT -> stringResource(R.string.group_recent)
    MainViewModel.HD -> stringResource(R.string.group_hd)
    MainViewModel.UHD -> stringResource(R.string.group_uhd)
    "Новые каналы", "New channels" -> stringResource(R.string.group_new)
    "Общие", "General" -> stringResource(R.string.group_generic)
    else -> name
}

@Composable
private fun dateLocale(): Locale {
    val lang = primaryLocale(LocalConfiguration.current).language
    return remember(lang) { dateLocale(lang) }
}

@Composable
fun hhmm(ms: Long): String {
    val locale = dateLocale()
    return remember(locale) { SimpleDateFormat("HH:mm", locale) }.format(Date(ms))
}

@Composable
fun dayTitle(ms: Long): String {
    val locale = dateLocale()
    val fmt = remember(locale) { SimpleDateFormat("EEEE, d MMMM", locale) }
    return fmt.format(Date(ms)).replaceFirstChar { it.uppercase() }
}
