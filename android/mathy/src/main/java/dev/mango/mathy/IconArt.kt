package dev.mango.mathy

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Small shared helpers: units, colours, the squircle, fonts. */
object U {
    var density = 3f
    fun init(ctx: Context) { density = ctx.resources.displayMetrics.density }
    fun dark(ctx: Context) = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val faces = HashMap<Int, Typeface>()
    fun font(w: Int): Typeface = faces.getOrPut(w) { Typeface.create(Typeface.DEFAULT, w, false) }

    private val unit: Path by lazy {
        val p = Path()
        val n = 5.0
        for (i in 0..180) {
            val t = 2.0 * Math.PI * i / 180
            val x = sign(cos(t)) * abs(cos(t)).pow(2.0 / n)
            val y = sign(sin(t)) * abs(sin(t)).pow(2.0 / n)
            val px = (0.5 + 0.5 * x).toFloat()
            val py = (0.5 + 0.5 * y).toFloat()
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        p
    }

    fun squircle(out: Path, l: Float, t: Float, s: Float) {
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate(l, t)
        out.reset()
        unit.transform(m, out)
    }
}

val Number.dp: Float get() = toFloat() * U.density
val Number.dpi: Int get() = Math.round(toFloat() * U.density)

/** Palette for colours in the UI (follows the phone's light / dark mode). */
class Pal(dark: Boolean) {
    val bg = if (dark) Color.BLACK else 0xFFF2F2F7.toInt()
    val group = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
    val text = if (dark) Color.WHITE else Color.BLACK
    val text2 = if (dark) 0x99EBEBF5.toInt() else 0x993C3C43.toInt()
    val text3 = if (dark) 0x4DEBEBF5 else 0x4D3C3C43
    val sep = if (dark) 0x30FFFFFF else 0x24000000
    val fill = if (dark) 0x3D787880 else 0x1F787880
    val accent = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
    val red = if (dark) 0xFFFF453A.toInt() else 0xFFFF3B30.toInt()
    val green = if (dark) 0xFF30D158.toInt() else 0xFF34C759.toInt()
}

/** Everything about drawing icons: the real ones, and the custom ones you make. */
object IconArt {
    const val SIZE = 256

    /** Gradient backgrounds for symbol icons (top, bottom). */
    val gradients = listOf(
        0xFF5B8CFF to 0xFF2A35C8, 0xFFFFB340 to 0xFFFF6A00, 0xFFFF6B8A to 0xFFE0245E, 0xFF5BE38A to 0xFF16A34A,
        0xFFB18CFF to 0xFF6D28D9, 0xFF5FE1F2 to 0xFF0891B2, 0xFFFFE066 to 0xFFF59E0B, 0xFF9CA3AF to 0xFF374151,
        0xFFFFFFFF to 0xFFE5E7EB, 0xFF3B3B3F to 0xFF0B0B0C, 0xFFF472B6 to 0xFFA21CAF, 0xFFA3E635 to 0xFF4D7C0F
    ).map { (a, b) -> a.toInt() to b.toInt() }

    /** Ready-made looks: symbol, gradient index, name. */
    val presets = listOf(
        Triple("📘", 0, "Math"),
        Triple("🧮", 1, "Calculator"),
        Triple("📝", 6, "Notes"),
        Triple("📐", 3, "Geometry"),
        Triple("🔢", 4, "Numbers"),
        Triple("📚", 2, "Homework"),
        Triple("🧪", 5, "Science"),
        Triple("🌍", 5, "Geography"),
        Triple("📖", 8, "Reading"),
        Triple("✏️", 9, "Practice")
    )

    val emojis = listOf(
        "📘", "📚", "📖", "📝", "✏️", "📐", "📏", "🧮", "🔢", "➗", "✖️", "➕", "🧪", "🔬", "🌍", "🗺️",
        "🎓", "🏫", "🗒️", "📊", "📈", "📅", "⏰", "💡", "⭐", "❤️", "🎮", "🎵", "📷", "💬", "🌙", "🔥"
    )

    /** Emoji or up to 3 letters on a gradient: the "Symbol" style. */
    fun symbol(text: String, gradient: Int): Bitmap {
        val s = SIZE
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val (top, bottom) = gradients[gradient.coerceIn(0, gradients.size - 1)]
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, s.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        val t = text.trim().ifEmpty { "?" }
        val letters = t.all { it.isLetterOrDigit() || it == ' ' }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.textAlign = Paint.Align.CENTER
        if (letters) {
            val light = luminance(top) > 0.7f
            tp.color = if (light) 0xFF1C1C1E.toInt() else Color.WHITE
            tp.typeface = U.font(700)
            tp.textSize = s * (if (t.length <= 1) 0.56f else if (t.length == 2) 0.44f else 0.34f)
            tp.setShadowLayer(s * 0.03f, 0f, s * 0.012f, 0x40000000)
        } else {
            tp.textSize = s * 0.55f
            tp.setShadowLayer(s * 0.035f, 0f, s * 0.018f, 0x4D000000)
        }
        val fm = tp.fontMetrics
        val y = s / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(t, s / 2f, y, tp)
        // soft glass sheen at the top
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
        sheen.shader = LinearGradient(0f, 0f, 0f, s * 0.55f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s.toFloat(), s * 0.55f, sheen)
        return b
    }

    private fun luminance(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    /** A photo, centre-cropped to a square. */
    fun photo(ctx: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        src?.let {
            val side = min(it.width, it.height)
            val l = (it.width - side) / 2
            val t = (it.height - side) / 2
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(it, Rect(l, t, l + side, t + side), Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
            out
        }
    } catch (e: Exception) {
        null
    }

    /** Another app's icon, full square (so it can stand in for this app). */
    fun fromApp(app: AppInfo): Bitmap {
        val d = try { app.info.getIcon(640) } catch (e: Exception) { null }
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        if (d != null) drawFull(Canvas(out), d, SIZE)
        return out
    }

    private fun drawFull(c: Canvas, d: Drawable, s: Int) {
        if (d is AdaptiveIconDrawable) {
            val o = s / 4
            d.background?.let { it.setBounds(-o, -o, s + o, s + o); it.draw(c) }
            d.foreground?.let { it.setBounds(-o, -o, s + o, s + o); it.draw(c) }
        } else {
            c.drawColor(Color.WHITE)
            val i = (s * 0.14f).toInt()
            d.setBounds(i, i, s - i, s - i)
            d.draw(c)
        }
    }

    /** Masks a square bitmap to the iOS-style squircle. */
    fun masked(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val sh = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val m = Matrix()
        m.setScale(size / src.width.toFloat(), size / src.height.toFloat())
        sh.setLocalMatrix(m)
        p.shader = sh
        val path = Path()
        U.squircle(path, 0f, 0f, size.toFloat())
        c.drawPath(path, p)
        return out
    }

    /** What an app looks like right now (custom icon if you made one). */
    fun current(ctx: Context, app: AppInfo, size: Int): Bitmap {
        val custom = Store.loadIcon(ctx, app.key)
        if (custom != null) return masked(custom, size)
        val full = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val d = try { app.info.getIcon(640) } catch (e: Exception) { null }
        if (d != null) drawFull(Canvas(full), d, SIZE)
        return masked(full, size)
    }

    fun original(app: AppInfo, size: Int): Bitmap = masked(fromApp(app), size)
}
