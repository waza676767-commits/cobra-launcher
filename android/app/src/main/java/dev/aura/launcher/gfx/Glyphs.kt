package dev.aura.launcher.gfx

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** Line-art symbols drawn in code (24×24 design grid), so they stay crisp at any GUI scale. */
enum class Glyph {
    SEARCH, PLUS, MINUS, CHECK, CHEVRON_RIGHT, CHEVRON_LEFT, CLOSE, GEAR, LOCK, LOCK_OPEN,
    FLASHLIGHT, CAMERA, BELL, SUN, MOON, CLOUD, CLOUD_SUN, RAIN, SNOW, BOLT, FOG,
    APPEARANCE, FLOWER, GRID, PHONE, WIDGETS, SPARKLES, BATTERY, EYE_SLASH, INFO, SWAP,
    PENCIL, TRASH, PLAY, PAUSE, NEXT, PREV, TEXT_SIZE, FOLDER, LOCATION, ALARM, CALENDAR,
    PHOTO, HOME, SHORTCUT, LEAF
}

object Glyphs {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val r = RectF()

    fun draw(c: Canvas, g: Glyph, cx: Float, cy: Float, size: Float, color: Int, weight: Float = 2f) {
        val s = size / 24f
        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        c.scale(s, s)
        p.color = color
        p.strokeWidth = weight
        stroke()
        when (g) {
            Glyph.SEARCH -> { c.drawCircle(10.5f, 10.5f, 6.5f, p); c.drawLine(15.5f, 15.5f, 20.5f, 20.5f, p) }
            Glyph.PLUS -> { c.drawLine(12f, 5f, 12f, 19f, p); c.drawLine(5f, 12f, 19f, 12f, p) }
            Glyph.MINUS -> c.drawLine(6f, 12f, 18f, 12f, p)
            Glyph.CHECK -> poly(c, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Glyph.CHEVRON_RIGHT -> poly(c, 9f, 5f, 16f, 12f, 9f, 19f)
            Glyph.CHEVRON_LEFT -> poly(c, 15f, 5f, 8f, 12f, 15f, 19f)
            Glyph.CLOSE -> { c.drawLine(6f, 6f, 18f, 18f, p); c.drawLine(18f, 6f, 6f, 18f, p) }
            Glyph.GEAR -> {
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    c.drawLine(12f + 6.5f * cos(a).toFloat(), 12f + 6.5f * sin(a).toFloat(), 12f + 9.5f * cos(a).toFloat(), 12f + 9.5f * sin(a).toFloat(), p.apply { strokeWidth = weight * 1.6f })
                }
                p.strokeWidth = weight
                c.drawCircle(12f, 12f, 6.5f, p)
                c.drawCircle(12f, 12f, 2.5f, p)
            }
            Glyph.LOCK, Glyph.LOCK_OPEN -> {
                fill(); r.set(5f, 10.5f, 19f, 21f); c.drawRoundRect(r, 2.5f, 2.5f, p)
                stroke()
                path.reset()
                if (g == Glyph.LOCK) {
                    path.moveTo(8f, 10.5f); path.lineTo(8f, 7.5f); path.cubicTo(8f, 2.5f, 16f, 2.5f, 16f, 7.5f); path.lineTo(16f, 10.5f)
                } else {
                    path.moveTo(8f, 10.5f); path.lineTo(8f, 7.5f); path.cubicTo(8f, 2.5f, 16f, 2.5f, 16f, 6.5f)
                }
                c.drawPath(path, p)
            }
            Glyph.FLASHLIGHT -> {
                path.reset()
                path.moveTo(7f, 3f); path.lineTo(17f, 3f); path.lineTo(17f, 6.5f); path.lineTo(14.5f, 10.5f)
                path.lineTo(14.5f, 21f); path.lineTo(9.5f, 21f); path.lineTo(9.5f, 10.5f); path.lineTo(7f, 6.5f); path.close()
                c.drawPath(path, p)
                c.drawLine(12f, 13f, 12f, 15.5f, p)
            }
            Glyph.CAMERA -> {
                path.reset()
                path.moveTo(3f, 8.5f); path.lineTo(7.5f, 8.5f); path.lineTo(9f, 5.5f); path.lineTo(15f, 5.5f); path.lineTo(16.5f, 8.5f)
                path.lineTo(21f, 8.5f); path.lineTo(21f, 19f); path.lineTo(3f, 19f); path.close()
                c.drawPath(path, p)
                c.drawCircle(12f, 13.3f, 3.6f, p)
            }
            Glyph.BELL -> {
                path.reset()
                path.moveTo(5f, 17f); path.lineTo(19f, 17f); path.lineTo(17.5f, 14.5f); path.lineTo(17.5f, 10.5f)
                path.cubicTo(17.5f, 4f, 6.5f, 4f, 6.5f, 10.5f); path.lineTo(6.5f, 14.5f); path.close()
                c.drawPath(path, p)
                c.drawLine(10.5f, 20f, 13.5f, 20f, p)
            }
            Glyph.SUN -> sun(c, 12f, 12f, 1f)
            Glyph.MOON -> {
                fill()
                path.reset()
                path.moveTo(15.5f, 3.5f)
                path.cubicTo(8f, 4f, 5f, 11f, 7.5f, 16f)
                path.cubicTo(10f, 20.5f, 17f, 21.5f, 20.5f, 16.5f)
                path.cubicTo(13.5f, 17.5f, 10.5f, 9f, 15.5f, 3.5f)
                path.close()
                c.drawPath(path, p)
            }
            Glyph.CLOUD -> cloud(c, 0f, 0f, true)
            Glyph.CLOUD_SUN -> {
                c.save(); c.translate(-3.5f, -4f); c.scale(0.8f, 0.8f, 12f, 12f); sunRays(c, 12f, 12f); c.restore()
                cloud(c, 1.5f, 2.5f, true)
            }
            Glyph.RAIN -> {
                cloud(c, 0f, -3f, true)
                stroke(); c.drawLine(8f, 18f, 7f, 21f, p); c.drawLine(12f, 18f, 11f, 21f, p); c.drawLine(16f, 18f, 15f, 21f, p)
            }
            Glyph.SNOW -> {
                cloud(c, 0f, -3f, true)
                fill(); c.drawCircle(8f, 19.5f, 1.2f, p); c.drawCircle(12f, 21f, 1.2f, p); c.drawCircle(16f, 19.5f, 1.2f, p)
            }
            Glyph.BOLT -> {
                cloud(c, 0f, -3f, true)
                fill(); path.reset(); path.moveTo(12.5f, 15f); path.lineTo(9.5f, 19.5f); path.lineTo(12f, 19.5f); path.lineTo(10.5f, 23f)
                path.lineTo(14.5f, 18f); path.lineTo(12f, 18f); path.lineTo(13.5f, 15f); path.close(); c.drawPath(path, p)
            }
            Glyph.FOG -> {
                c.drawLine(4f, 9f, 20f, 9f, p); c.drawLine(6f, 13f, 18f, 13f, p); c.drawLine(4f, 17f, 20f, 17f, p)
            }
            Glyph.APPEARANCE -> {
                c.drawCircle(12f, 12f, 8.5f, p)
                fill(); r.set(3.5f, 3.5f, 20.5f, 20.5f); c.drawArc(r, 90f, 180f, true, p)
            }
            Glyph.FLOWER -> {
                fill()
                for (i in 0 until 6) {
                    c.save(); c.rotate(i * 60f, 12f, 12f); r.set(10f, 3f, 14f, 11f); c.drawOval(r, p); c.restore()
                }
                p.color = 0x55000000; c.drawCircle(12f, 12f, 2.2f, p)
            }
            Glyph.GRID -> {
                fill()
                r.set(4f, 4f, 11f, 11f); c.drawRoundRect(r, 2.2f, 2.2f, p)
                r.set(13f, 4f, 20f, 11f); c.drawRoundRect(r, 2.2f, 2.2f, p)
                r.set(4f, 13f, 11f, 20f); c.drawRoundRect(r, 2.2f, 2.2f, p)
                r.set(13f, 13f, 20f, 20f); c.drawRoundRect(r, 2.2f, 2.2f, p)
            }
            Glyph.PHONE -> {
                r.set(6.5f, 2.5f, 17.5f, 21.5f); c.drawRoundRect(r, 3f, 3f, p)
                fill()
                for (yy in 0..1) for (xx in 0..1) { r.set(9f + xx * 3.5f, 6f + yy * 3.5f, 11.2f + xx * 3.5f, 8.2f + yy * 3.5f); c.drawRect(r, p) }
            }
            Glyph.WIDGETS -> {
                fill()
                r.set(3.5f, 3.5f, 13f, 13f); c.drawRoundRect(r, 2.5f, 2.5f, p)
                r.set(15f, 3.5f, 20.5f, 9f); c.drawRoundRect(r, 1.8f, 1.8f, p)
                r.set(3.5f, 15f, 20.5f, 20.5f); c.drawRoundRect(r, 2.2f, 2.2f, p)
            }
            Glyph.SPARKLES -> { fill(); star(c, 10f, 13f, 7f); star(c, 18f, 6f, 3.5f) }
            Glyph.BATTERY -> {
                r.set(2.5f, 7f, 19.5f, 17f); c.drawRoundRect(r, 3f, 3f, p)
                fill(); r.set(21f, 10f, 22.5f, 14f); c.drawRoundRect(r, 0.7f, 0.7f, p)
                r.set(5f, 9.5f, 13f, 14.5f); c.drawRoundRect(r, 1.2f, 1.2f, p)
            }
            Glyph.EYE_SLASH -> {
                path.reset(); path.moveTo(2.5f, 12f); path.cubicTo(7f, 4.5f, 17f, 4.5f, 21.5f, 12f); path.cubicTo(17f, 19.5f, 7f, 19.5f, 2.5f, 12f); path.close()
                c.drawPath(path, p); c.drawCircle(12f, 12f, 3f, p); c.drawLine(4f, 4f, 20f, 20f, p)
            }
            Glyph.INFO -> {
                c.drawCircle(12f, 12f, 9f, p); c.drawLine(12f, 11f, 12f, 16.5f, p)
                fill(); c.drawCircle(12f, 7.6f, 1.2f, p)
            }
            Glyph.SWAP -> {
                c.drawLine(4f, 8f, 18f, 8f, p); poly(c, 14f, 4f, 18f, 8f, 14f, 12f)
                c.drawLine(20f, 16f, 6f, 16f, p); poly(c, 10f, 12f, 6f, 16f, 10f, 20f)
            }
            Glyph.PENCIL -> {
                path.reset(); path.moveTo(15f, 4.5f); path.lineTo(19.5f, 9f); path.lineTo(9f, 19.5f); path.lineTo(4f, 20f)
                path.lineTo(4.5f, 15f); path.close(); c.drawPath(path, p)
            }
            Glyph.TRASH -> {
                c.drawLine(4f, 6.5f, 20f, 6.5f, p)
                path.reset(); path.moveTo(6f, 6.5f); path.lineTo(7f, 20.5f); path.lineTo(17f, 20.5f); path.lineTo(18f, 6.5f); c.drawPath(path, p)
                path.reset(); path.moveTo(9f, 6.5f); path.lineTo(9.5f, 3.5f); path.lineTo(14.5f, 3.5f); path.lineTo(15f, 6.5f); c.drawPath(path, p)
            }
            Glyph.PLAY -> { fill(); path.reset(); path.moveTo(7f, 4f); path.lineTo(20f, 12f); path.lineTo(7f, 20f); path.close(); c.drawPath(path, p) }
            Glyph.PAUSE -> { fill(); r.set(6f, 4f, 10f, 20f); c.drawRoundRect(r, 1f, 1f, p); r.set(14f, 4f, 18f, 20f); c.drawRoundRect(r, 1f, 1f, p) }
            Glyph.NEXT -> {
                fill(); path.reset(); path.moveTo(3f, 6f); path.lineTo(12f, 12f); path.lineTo(3f, 18f); path.close()
                path.moveTo(12f, 6f); path.lineTo(21f, 12f); path.lineTo(12f, 18f); path.close(); c.drawPath(path, p)
            }
            Glyph.PREV -> {
                fill(); path.reset(); path.moveTo(21f, 6f); path.lineTo(12f, 12f); path.lineTo(21f, 18f); path.close()
                path.moveTo(12f, 6f); path.lineTo(3f, 12f); path.lineTo(12f, 18f); path.close(); c.drawPath(path, p)
            }
            Glyph.TEXT_SIZE -> {
                path.reset(); path.moveTo(2.5f, 19f); path.lineTo(8f, 5f); path.lineTo(13.5f, 19f); c.drawPath(path, p); c.drawLine(4.6f, 14f, 11.4f, 14f, p)
                path.reset(); path.moveTo(14f, 19f); path.lineTo(17.5f, 10f); path.lineTo(21f, 19f); c.drawPath(path, p)
            }
            Glyph.FOLDER -> {
                path.reset(); path.moveTo(3f, 6f); path.lineTo(9.5f, 6f); path.lineTo(11.5f, 8f); path.lineTo(21f, 8f); path.lineTo(21f, 19f)
                path.lineTo(3f, 19f); path.close(); c.drawPath(path, p)
            }
            Glyph.LOCATION -> { fill(); path.reset(); path.moveTo(20f, 4f); path.lineTo(4f, 11f); path.lineTo(11.5f, 12.5f); path.lineTo(13f, 20f); path.close(); c.drawPath(path, p) }
            Glyph.ALARM -> {
                c.drawCircle(12f, 13f, 7.5f, p); poly(c, 12f, 9f, 12f, 13f, 15f, 15f)
                c.drawLine(4f, 5.5f, 6.5f, 3.5f, p); c.drawLine(20f, 5.5f, 17.5f, 3.5f, p)
            }
            Glyph.CALENDAR -> {
                r.set(3.5f, 5f, 20.5f, 20.5f); c.drawRoundRect(r, 3f, 3f, p); c.drawLine(3.5f, 10f, 20.5f, 10f, p)
                c.drawLine(8f, 3f, 8f, 6.5f, p); c.drawLine(16f, 3f, 16f, 6.5f, p)
            }
            Glyph.PHOTO -> {
                r.set(3f, 4.5f, 21f, 19.5f); c.drawRoundRect(r, 3f, 3f, p)
                poly(c, 5f, 17f, 10f, 11.5f, 14f, 15.5f); poly(c, 13f, 14.5f, 15.5f, 12f, 19f, 16f)
                fill(); c.drawCircle(15.5f, 8.5f, 1.6f, p)
            }
            Glyph.HOME -> {
                path.reset(); path.moveTo(3.5f, 11f); path.lineTo(12f, 4f); path.lineTo(20.5f, 11f); c.drawPath(path, p)
                path.reset(); path.moveTo(6f, 9.5f); path.lineTo(6f, 20f); path.lineTo(18f, 20f); path.lineTo(18f, 9.5f); c.drawPath(path, p)
            }
            Glyph.SHORTCUT -> { c.drawLine(7f, 17f, 17f, 7f, p); poly(c, 9f, 7f, 17f, 7f, 17f, 15f) }
            Glyph.LEAF -> {
                path.reset(); path.moveTo(5f, 19f); path.cubicTo(4f, 9f, 11f, 4f, 20f, 4f); path.cubicTo(20f, 13f, 15f, 20f, 5f, 19f); path.close()
                c.drawPath(path, p); c.drawLine(5f, 19f, 14f, 10f, p)
            }
        }
        c.restore()
    }

    private fun stroke() { p.style = Paint.Style.STROKE }
    private fun fill() { p.style = Paint.Style.FILL }

    private fun poly(c: Canvas, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        c.drawPath(path, p)
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, k: Float) {
        fill(); c.drawCircle(cx, cy, 4.5f * k, p)
        sunRays(c, cx, cy)
    }

    private fun sunRays(c: Canvas, cx: Float, cy: Float) {
        stroke()
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            c.drawLine(cx + 7f * cos(a).toFloat(), cy + 7f * sin(a).toFloat(), cx + 9.5f * cos(a).toFloat(), cy + 9.5f * sin(a).toFloat(), p)
        }
        fill(); c.drawCircle(cx, cy, 4.5f, p)
    }

    private fun cloud(c: Canvas, dx: Float, dy: Float, filled: Boolean) {
        if (filled) fill() else stroke()
        path.reset()
        path.moveTo(6.5f + dx, 18f + dy)
        path.cubicTo(2.5f + dx, 18f + dy, 2.5f + dx, 12f + dy, 6.8f + dx, 12f + dy)
        path.cubicTo(7.5f + dx, 6f + dy, 15.5f + dx, 5.5f + dy, 16.8f + dx, 10.8f + dy)
        path.cubicTo(21.5f + dx, 10.8f + dy, 21.5f + dx, 18f + dy, 17f + dx, 18f + dy)
        path.close()
        c.drawPath(path, p)
        stroke()
    }

    private fun star(c: Canvas, cx: Float, cy: Float, rr: Float) {
        path.reset()
        path.moveTo(cx, cy - rr)
        path.quadTo(cx + rr * 0.15f, cy - rr * 0.15f, cx + rr, cy)
        path.quadTo(cx + rr * 0.15f, cy + rr * 0.15f, cx, cy + rr)
        path.quadTo(cx - rr * 0.15f, cy + rr * 0.15f, cx - rr, cy)
        path.quadTo(cx - rr * 0.15f, cy - rr * 0.15f, cx, cy - rr)
        path.close()
        c.drawPath(path, p)
    }

    fun weatherGlyph(code: Int, day: Boolean): Glyph = when (code) {
        0 -> if (day) Glyph.SUN else Glyph.MOON
        1, 2 -> if (day) Glyph.CLOUD_SUN else Glyph.CLOUD
        3 -> Glyph.CLOUD
        45, 48 -> Glyph.FOG
        in 51..67, in 80..82 -> Glyph.RAIN
        in 71..77, 85, 86 -> Glyph.SNOW
        in 95..99 -> Glyph.BOLT
        else -> Glyph.CLOUD
    }
}

/** A glyph as a Drawable (for system places like the Quick Settings tile or ImageViews). */
class GlyphDrawable(private val glyph: Glyph, private var color: Int, private val weight: Float = 2f) : Drawable() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        Glyphs.draw(canvas, glyph, b.exactCenterX(), b.exactCenterY(), minOf(b.width(), b.height()).toFloat(), color, weight)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
