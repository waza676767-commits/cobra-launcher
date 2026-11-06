package dev.aura.launcher.gfx

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import dev.aura.launcher.core.Prefs
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * The current wallpaper plus a tiny pre-blurred copy of it. Every glass surface samples the
 * blurred copy, so "frosted glass" costs one bitmap draw instead of a live blur each frame.
 */
object Backdrop {
    var full: Bitmap? = null
        private set
    var blurred: Bitmap? = null
        private set
    /** 0 = dark, 1 = bright: average brightness of the top of the wallpaper (status bar icons). */
    var topLuminance = 0f
        private set
    var screenW = 1080
        private set
    var screenH = 2340
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var requested = ""

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    fun customFile(ctx: Context) = File(ctx.filesDir, "wallpaper_custom.jpg")

    fun screenSize(ctx: Context): Pair<Int, Int> {
        val b = ctx.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return min(b.width(), b.height()) to max(b.width(), b.height())
    }

    fun ensure(ctx: Context) {
        val app = ctx.applicationContext
        val id = Prefs.wallpaper
        val (w, h) = screenSize(app)
        val want = "$id@${w}x$h"
        if (want == requested) return
        requested = want
        screenW = w; screenH = h
        exec.execute {
            val bmp = try { load(app, id, w, h) } catch (t: Throwable) { null } ?: return@execute
            val blur = makeBlur(bmp)
            val lum = topLum(blur)
            main.post {
                if (requested == want) {
                    full = bmp
                    blurred = blur
                    topLuminance = lum
                    listeners.forEach { it() }
                }
            }
        }
    }

    private fun load(ctx: Context, id: String, w: Int, h: Int): Bitmap {
        if (id.startsWith("custom")) {
            val f = customFile(ctx)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        }
        val spec = WallpaperArt.find(id)
        val cache = File(ctx.cacheDir, "wall_${spec.id}_v1_${w}x$h.jpg")
        if (cache.exists()) BitmapFactory.decodeFile(cache.path)?.let { return it }
        val bmp = WallpaperArt.render(spec, w, h)
        try {
            cache.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } catch (_: Exception) {
        }
        return bmp
    }

    fun makeBlur(src: Bitmap): Bitmap {
        val bw = max(8, src.width / 10)
        val bh = max(8, src.height / 10)
        val mid = Bitmap.createScaledBitmap(src, max(16, src.width / 3), max(16, src.height / 3), true)
        val small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.4f) })
        Canvas(small).drawBitmap(mid, null, Rect(0, 0, bw, bh), p)
        return Blur.blur(small, 5)
    }

    private fun topLum(b: Bitmap): Float {
        val h = max(1, b.height / 7)
        var sum = 0f
        var n = 0
        for (y in 0 until h step 2) for (x in 0 until b.width step 2) {
            val c = b.getPixel(x, y)
            sum += (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
            n++
        }
        return if (n == 0) 0f else sum / n
    }

    /** Crops + scales a picked photo to the screen and makes it the wallpaper. */
    fun importPhoto(ctx: Context, uri: Uri, done: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        exec.execute {
            val ok = try {
                val (w, h) = screenSize(app)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val src = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: throw IllegalStateException("decode")
                val scale = max(w / src.width.toFloat(), h / src.height.toFloat())
                val cw = (w / scale).toInt().coerceAtMost(src.width)
                val ch = (h / scale).toInt().coerceAtMost(src.height)
                val l = (src.width - cw) / 2
                val t = (src.height - ch) / 2
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(out).drawBitmap(src, Rect(l, t, l + cw, t + ch), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                customFile(app).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 93, it) }
                true
            } catch (t: Throwable) {
                false
            }
            main.post {
                if (ok) Prefs.wallpaper = "custom:" + System.currentTimeMillis()
                done(ok)
            }
        }
    }

    /** Also hands the current wallpaper to the system (One UI lock screen / AOD / recents). */
    fun applyToSystem(ctx: Context, done: (Boolean) -> Unit) {
        val bmp = full ?: return done(false)
        val app = ctx.applicationContext
        exec.execute {
            val ok = try {
                WallpaperManager.getInstance(app).setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                true
            } catch (t: Throwable) {
                false
            }
            main.post { done(ok) }
        }
    }

    private val thumbs = HashMap<String, Bitmap>()

    /** Small preview for the wallpaper picker. */
    fun thumbnail(id: String, w: Int, h: Int, done: (Bitmap) -> Unit) {
        thumbs[id]?.let { return done(it) }
        exec.execute {
            val b = WallpaperArt.render(WallpaperArt.find(id), w, h)
            main.post { thumbs[id] = b; done(b) }
        }
    }
}
