package dev.aura.launcher.gfx

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

/** Fast 3-pass box blur (≈ gaussian) for small opaque bitmaps. */
object Blur {
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        repeat(3) {
            horizontal(a, b, w, h, radius)
            vertical(b, a, w, h, radius)
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val pin = src[row + (x + r + 1).coerceAtMost(w - 1)]
                val pout = src[row + (x - r).coerceAtLeast(0)]
                rs += ((pin shr 16) and 0xFF) - ((pout shr 16) and 0xFF)
                gs += ((pin shr 8) and 0xFF) - ((pout shr 8) and 0xFF)
                bs += (pin and 0xFF) - (pout and 0xFF)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val pin = src[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val pout = src[(y - r).coerceAtLeast(0) * w + x]
                rs += ((pin shr 16) and 0xFF) - ((pout shr 16) and 0xFF)
                gs += ((pin shr 8) and 0xFF) - ((pout shr 8) and 0xFF)
                bs += (pin and 0xFF) - (pout and 0xFF)
            }
        }
    }
}

fun RectF.setSize(l: Float, t: Float, w: Float, h: Float): RectF { set(l, t, l + w, t + h); return this }
