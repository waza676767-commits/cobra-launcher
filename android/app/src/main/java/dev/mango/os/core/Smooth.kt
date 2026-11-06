package dev.mango.os.core

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import dev.mango.os.gfx.Blur

/**
 * Asks the display for its fastest refresh rate (120 Hz on the Galaxy A56) only while something
 * is moving, then lets it drop back when everything is still. Smooth motion, no idle cost.
 */
object FrameBoost {
    private var window: Window? = null
    private var highMode = 0
    private var boosted = false
    private val main = Handler(Looper.getMainLooper())
    private val calm = Runnable { set(false) }

    fun attach(a: Activity) {
        window = a.window
        highMode = try {
            val d = a.display ?: return
            val cur = d.mode
            d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate }?.modeId ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun detach(a: Activity) {
        if (window === a.window) {
            set(false)
            window = null
        }
    }

    /** Call on every animation frame / touch; stays fast until things have been still for a moment. */
    fun ping() {
        if (highMode == 0 || window == null) return
        if (!boosted) set(true)
        main.removeCallbacks(calm)
        main.postDelayed(calm, 900)
    }

    private fun set(on: Boolean) {
        val w = window ?: return
        if (on == boosted) return
        boosted = on
        try {
            val lp = w.attributes
            lp.preferredDisplayModeId = if (on) highMode else 0
            w.attributes = lp
        } catch (e: Exception) {
        }
    }
}

/**
 * A blurred still of the home screen, taken once when a folder / menu / search opens. Cross-fading
 * to it costs nothing per frame (a live blur would re-blur the whole screen 120 times a second).
 */
class FrozenBlurView(a: Activity) : View(a) {
    private var bmp: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    private var pending = false

    val ready get() = bmp != null

    fun capture(window: Window, of: View, done: () -> Unit) {
        if (pending || of.width == 0) return
        pending = true
        val w = (of.width / 8).coerceAtLeast(8)
        val h = (of.height / 8).coerceAtLeast(8)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val loc = IntArray(2)
        of.getLocationInWindow(loc)
        val src = Rect(loc[0], loc[1], loc[0] + of.width, loc[1] + of.height)
        try {
            PixelCopy.request(window, src, out, { result ->
                pending = false
                if (result == PixelCopy.SUCCESS) {
                    bmp = Blur.blur(out, 4)
                    invalidate()
                    done()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            pending = false
        }
    }

    fun clear() {
        bmp = null
        pending = false
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        dst.set(0, 0, width, height)
        c.drawBitmap(b, null, dst, paint)
    }
}
