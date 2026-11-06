package dev.mango.os.gfx

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** The continuous-curve "squircle" app icons use (a superellipse, n = 5). */
object Squircle {
    private val unit: Path by lazy {
        val p = Path()
        val n = 5.0
        val steps = 180
        for (i in 0..steps) {
            val t = 2.0 * Math.PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = sign(c) * abs(c).pow(2.0 / n)
            val y = sign(s) * abs(s).pow(2.0 / n)
            val px = (0.5 + 0.5 * x).toFloat()
            val py = (0.5 + 0.5 * y).toFloat()
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        p
    }
    private val m = android.graphics.Matrix()

    /** Writes a squircle filling [l,t,l+size,t+size] into [out]. */
    fun into(out: Path, l: Float, t: Float, size: Float) {
        m.setScale(size, size)
        m.postTranslate(l, t)
        out.reset()
        unit.transform(m, out)
    }

    fun path(size: Float): Path = Path().also { into(it, 0f, 0f, size) }
}

/** Fast 3-pass box blur (≈ gaussian). Keeps transparency (blurs premultiplied colour). */
object Blur {
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val a = IntArray(n); val r = IntArray(n); val g = IntArray(n); val b = IntArray(n)
        for (i in 0 until n) {
            val p = px[i]
            val al = (p ushr 24) and 0xFF
            a[i] = al
            r[i] = ((p shr 16) and 0xFF) * al / 255
            g[i] = ((p shr 8) and 0xFF) * al / 255
            b[i] = (p and 0xFF) * al / 255
        }
        val t = IntArray(n)
        for (ch in arrayOf(a, r, g, b)) repeat(3) {
            horizontal(ch, t, w, h, radius)
            vertical(t, ch, w, h, radius)
        }
        for (i in 0 until n) {
            val al = a[i].coerceIn(0, 255)
            px[i] = if (al == 0) 0 else (al shl 24) or
                ((r[i] * 255 / al).coerceIn(0, 255) shl 16) or
                ((g[i] * 255 / al).coerceIn(0, 255) shl 8) or
                (b[i] * 255 / al).coerceIn(0, 255)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (i in -r..r) sum += src[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                sum += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }
}

fun RectF.setSize(l: Float, t: Float, w: Float, h: Float): RectF { set(l, t, l + w, t + h); return this }
