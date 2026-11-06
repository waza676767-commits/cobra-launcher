package dev.mango.os.gfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import dev.mango.os.data.AppEntry
import dev.mango.os.data.AppRepository
import java.util.concurrent.Executors

/**
 * Turns app icons into Mango icons: squircle mask plus one of four looks
 * (Colourful, Dark, Clear glass, Tinted). Rendered off the main thread, cached in memory.
 */
object IconFactory {
    var size = 0
        private set
    var style = "colorful"
        private set
    var tint = 0xFF64D2FF.toInt()
        private set
    private var generation = 0

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val waiting = HashMap<String, MutableList<(Bitmap) -> Unit>>()
    private val exec = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    fun init(ctx: Context) { app = ctx.applicationContext }

    /** Returns true when something changed (callers then refresh their icons). */
    fun configure(sizePx: Int, style: String, tint: Int): Boolean {
        if (sizePx == size && style == this.style && tint == this.tint) return false
        size = sizePx
        this.style = style
        this.tint = tint
        generation++
        cache.evictAll()
        waiting.clear()
        return true
    }

    val isClear get() = style == "clear"

    fun peek(key: String): Bitmap? = cache.get(key)

    /** Cached bitmap now, or null and [cb] later (on the main thread). */
    fun get(key: String, cb: (Bitmap) -> Unit): Bitmap? {
        if (size <= 0) return null
        cache.get(key)?.let { return it }
        val list = waiting[key]
        if (list != null) { list.add(cb); return null }
        waiting[key] = mutableListOf(cb)
        val gen = generation
        val s = size
        val st = style
        val t = tint
        val entry = AppRepository[key]
        exec.execute {
            val bmp = try {
                if (entry != null) render(entry, s, st, t) else null
            } catch (e: Throwable) {
                null
            }
            main.post {
                if (gen != generation) return@post
                val cbs = waiting.remove(key) ?: return@post
                if (bmp != null) {
                    cache.put(key, bmp)
                    cbs.forEach { it(bmp) }
                }
            }
        }
        return null
    }

    fun preload(keys: Collection<String>) {
        for (k in keys) if (cache.get(k) == null) get(k) {}
    }

    // ------------------------------------------------------------------ rendering

    fun render(entry: AppEntry, s: Int, style: String, tint: Int): Bitmap {
        val d: Drawable = try {
            entry.info.getIcon(640) ?: app.packageManager.defaultActivityIcon
        } catch (e: Exception) {
            app.packageManager.defaultActivityIcon
        }
        return renderDrawable(d, s, style, tint)
    }

    fun renderDrawable(d: Drawable, s: Int, style: String, tint: Int): Bitmap {
        val content = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(content)
        val adaptive = d as? AdaptiveIconDrawable
        val o = s / 4
        when (style) {
            "dark" -> {
                darkBackground(c, s)
                if (adaptive != null) layer(c, adaptive.foreground, s, o) else legacy(c, d, s, onDark = true)
            }
            "tinted" -> {
                darkBackground(c, s)
                glyph(c, d, s, o, tint, keepShade = true)
            }
            "clear" -> {
                // white glyph with a soft shadow so it reads on light and dark glass
                val g = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                glyph(Canvas(g), d, s, o, Color.WHITE, keepShade = false)
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = android.graphics.BlurMaskFilter(s * 0.035f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
                val off = IntArray(2)
                val shadow = g.extractAlpha(sp, off)
                sp.maskFilter = null
                sp.color = 0x59000000
                c.drawBitmap(shadow, off[0].toFloat(), off[1] + s * 0.015f, sp)
                c.drawBitmap(g, 0f, 0f, null)
                shadow.recycle(); g.recycle()
            }
            else -> {
                if (adaptive != null) {
                    layer(c, adaptive.background, s, o)
                    layer(c, adaptive.foreground, s, o)
                } else legacy(c, d, s, onDark = false)
            }
        }
        // squircle mask with anti-aliased edges
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val path = Squircle.path(s.toFloat())
        oc.drawPath(path, p)
        if (style != "clear") gloss(oc, path, s)
        content.recycle()
        return out
    }

    private fun layer(c: Canvas, l: Drawable?, s: Int, o: Int) {
        l ?: return
        l.setBounds(-o, -o, s + o, s + o)
        l.draw(c)
    }

    private fun darkBackground(c: Canvas, s: Int) {
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), 0xFF2C2C30.toInt(), 0xFF101012.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
    }

    /** Old-style (non adaptive) icon: full-bleed if it's already square, else on a plate. */
    private fun legacy(c: Canvas, d: Drawable, s: Int, onDark: Boolean) {
        if (isFullBleed(d)) {
            d.setBounds(0, 0, s, s)
            d.draw(c)
            return
        }
        if (!onDark) c.drawColor(Color.WHITE)
        val inset = (s * 0.16f).toInt()
        d.setBounds(inset, inset, s - inset, s - inset)
        d.draw(c)
    }

    private fun isFullBleed(d: Drawable): Boolean {
        val n = 48
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(b))
        val k = 3
        val opaque = listOf(b.getPixel(k, n / 2), b.getPixel(n - 1 - k, n / 2), b.getPixel(n / 2, k), b.getPixel(n / 2, n - 1 - k),
            b.getPixel(k + 2, k + 2), b.getPixel(n - 3 - k, n - 3 - k)).all { Color.alpha(it) > 230 }
        b.recycle()
        return opaque
    }

    /**
     * One-colour glyph: the app's monochrome layer when it has one (Android 13+ themed icons),
     * otherwise its foreground silhouette or a brightness-mapped version of the icon.
     */
    private fun glyph(c: Canvas, d: Drawable, s: Int, o: Int, color: Int, keepShade: Boolean) {
        val adaptive = d as? AdaptiveIconDrawable
        val mono = if (android.os.Build.VERSION.SDK_INT >= 33) adaptive?.monochrome else null
        if (mono != null) {
            mono.mutate()
            mono.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            val shrink = (s * 0.04f).toInt()
            mono.setBounds(-o + shrink, -o + shrink, s + o - shrink, s + o - shrink)
            mono.draw(c)
            mono.colorFilter = null
            return
        }
        val tmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val tc = Canvas(tmp)
        var silhouette = false
        if (adaptive?.foreground != null) {
            layer(tc, adaptive.foreground, s, o)
            silhouette = coverage(tmp) < 0.55f
        } else {
            val inset = (s * 0.18f).toInt()
            d.setBounds(inset, inset, s - inset, s - inset)
            d.draw(tc)
        }
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        p.colorFilter = if (silhouette && !keepShade) {
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        } else {
            // brightness → tint colour (keeps the logo readable)
            val lift = if (keepShade) 0.45f else 0.3f
            val k = 1f - lift
            ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        r * k * 0.299f, r * k * 0.587f, r * k * 0.114f, 0f, r * lift * 255f,
                        g * k * 0.299f, g * k * 0.587f, g * k * 0.114f, 0f, g * lift * 255f,
                        b * k * 0.299f, b * k * 0.587f, b * k * 0.114f, 0f, b * lift * 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (!silhouette && adaptive != null) {
            // the foreground covers the whole icon; shrink it a bit so it reads as a glyph
            val inset = (s * 0.1f).toInt()
            c.drawBitmap(tmp, null, Rect(inset, inset, s - inset, s - inset), p)
        } else {
            c.drawBitmap(tmp, 0f, 0f, p)
        }
        tmp.recycle()
    }

    private fun coverage(b: Bitmap): Float {
        val n = 24
        var opaque = 0
        val w = b.width
        val step = w / n
        if (step <= 0) return 0f
        val from = w / 6
        val to = w - w / 6
        var total = 0
        var y = from
        while (y < to) {
            var x = from
            while (x < to) {
                if (Color.alpha(b.getPixel(x, y)) > 200) opaque++
                total++
                x += step
            }
            y += step
        }
        return if (total == 0) 0f else opaque / total.toFloat()
    }

    /** Subtle top sheen + rim light, the "liquid" finish. */
    private fun gloss(c: Canvas, path: Path, s: Int) {
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
        sheen.shader = LinearGradient(0f, 0f, 0f, s * 0.55f, 0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawPath(path, sheen)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG)
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = s * 0.018f
        rim.shader = LinearGradient(0f, 0f, s * 0.7f, s.toFloat(), intArrayOf(0x73FFFFFF, 0x0AFFFFFF, 0x26FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val inset = rim.strokeWidth / 2f
        val p2 = Path()
        Squircle.into(p2, inset, inset, s - inset * 2)
        c.drawPath(p2, rim)
    }
}
