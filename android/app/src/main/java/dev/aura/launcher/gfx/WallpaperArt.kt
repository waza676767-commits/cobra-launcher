package dev.aura.launcher.gfx

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import dev.aura.launcher.core.argb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Aura's built-in wallpapers, painted in code: soft colour fields with "liquid glass" petals or
 * waves on top (inner glow + bright rim + soft shadow). Rendered once per screen size and cached.
 */
object WallpaperArt {
    class Blob(val x: Float, val y: Float, val r: Float, val color: Int, val alpha: Float)

    class Spec(
        val id: String,
        val name: String,
        val top: Int,
        val bottom: Int,
        val blobs: List<Blob>,
        val shape: String,           // petals | waves | none
        val palette: IntArray,
        val originX: Float = 0.5f,
        val originY: Float = 1.12f,
        val angleFrom: Float = -58f,
        val angleTo: Float = 58f,
        val count: Int = 7,
        val length: Float = 0.82f,   // of screen height
        val width: Float = 0.30f,    // of screen width
        val light: Boolean = false
    )

    private fun c(hex: Long) = hex.toInt()

    val all = listOf(
        Spec(
            "lagoon", "Lagoon", c(0xFF031033), c(0xFF0A2B66),
            listOf(
                Blob(0.15f, 0.12f, 0.75f, c(0xFF1E5BFF), 0.75f),
                Blob(0.9f, 0.4f, 0.65f, c(0xFF00C2FF), 0.55f),
                Blob(0.5f, 0.98f, 0.9f, c(0xFF00E0B0), 0.45f)
            ),
            "petals", intArrayOf(c(0xFF2A6BFF), c(0xFF00B3FF), c(0xFF24E0D0), c(0xFF5A8CFF), c(0xFF3FD0FF))
        ),
        Spec(
            "bloom", "Bloom", c(0xFF1A0630), c(0xFF3A0B4A),
            listOf(
                Blob(0.85f, 0.15f, 0.7f, c(0xFFFF4FA0), 0.6f),
                Blob(0.1f, 0.55f, 0.7f, c(0xFF8A4DFF), 0.6f),
                Blob(0.6f, 0.95f, 0.8f, c(0xFFFF8A4C), 0.5f)
            ),
            "petals", intArrayOf(c(0xFFFF5FA8), c(0xFFB06BFF), c(0xFFFF9A6B), c(0xFFFF7AD0), c(0xFF8F7BFF)),
            originX = 0.12f, originY = 1.1f, angleFrom = -18f, angleTo = 72f, count = 6, length = 0.86f
        ),
        Spec(
            "ember", "Ember", c(0xFF1C0503), c(0xFF3D0E05),
            listOf(
                Blob(0.8f, 0.2f, 0.7f, c(0xFFFF5A1F), 0.7f),
                Blob(0.15f, 0.45f, 0.6f, c(0xFFFF2D55), 0.55f),
                Blob(0.5f, 0.9f, 0.8f, c(0xFFFFB020), 0.5f)
            ),
            "waves", intArrayOf(c(0xFFFF6A2B), c(0xFFFF3B5C), c(0xFFFFA62B), c(0xFFFF5476), c(0xFFFFC34D))
        ),
        Spec(
            "meadow", "Meadow", c(0xFF021A14), c(0xFF06352A),
            listOf(
                Blob(0.2f, 0.2f, 0.7f, c(0xFF00B37A), 0.55f),
                Blob(0.9f, 0.6f, 0.6f, c(0xFF3BE3C2), 0.5f),
                Blob(0.4f, 0.95f, 0.8f, c(0xFF8BE36B), 0.35f)
            ),
            "petals", intArrayOf(c(0xFF19D48F), c(0xFF46E6C6), c(0xFF8FE870), c(0xFF22B8A0), c(0xFF5FF0B0)),
            originX = 0.88f, originY = 1.1f, angleFrom = -72f, angleTo = 16f, count = 6, length = 0.86f
        ),
        Spec(
            "tide", "Tide", c(0xFF8FD0FF), c(0xFF1A5BE0),
            listOf(
                Blob(0.2f, 0.1f, 0.7f, c(0xFFE4F4FF), 0.7f),
                Blob(0.9f, 0.5f, 0.6f, c(0xFF4FA8FF), 0.5f)
            ),
            "waves", intArrayOf(c(0xFF6BC1FF), c(0xFF3D8DFF), c(0xFF2465F0), c(0xFF8AD6FF), c(0xFF1F4FD0))
        ),
        Spec(
            "daybreak", "Daybreak", c(0xFFFDE7D9), c(0xFFD6E2FF),
            listOf(
                Blob(0.15f, 0.2f, 0.7f, c(0xFFFFB59E), 0.7f),
                Blob(0.9f, 0.45f, 0.6f, c(0xFFB9A3FF), 0.6f),
                Blob(0.5f, 0.95f, 0.8f, c(0xFF9ED8FF), 0.6f)
            ),
            "petals", intArrayOf(c(0xFFFFB199), c(0xFFC4A8FF), c(0xFF9ED2FF), c(0xFFFFCFA8), c(0xFFB5C7FF)),
            light = true
        ),
        Spec(
            "graphite", "Graphite", c(0xFF050506), c(0xFF141417),
            listOf(
                Blob(0.8f, 0.2f, 0.7f, c(0xFF3A3A44), 0.6f),
                Blob(0.2f, 0.85f, 0.7f, c(0xFF2A2A30), 0.6f)
            ),
            "waves", intArrayOf(c(0xFF4A4A54), c(0xFF6B6B78), c(0xFF2E2E36), c(0xFF5A5A66), c(0xFF3C3C46))
        ),
        Spec(
            "midnight", "Midnight", c(0xFF000000), c(0xFF000000),
            listOf(
                Blob(0.5f, 1.05f, 0.9f, c(0xFF0A2A6B), 0.55f),
                Blob(0.1f, 0.7f, 0.5f, c(0xFF1B0F4A), 0.4f)
            ),
            "none", intArrayOf()
        )
    )

    fun find(id: String): Spec = all.firstOrNull { it.id == id } ?: all[0]

    fun render(spec: Spec, w: Int, h: Int): Bitmap {
        val s = w / 1080f
        // 1. soft colour field, painted small and scaled up (cheap, and naturally smooth)
        val sw = max(24, w / 6)
        val sh = max(48, h / 6)
        val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val sc = Canvas(small)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, sh.toFloat(), spec.top, spec.bottom, Shader.TileMode.CLAMP)
        sc.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), p)
        for (b in spec.blobs) {
            val cx = b.x * sw
            val cy = b.y * sh
            val r = b.r * sw * 1.4f
            p.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(argb(b.alpha, b.color), argb(b.alpha * 0.45f, b.color), argb(0f, b.color)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            sc.drawCircle(cx, cy, r, p)
        }
        val soft = Blur.blur(small, 3)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(soft, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))

        val rnd = Random(spec.id.hashCode())
        when (spec.shape) {
            "petals" -> petals(c, spec, w, h, s, rnd)
            "waves" -> waves(c, spec, w, h, s, rnd)
        }
        grain(c, w, h, spec.light, rnd)
        return out
    }

    private fun petals(c: Canvas, spec: Spec, w: Int, h: Int, s: Float, rnd: Random) {
        val ox = spec.originX * w
        val oy = spec.originY * h
        val n = spec.count
        val angles = (0 until n).map { i -> spec.angleFrom + (spec.angleTo - spec.angleFrom) * i / (n - 1).coerceAtLeast(1) }
        val mid = (spec.angleFrom + spec.angleTo) / 2f
        val order = angles.indices.sortedByDescending { abs(angles[it] - mid) }
        for ((k, i) in order.withIndex()) {
            val ang = angles[i] + rnd.nextFloat() * 6f - 3f
            val len = h * spec.length * (0.82f + 0.2f * rnd.nextFloat()) * (1f - 0.18f * abs(angles[i] - mid) / 90f)
            val wid = w * spec.width * (0.85f + 0.3f * rnd.nextFloat())
            val path = Path()
            path.moveTo(0f, 0f)
            path.cubicTo(-wid * 0.95f, -len * 0.22f, -wid * 0.7f, -len * 0.8f, 0f, -len)
            path.cubicTo(wid * 0.7f, -len * 0.8f, wid * 0.95f, -len * 0.22f, 0f, 0f)
            path.close()
            val m = Matrix()
            m.postRotate(ang)
            m.postTranslate(ox, oy)
            path.transform(m)
            val rad = Math.toRadians(ang.toDouble())
            val tipX = ox + (sin(rad) * len).toFloat()
            val tipY = oy - (cos(rad) * len).toFloat()
            val color = spec.palette[(k + i) % spec.palette.size]
            glassShape(c, path, ox, oy, tipX, tipY, color, s, spec.light)
        }
    }

    private fun waves(c: Canvas, spec: Spec, w: Int, h: Int, s: Float, rnd: Random) {
        val n = 5
        for (i in 0 until n) {
            val baseY = h * (0.38f + i * 0.13f)
            val amp = h * (0.05f + rnd.nextFloat() * 0.05f)
            val path = Path()
            path.moveTo(-60f * s, baseY)
            val segs = 2
            val segW = (w + 120f * s) / segs
            var x = -60f * s
            var dir = if (i % 2 == 0) 1f else -1f
            for (k in 0 until segs) {
                path.cubicTo(x + segW * 0.35f, baseY - amp * dir, x + segW * 0.65f, baseY + amp * dir, x + segW, baseY + amp * 0.2f * dir)
                x += segW
                dir = -dir
            }
            path.lineTo(w + 60f * s, h + 80f * s)
            path.lineTo(-60f * s, h + 80f * s)
            path.close()
            val color = spec.palette[i % spec.palette.size]
            glassShape(c, path, w / 2f, h.toFloat(), w / 2f, baseY - amp, color, s, spec.light)
        }
    }

    /** Fill + soft shadow + inner glow + rim highlight: the "liquid glass" treatment. */
    private fun glassShape(c: Canvas, path: Path, bx: Float, by: Float, tx: Float, ty: Float, color: Int, s: Float, light: Boolean) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (light) 0x40345080 else 0x73000000
            maskFilter = BlurMaskFilter(42f * s, BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(0f, 18f * s)
        c.drawPath(path, shadow)
        c.restore()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(bx, by, tx, ty, argb(0.96f, color), argb(if (light) 0.6f else 0.72f, lighten(color, 0.1f)), Shader.TileMode.CLAMP)
        }
        c.drawPath(path, fill)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (light) 0x73FFFFFF else 0x3DFFFFFF
            maskFilter = BlurMaskFilter(22f * s, BlurMaskFilter.Blur.INNER)
        }
        c.drawPath(path, glow)

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f * s
            shader = LinearGradient(tx, ty, bx, by, 0xD9FFFFFF.toInt(), 0x0DFFFFFF, Shader.TileMode.CLAMP)
        }
        c.drawPath(path, rim)
    }

    private fun lighten(c: Int, t: Float): Int {
        val r = Color.red(c) + ((255 - Color.red(c)) * t).toInt()
        val g = Color.green(c) + ((255 - Color.green(c)) * t).toInt()
        val b = Color.blue(c) + ((255 - Color.blue(c)) * t).toInt()
        return Color.rgb(r, g, b)
    }

    /** A whisper of film grain so smooth gradients don't band on the AMOLED panel. */
    private fun grain(c: Canvas, w: Int, h: Int, light: Boolean, rnd: Random) {
        val n = 128
        val px = IntArray(n * n)
        for (i in px.indices) {
            val white = rnd.nextBoolean()
            val a = rnd.nextInt(if (light) 8 else 10)
            px[i] = Color.argb(a, if (white) 255 else 0, if (white) 255 else 0, if (white) 255 else 0)
        }
        val tile = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        val p = Paint()
        p.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }
}
