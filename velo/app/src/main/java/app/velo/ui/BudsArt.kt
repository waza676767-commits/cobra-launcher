package app.velo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.Composable
import app.velo.bt.BudsStyle

/**
 * Product-style illustration of the connected model (bean buds in an open case, stem buds, or
 * over-ear headphones), drawn in vector so it's sharp on every screen and costs no image memory.
 */
@Composable
fun BudsArt(style: BudsStyle, accent: Color, modifier: Modifier = Modifier, glowing: Boolean = true) {
    Canvas(modifier) {
        if (glowing) drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.55f), radius = size.minDimension * 0.6f)
        )
        when (style) {
            BudsStyle.BEANS -> beans(accent)
            BudsStyle.STEMS -> stems(accent)
            BudsStyle.OVER_EAR -> overEar(accent)
        }
    }
}

private val shell = Brush.verticalGradient(listOf(Color(0xFF4A4E55), Color(0xFF26292E), Color(0xFF16181B)))
private val inner = Color(0xFF0B0C0E)

private fun DrawScope.case(top: Float, accent: Color, lidOpen: Boolean = true) {
    val w = size.width * 0.62f
    val h = size.height * 0.40f
    val l = (size.width - w) / 2
    // lid (open, behind)
    if (lidOpen) {
        val lidTop = top - h * 0.62f
        drawRoundRect(shell, Offset(l + w * 0.02f, lidTop), Size(w * 0.96f, h * 0.78f), CornerRadius(w * 0.2f))
        drawRoundRect(inner, Offset(l + w * 0.07f, lidTop + h * 0.08f), Size(w * 0.86f, h * 0.6f), CornerRadius(w * 0.16f))
    }
    // body
    drawRoundRect(shell, Offset(l, top), Size(w, h), CornerRadius(w * 0.24f, w * 0.24f))
    // rim light
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color.Transparent), startY = top, endY = top + h * 0.4f),
        Offset(l, top), Size(w, h), CornerRadius(w * 0.24f), style = Stroke(width = size.width * 0.004f)
    )
    // cradle opening
    drawRoundRect(inner, Offset(l + w * 0.05f, top), Size(w * 0.9f, h * 0.12f), CornerRadius(w * 0.05f))
    // status LED
    drawRoundRect(accent.copy(alpha = 0.9f), Offset(size.width / 2 - w * 0.08f, top + h * 0.32f),
        Size(w * 0.16f, h * 0.018f), CornerRadius(4f))
}

private fun DrawScope.bean(cx: Float, cy: Float, r: Float, mirror: Boolean) {
    val s = if (mirror) -1f else 1f
    rotate(if (mirror) 18f else -18f, Offset(cx, cy)) {
        drawOval(shell, Offset(cx - r, cy - r * 0.92f), Size(r * 2, r * 1.84f))
        // ear tip
        drawOval(Brush.verticalGradient(listOf(Color(0xFF3A3D43), Color(0xFF1D1F23))),
            Offset(cx + s * r * 0.25f - r * 0.55f, cy + r * 0.35f), Size(r * 1.1f, r * 0.9f))
        // highlight
        drawOval(Brush.radialGradient(listOf(Color(0x40FFFFFF), Color.Transparent),
            center = Offset(cx - s * r * 0.3f, cy - r * 0.4f), radius = r * 0.8f),
            Offset(cx - r, cy - r * 0.92f), Size(r * 2, r * 1.84f))
    }
}

private fun DrawScope.beans(accent: Color) {
    val top = size.height * 0.52f
    case(top, accent)
    val r = size.width * 0.105f
    bean(size.width * 0.385f, top - r * 0.15f, r, false)
    bean(size.width * 0.615f, top - r * 0.15f, r, true)
}

private fun DrawScope.stems(accent: Color) {
    val top = size.height * 0.55f
    case(top, accent)
    val r = size.width * 0.075f
    for ((cx, m) in listOf(size.width * 0.42f to false, size.width * 0.58f to true)) {
        val cy = top - r * 0.6f
        drawOval(shell, Offset(cx - r, cy - r), Size(r * 2, r * 1.8f))
        drawRoundRect(shell, Offset(cx - r * 0.32f + (if (m) r * 0.15f else -r * 0.15f), cy + r * 0.4f),
            Size(r * 0.64f, r * 2.6f), CornerRadius(r * 0.32f))
        drawOval(Brush.radialGradient(listOf(Color(0x40FFFFFF), Color.Transparent), Offset(cx, cy - r * 0.4f), r),
            Offset(cx - r, cy - r), Size(r * 2, r * 1.8f))
    }
}

private fun DrawScope.overEar(accent: Color) {
    val cx = size.width / 2
    val w = size.width * 0.56f
    val bandTop = size.height * 0.16f
    val band = Path().apply {
        moveTo(cx - w / 2, size.height * 0.58f)
        cubicTo(cx - w / 2, bandTop - size.height * 0.05f, cx + w / 2, bandTop - size.height * 0.05f, cx + w / 2, size.height * 0.58f)
    }
    drawPath(band, Color(0xFF2A2D32), style = Stroke(width = size.width * 0.05f))
    drawPath(band, Color(0x33FFFFFF), style = Stroke(width = size.width * 0.006f))
    for (x in listOf(cx - w / 2, cx + w / 2)) {
        val cw = size.width * 0.17f
        val ch = size.height * 0.30f
        drawRoundRect(shell, Offset(x - cw / 2, size.height * 0.5f), Size(cw, ch), CornerRadius(cw * 0.45f))
        drawRoundRect(Color(0xFF151619), Offset(x - cw * 0.32f, size.height * 0.53f), Size(cw * 0.64f, ch * 0.88f), CornerRadius(cw * 0.3f))
    }
    drawCircle(accent.copy(alpha = 0.9f), size.width * 0.008f, Offset(cx + w / 2, size.height * 0.62f))
}
