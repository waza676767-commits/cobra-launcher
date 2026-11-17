package dev.mango.mathy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** Line glyphs used in Mathy (24-unit grid). */
object G {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()

    fun chevron(c: Canvas, cx: Float, cy: Float, size: Float, color: Int, left: Boolean = false) {
        val s = size / 24f
        p.color = color; p.strokeWidth = 2.6f * s
        path.reset()
        if (left) { path.moveTo(cx + 3.5f * s, cy - 7 * s); path.lineTo(cx - 3.5f * s, cy); path.lineTo(cx + 3.5f * s, cy + 7 * s) }
        else { path.moveTo(cx - 3.5f * s, cy - 7 * s); path.lineTo(cx + 3.5f * s, cy); path.lineTo(cx - 3.5f * s, cy + 7 * s) }
        c.drawPath(path, p)
    }

    fun search(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size / 24f
        p.color = color; p.strokeWidth = 2.4f * s
        c.drawCircle(cx - 1.5f * s, cy - 1.5f * s, 6.5f * s, p)
        c.drawLine(cx + 3.5f * s, cy + 3.5f * s, cx + 8.5f * s, cy + 8.5f * s, p)
    }

    fun check(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val s = size / 24f
        p.color = color; p.strokeWidth = 2.8f * s
        path.reset(); path.moveTo(cx - 7 * s, cy + 0.5f * s); path.lineTo(cx - 2 * s, cy + 5.5f * s); path.lineTo(cx + 7 * s, cy - 5 * s)
        c.drawPath(path, p)
    }
}

/** iOS-style segmented control. */
@SuppressLint("ViewConstructor")
class Segmented(ctx: Context, private val pal: Pal, private val options: List<String>, var selected: Int, private val onChange: (Int) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private var pos = selected.toFloat()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), 36.dpi)

    fun select(i: Int) {
        if (i == selected) return
        selected = i
        val from = pos
        animate().cancel()
        val a = android.animation.ValueAnimator.ofFloat(from, i.toFloat())
        a.duration = 260
        a.interpolator = android.view.animation.PathInterpolator(0.2f, 0.9f, 0.2f, 1f)
        a.addUpdateListener { pos = it.animatedValue as Float; invalidate() }
        a.start()
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        r.set(0f, 0f, width.toFloat(), h)
        p.color = pal.fill
        p.clearShadowLayer()
        c.drawRoundRect(r, h / 2f, h / 2f, p)
        val sw = width / options.size.toFloat()
        val inset = 2.dp
        r.set(sw * pos + inset, inset, sw * (pos + 1) - inset, h - inset)
        p.color = if (pal.text == Color.WHITE) 0xFF636366.toInt() else Color.WHITE
        p.setShadowLayer(3.dp, 0f, 1.dp, 0x22000000)
        c.drawRoundRect(r, (h - inset * 2) / 2f, (h - inset * 2) / 2f, p)
        p.clearShadowLayer()
        tp.textSize = 13.5f.dp
        for ((i, o) in options.withIndex()) {
            tp.typeface = U.font(if (i == selected) 600 else 500)
            tp.color = pal.text
            c.drawText(o, sw * (i + 0.5f), h / 2f + tp.textSize * 0.36f, tp)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            val i = (ev.x / (width / options.size.toFloat())).toInt().coerceIn(0, options.size - 1)
            if (i != selected) { select(i); performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onChange(i) }
        }
        return true
    }
}

/** Grid of tappable emoji. */
@SuppressLint("ViewConstructor")
class EmojiGrid(ctx: Context, private val items: List<String>, private val onPick: (String) -> Unit) : View(ctx) {
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val cols = 8
    private val cell get() = width / cols.toFloat()
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        val rows = (items.size + cols - 1) / cols
        setMeasuredDimension(width, (rows * width / cols.toFloat()).roundToInt())
    }
    override fun onDraw(c: Canvas) {
        tp.textSize = cell * 0.55f
        for ((i, e) in items.withIndex()) {
            val cx = (i % cols + 0.5f) * cell
            val cy = (i / cols + 0.5f) * cell
            c.drawText(e, cx, cy + tp.textSize * 0.36f, tp)
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            val i = (ev.y / cell).toInt() * cols + (ev.x / cell).toInt()
            items.getOrNull(i)?.let { performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onPick(it) }
        }
        return true
    }
}

/** Gradient swatches; the chosen one gets a ring. */
@SuppressLint("ViewConstructor")
class Swatches(ctx: Context, private val pal: Pal, var selected: Int, private val onPick: (Int) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val perRow = 6
    private val cell get() = width / perRow.toFloat()
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        val rows = (IconArt.gradients.size + perRow - 1) / perRow
        setMeasuredDimension(width, (rows * width / perRow.toFloat()).roundToInt())
    }
    override fun onDraw(c: Canvas) {
        val r = cell * 0.32f
        for ((i, g) in IconArt.gradients.withIndex()) {
            val cx = (i % perRow + 0.5f) * cell
            val cy = (i / perRow + 0.5f) * cell
            p.shader = LinearGradient(0f, cy - r, 0f, cy + r, g.first, g.second, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            if (i == selected) {
                ring.color = pal.text
                ring.strokeWidth = 2.5f.dp
                c.drawCircle(cx, cy, r + 5.dp, ring)
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            val i = (ev.y / cell).toInt() * perRow + (ev.x / cell).toInt()
            if (i in IconArt.gradients.indices) { selected = i; invalidate(); performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); onPick(i) }
        }
        return true
    }
}

/** A preset tile: icon + caption. */
@SuppressLint("ViewConstructor")
class PresetTile(ctx: Context, private val pal: Pal, private val icon: Bitmap, private val caption: String) : View(ctx) {
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(72.dpi, 86.dpi)
    override fun onDraw(c: Canvas) {
        val s = 56.dp
        val l = (width - s) / 2f
        r.set(l, 2.dp, l + s, 2.dp + s)
        c.drawBitmap(icon, null, r, bp)
        tp.color = pal.text2
        tp.textSize = 12.dp
        tp.typeface = U.font(500)
        val t = TextUtils.ellipsize(caption, tp, width.toFloat(), TextUtils.TruncateAt.END)
        c.drawText(t, 0, t.length, width / 2f, r.bottom + 18.dp, tp)
    }
}

/** How the app will look on the home screen: icon + name on a wallpaper-like card. */
@SuppressLint("ViewConstructor")
class PreviewCard(ctx: Context) : View(ctx) {
    var icon: Bitmap? = null
        set(v) { field = v; invalidate() }
    var label: String = ""
        set(v) { field = v; invalidate() }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val r = RectF()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), 190.dpi)
    override fun onDraw(c: Canvas) {
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        bg.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), intArrayOf(0xFF0B2A66.toInt(), 0xFF1E5BFF.toInt(), 0xFF00C2B0.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 26.dp, 26.dp, bg)
        val s = 96.dp
        val l = (width - s) / 2f
        val t = 30.dp
        icon?.let { r.set(l, t, l + s, t + s); c.drawBitmap(it, null, r, bp) }
        tp.textSize = 15.dp
        tp.typeface = U.font(500)
        tp.setShadowLayer(4.dp, 0f, 1.dp, 0x66000000)
        val name = TextUtils.ellipsize(label, tp, width * 0.8f, TextUtils.TruncateAt.END)
        c.drawText(name, 0, name.length, width / 2f, t + s + 28.dp, tp)
    }
}
