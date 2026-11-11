package app.velo.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.velo.browser.data.Settings

@Immutable
data class VeloColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val danger: Color,
    val success: Color,
    val scrim: Color,
)

@Immutable
data class VeloShapes(
    val bar: Shape,
    val card: Shape,
    val tile: Shape,
    val button: Shape,
    val chip: Shape,
    val field: Shape,
    val sheet: Shape,
    val dialog: Shape,
    val bubble: Shape,
)

val LocalVeloColors = compositionLocalOf { veloColors(dark = false, pureBlack = false, accentArgb = Settings.ACCENT_INK) }
val LocalVeloShapes = compositionLocalOf { veloShapes(Settings.STYLE_ROUND) }

object Velo {
    val colors: VeloColors
        @Composable @ReadOnlyComposable get() = LocalVeloColors.current
    val shapes: VeloShapes
        @Composable @ReadOnlyComposable get() = LocalVeloShapes.current
}

/** Named colours offered for buttons and highlights. The first entry follows the theme. */
val AccentPresets: List<Pair<String, Int>> = listOf(
    "Ink" to Settings.ACCENT_INK,
    "Blue" to 0xFF3D7BFF.toInt(),
    "Violet" to 0xFF7C5CFF.toInt(),
    "Pink" to 0xFFFF4F8B.toInt(),
    "Red" to 0xFFF2463D.toInt(),
    "Orange" to 0xFFFF8A3D.toInt(),
    "Amber" to 0xFFF5B301.toInt(),
    "Green" to 0xFF22B866.toInt(),
    "Teal" to 0xFF12B5A6.toInt(),
    "Sky" to 0xFF0EA5E9.toInt(),
)

fun veloColors(dark: Boolean, pureBlack: Boolean, accentArgb: Int): VeloColors {
    val bg: Color
    val surface: Color
    val surfaceHi: Color
    val text: Color
    val textDim: Color
    val textFaint: Color
    val outline: Color
    if (dark) {
        bg = if (pureBlack) Color(0xFF000000) else Color(0xFF0D0D10)
        surface = if (pureBlack) Color(0xFF101013) else Color(0xFF18181C)
        surfaceHi = if (pureBlack) Color(0xFF1B1B20) else Color(0xFF24242A)
        text = Color(0xFFF4F4F6)
        textDim = Color(0xFFA3A3AF)
        textFaint = Color(0xFF6B6B77)
        outline = Color(0xFF2A2A31)
    } else {
        bg = Color(0xFFFFFFFF)
        surface = Color(0xFFF4F4F6)
        surfaceHi = Color(0xFFE9E9EE)
        text = Color(0xFF0B0B0F)
        textDim = Color(0xFF5F5F6B)
        textFaint = Color(0xFF9A9AA6)
        outline = Color(0xFFE3E3E8)
    }
    val ink = accentArgb == Settings.ACCENT_INK
    val accent = if (ink) text else Color(accentArgb)
    val onAccent = when {
        ink -> bg
        accent.luminance() > 0.55f -> Color(0xFF0B0B0F)
        else -> Color.White
    }
    return VeloColors(
        dark = dark,
        bg = bg,
        surface = surface,
        surfaceHi = surfaceHi,
        text = text,
        textDim = textDim,
        textFaint = textFaint,
        outline = outline,
        accent = accent,
        onAccent = onAccent,
        accentSoft = accent.copy(alpha = if (ink) 0.08f else 0.14f),
        danger = if (dark) Color(0xFFFF6369) else Color(0xFFE5484D),
        success = if (dark) Color(0xFF4CC38A) else Color(0xFF2E9E5B),
        scrim = Color.Black.copy(alpha = if (dark) 0.62f else 0.38f),
    )
}

fun veloShapes(style: Int): VeloShapes = when (style) {
    Settings.STYLE_SOFT -> VeloShapes(
        bar = RoundedCornerShape(18.dp),
        card = RoundedCornerShape(16.dp),
        tile = RoundedCornerShape(14.dp),
        button = RoundedCornerShape(14.dp),
        chip = RoundedCornerShape(10.dp),
        field = RoundedCornerShape(12.dp),
        sheet = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dialog = RoundedCornerShape(20.dp),
        bubble = RoundedCornerShape(16.dp),
    )
    Settings.STYLE_SHARP -> VeloShapes(
        bar = RoundedCornerShape(6.dp),
        card = RoundedCornerShape(6.dp),
        tile = RoundedCornerShape(4.dp),
        button = RoundedCornerShape(6.dp),
        chip = RoundedCornerShape(4.dp),
        field = RoundedCornerShape(6.dp),
        sheet = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        dialog = RoundedCornerShape(8.dp),
        bubble = RoundedCornerShape(6.dp),
    )
    else -> VeloShapes(
        bar = CircleShape,
        card = RoundedCornerShape(24.dp),
        tile = RoundedCornerShape(20.dp),
        button = CircleShape,
        chip = CircleShape,
        field = RoundedCornerShape(18.dp),
        sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dialog = RoundedCornerShape(28.dp),
        bubble = RoundedCornerShape(22.dp),
    )
}

/**
 * Provides Velo's colours and shapes. Colour changes (theme or accent) cross-fade instead of
 * snapping, which is what makes switching between light and dark feel smooth.
 */
@Composable
fun VeloTheme(dark: Boolean, pureBlack: Boolean, accent: Int, style: Int, content: @Composable () -> Unit) {
    val target = remember(dark, pureBlack, accent) { veloColors(dark, pureBlack, accent) }
    val spec = tween<Color>(320)
    val bg by animateColorAsState(target.bg, spec, label = "bg")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val surfaceHi by animateColorAsState(target.surfaceHi, spec, label = "surfaceHi")
    val text by animateColorAsState(target.text, spec, label = "text")
    val textDim by animateColorAsState(target.textDim, spec, label = "textDim")
    val textFaint by animateColorAsState(target.textFaint, spec, label = "textFaint")
    val outline by animateColorAsState(target.outline, spec, label = "outline")
    val accentColor by animateColorAsState(target.accent, spec, label = "accent")
    val onAccent by animateColorAsState(target.onAccent, spec, label = "onAccent")
    val accentSoft by animateColorAsState(target.accentSoft, spec, label = "accentSoft")
    val colors = target.copy(
        bg = bg, surface = surface, surfaceHi = surfaceHi, text = text, textDim = textDim,
        textFaint = textFaint, outline = outline, accent = accentColor, onAccent = onAccent,
        accentSoft = accentSoft,
    )
    val shapes = remember(style) { veloShapes(style) }
    val selection = remember(target.accent) {
        val handle = if (target.accent.toArgb() == target.text.toArgb()) Color(0xFF3D7BFF) else target.accent
        TextSelectionColors(handleColor = handle, backgroundColor = handle.copy(alpha = 0.3f))
    }
    CompositionLocalProvider(
        LocalVeloColors provides colors,
        LocalVeloShapes provides shapes,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}
