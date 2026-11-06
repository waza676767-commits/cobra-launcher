package dev.mango.os.gfx

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import dev.mango.os.core.Theme
import dev.mango.os.core.dp

/**
 * Draws one "liquid glass" surface: the blurred wallpaper that sits behind the shape,
 * a light tint, and a specular rim that's bright on the top-left edge.
 * Each glass view owns one painter (it caches its shaders for its own size).
 */
class GlassPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var shaderSource: Bitmap? = null
    private var shader: BitmapShader? = null
    private val m = Matrix()
    private val loc = IntArray(2)
    private var rimW = -1f
    private var rimH = -1f
    private var rimDark = false
    private val rect = RectF()

    /** Extra colour on top of the default tint (e.g. a pressed state). */
    var overlay = 0
    /** 0..1, fades the whole surface. */
    var alpha = 1f
    var rimStrength = 1f
    /** Tint override; 0 = theme default. */
    var tintColor = 0

    private fun prepare(v: View, w: Float, h: Float, dx: Float, dy: Float) {
        val blur = Backdrop.blurred
        if (blur == null || Theme.reduceTransparency) {
            fill.shader = null
            fill.color = Theme.solidPanel
        } else {
            if (shaderSource !== blur) {
                shaderSource = blur
                shader = BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            fill.shader = shader
            v.getLocationInWindow(loc)
            val sx = Backdrop.screenW / blur.width.toFloat()
            val sy = Backdrop.screenH / blur.height.toFloat()
            m.setScale(sx, sy)
            m.postTranslate(-loc[0] - dx, -loc[1] - dy)
            shader!!.setLocalMatrix(m)
        }
        fill.alpha = (255 * alpha).toInt()
        tint.color = if (tintColor != 0) tintColor else if (Theme.reduceTransparency) 0 else Theme.glassTint
        tint.alpha = (tint.alpha * alpha).toInt()
        if (w != rimW || h != rimH || rimDark != Theme.dark) {
            rimW = w; rimH = h; rimDark = Theme.dark
            val strong = if (Theme.dark) 0x8CFFFFFF.toInt() else 0xE6FFFFFF.toInt()
            val soft = if (Theme.dark) 0x14FFFFFF else 0x33FFFFFF
            val back = if (Theme.dark) 0x40FFFFFF else 0x80FFFFFF.toInt()
            rim.shader = LinearGradient(
                0f, 0f, w * 0.6f, h,
                intArrayOf(strong, soft, soft, back), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
        }
        rim.strokeWidth = 1.dp.coerceAtLeast(1.5f)
        rim.alpha = (255 * alpha * rimStrength).toInt().coerceIn(0, 255)
    }

    /** [dx]/[dy]: where the canvas origin is relative to the view's top-left (scrolling, translate). */
    fun drawRoundRect(c: Canvas, v: View, l: Float, t: Float, r: Float, b: Float, radius: Float, dx: Float = 0f, dy: Float = 0f) {
        prepare(v, r - l, b - t, dx, dy)
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, fill)
        if (tint.alpha > 0) c.drawRoundRect(rect, radius, radius, tint)
        if (overlay != 0) { tint.color = overlay; c.drawRoundRect(rect, radius, radius, tint) }
        val inset = rim.strokeWidth / 2f
        rect.inset(inset, inset)
        c.save()
        c.translate(l, t)
        rect.offset(-l, -t)
        c.drawRoundRect(rect, radius - inset, radius - inset, rim)
        c.restore()
    }

    /** [path] in canvas coordinates; its bounds start at (l, t) with size w×h. */
    fun drawPath(c: Canvas, v: View, path: Path, l: Float, t: Float, w: Float, h: Float, dx: Float = 0f, dy: Float = 0f) {
        prepare(v, w, h, dx, dy)
        c.drawPath(path, fill)
        if (tint.alpha > 0) c.drawPath(path, tint)
        if (overlay != 0) { tint.color = overlay; c.drawPath(path, tint) }
        c.save()
        c.translate(l, t)
        path.offset(-l, -t)
        c.drawPath(path, rim)
        path.offset(l, t)
        c.restore()
    }
}
