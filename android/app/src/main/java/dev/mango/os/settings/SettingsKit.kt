package dev.mango.os.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.mango.os.core.Spring
import dev.mango.os.core.Theme
import dev.mango.os.core.Ui
import dev.mango.os.core.blendColors
import dev.mango.os.core.dp
import dev.mango.os.core.dpi
import dev.mango.os.gfx.Glyph
import dev.mango.os.gfx.Glyphs
import kotlin.math.abs
import kotlin.math.roundToInt

/** iOS-style switch. */
class Toggle(ctx: Context, initial: Boolean, private val onChange: (Boolean) -> Unit) : View(ctx) {
    var checked = initial
        private set
    private var pos = if (initial) 1f else 0f
    private val spring = Spring(600f, 0.78f, 0.002f).apply { onUpdate = { pos = it; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun set(v: Boolean, notify: Boolean) {
        if (v == checked) return
        checked = v
        spring.setValue(pos)
        spring.animateTo(if (v) 1f else 0f)
        if (notify) onChange(v)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(51.dpi, 31.dpi)

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        r.set(0f, 0f, width.toFloat(), h)
        p.color = blendColors(Theme.fill, Theme.green, pos.coerceIn(0f, 1f))
        p.clearShadowLayer()
        c.drawRoundRect(r, h / 2f, h / 2f, p)
        val tr = h / 2f - 2.dp
        val cx = h / 2f + (width - h) * pos
        p.color = Color.WHITE
        p.setShadowLayer(3.dp, 0f, 1.5f.dp, 0x33000000)
        c.drawCircle(cx, h / 2f, tr, p)
        p.clearShadowLayer()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) { set(!checked, true); Ui.tick(this) }
        return true
    }
}

/** Segmented control with a sliding thumb. */
class Segmented(ctx: Context, private val options: List<String>, initial: Int, private val onChange: (Int) -> Unit) : View(ctx) {
    private var selected = initial.coerceIn(0, options.size - 1)
    private var pos = selected.toFloat()
    private val spring = Spring(520f, 0.82f, 0.001f).apply { onUpdate = { pos = it; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 34.dpi)

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        r.set(0f, 0f, width.toFloat(), h)
        p.color = Theme.fill
        p.clearShadowLayer()
        c.drawRoundRect(r, h / 2f, h / 2f, p)
        val sw = width / options.size.toFloat()
        val inset = 2.dp
        r.set(sw * pos + inset, inset, sw * (pos + 1) - inset, h - inset)
        p.color = if (Theme.dark) 0xFF636366.toInt() else Color.WHITE
        p.setShadowLayer(3.dp, 0f, 1.dp, 0x22000000)
        c.drawRoundRect(r, (h - inset * 2) / 2f, (h - inset * 2) / 2f, p)
        p.clearShadowLayer()
        tp.textSize = 13.5f.dp
        for ((i, o) in options.withIndex()) {
            tp.typeface = Ui.font(if (i == selected) 600 else 500)
            tp.color = Theme.text
            val t = TextUtils.ellipsize(o, tp, sw - 8.dp, TextUtils.TruncateAt.END)
            c.drawText(t, 0, t.length, sw * (i + 0.5f), h / 2f + tp.textSize * 0.36f, tp)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            val i = (ev.x / (width / options.size.toFloat())).toInt().coerceIn(0, options.size - 1)
            if (i != selected) {
                selected = i
                spring.setValue(pos)
                spring.animateTo(i.toFloat())
                Ui.tick(this)
                onChange(i)
            }
        }
        return true
    }
}

/** Slider with an iOS-style white thumb. Reports while dragging and once released. */
class Slider(
    ctx: Context,
    private val min: Float,
    private val max: Float,
    initial: Float,
    private val step: Float,
    private val onMove: (Float) -> Unit,
    private val onCommit: (Float) -> Unit
) : View(ctx) {
    private var value = initial
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var lastTick = initial

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 40.dpi)

    private val thumbR get() = 13.dp

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val l = thumbR
        val rr = width - thumbR
        val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val x = l + (rr - l) * f
        p.clearShadowLayer()
        p.color = Theme.fill
        r.set(l, cy - 2.dp, rr, cy + 2.dp)
        c.drawRoundRect(r, 2.dp, 2.dp, p)
        p.color = Theme.accent
        r.set(l, cy - 2.dp, x, cy + 2.dp)
        c.drawRoundRect(r, 2.dp, 2.dp, p)
        p.color = Color.WHITE
        p.setShadowLayer(4.dp, 0f, 1.5f.dp, 0x40000000)
        c.drawCircle(x, cy, thumbR, p)
        p.clearShadowLayer()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val l = thumbR
        val rr = width - thumbR
        val f = ((ev.x - l) / (rr - l)).coerceIn(0f, 1f)
        var v = min + (max - min) * f
        v = (Math.round(v / step) * step).coerceIn(min, max)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_UP -> { value = v; invalidate(); onCommit(v); return true }
            MotionEvent.ACTION_CANCEL -> return true
        }
        if (v != value) {
            value = v
            if (abs(v - lastTick) >= step * 0.99f) { Ui.tick(this); lastTick = v }
            invalidate()
            onMove(v)
        }
        return true
    }
}

/** Coloured rounded-square icon used at the start of a settings row. */
class RowIcon(ctx: Context, private val glyph: Glyph, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(30.dpi, 30.dpi)
    override fun onDraw(c: Canvas) {
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        p.color = color
        c.drawRoundRect(r, 8.dp, 8.dp, p)
        Glyphs.draw(c, glyph, width / 2f, height / 2f, width * 0.62f, Color.WHITE, 2.2f)
    }
}

/** Builds grouped iOS-style lists. */
class Kit(private val ctx: Context) {
    fun text(s: CharSequence, size: Float, color: Int, weight: Int = 400): TextView = TextView(ctx).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        typeface = Ui.font(weight)
        includeFontPadding = false
    }

    fun groupBox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 22.dp
            setColor(Theme.groupBg)
        }
        clipToOutline = true
    }

    fun header(s: String): View = text(s.uppercase(), 13.dp, Theme.text2, 500).apply {
        setPadding(36.dpi, 22.dpi, 36.dpi, 8.dpi)
    }

    fun footer(s: CharSequence): View = text(s, 13.dp, Theme.text2).apply {
        setPadding(36.dpi, 8.dpi, 36.dpi, 4.dpi)
        setLineSpacing(0f, 1.18f)
    }

    /** One row: [icon] title / subtitle ……… value [accessory]. */
    fun row(
        title: String,
        icon: Glyph? = null,
        iconColor: Int = 0,
        value: String? = null,
        subtitle: String? = null,
        titleColor: Int = Theme.text,
        chevron: Boolean = false,
        accessory: View? = null,
        center: Boolean = false,
        onClick: (() -> Unit)? = null
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 52.dpi
            setPadding(16.dpi, 9.dpi, 16.dpi, 9.dpi)
        }
        if (icon != null) row.addView(RowIcon(ctx, icon, iconColor), LinearLayout.LayoutParams(30.dpi, 30.dpi).apply { rightMargin = 14.dpi })
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(title, 17.dp, titleColor).apply { if (center) gravity = Gravity.CENTER })
        if (subtitle != null) texts.addView(text(subtitle, 13.dp, Theme.text2).apply { setPadding(0, 3.dpi, 0, 0); setLineSpacing(0f, 1.12f) })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (value != null) row.addView(text(value, 17.dp, Theme.text2).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(8.dpi, 0, 0, 0) })
        if (accessory != null) row.addView(accessory, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = 10.dpi })
        if (chevron) row.addView(object : View(ctx) {
            override fun onDraw(c: Canvas) = Glyphs.draw(c, Glyph.CHEVRON_RIGHT, width / 2f, height / 2f, 14.dp, Theme.text3, 2.6f)
        }, LinearLayout.LayoutParams(18.dpi, 22.dpi).apply { leftMargin = 6.dpi })
        if (onClick != null) {
            val tv = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            row.setBackgroundResource(tv.resourceId)
            row.setOnClickListener { onClick() }
        }
        return row
    }

    fun separator(indent: Int): View = View(ctx).apply {
        setBackgroundColor(Theme.separator)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, (0.5f.dp).roundToInt())).apply { leftMargin = indent }
    }
}

/** A scrolling page with a large title and grouped sections. */
class Page(ctx: Context, val title: String) {
    private val kit = Kit(ctx)
    val body = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 48.dpi)
    }

    init {
        body.addView(kit.text(title, 34.dp, Theme.text, 700).apply { setPadding(20.dpi, 6.dpi, 20.dpi, 6.dpi) })
    }

    fun group(header: String? = null, footer: CharSequence? = null, build: Group.() -> Unit) {
        if (header != null) body.addView(kit.header(header)) else body.addView(View(body.context), LinearLayout.LayoutParams(1, 18.dpi))
        val g = Group(kit, kit.groupBox())
        g.build()
        body.addView(g.box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = 16.dpi; rightMargin = 16.dpi
        })
        if (footer != null) body.addView(kit.footer(footer))
    }

    fun view(v: View, margin: Int = 16.dpi) {
        body.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = margin; rightMargin = margin
        })
    }
}

class Group(val kit: Kit, val box: LinearLayout) {
    private var count = 0
    private var lastIndent = 0

    fun add(v: View, indent: Int = 16.dpi) {
        if (count > 0) box.addView(kit.separator(lastIndent))
        box.addView(v)
        count++
        lastIndent = indent
    }

    fun row(
        title: String, icon: Glyph? = null, color: Int = 0, value: String? = null, subtitle: String? = null,
        titleColor: Int = Theme.text, chevron: Boolean = false, accessory: View? = null, center: Boolean = false, onClick: (() -> Unit)? = null
    ) = add(kit.row(title, icon, color, value, subtitle, titleColor, chevron, accessory, center, onClick), if (icon != null) 60.dpi else 16.dpi)

    fun nav(title: String, icon: Glyph?, color: Int, value: String? = null, subtitle: String? = null, onClick: () -> Unit) =
        row(title, icon, color, value, subtitle, chevron = true, onClick = onClick)

    fun toggle(title: String, value: Boolean, icon: Glyph? = null, color: Int = 0, subtitle: String? = null, onChange: (Boolean) -> Unit) {
        val t = Toggle(box.context, value, onChange)
        row(title, icon, color, subtitle = subtitle, accessory = t, onClick = { t.set(!t.checked, true) })
    }

    fun check(title: String, checked: Boolean, subtitle: String? = null, onClick: () -> Unit) {
        val mark = object : View(box.context) {
            override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(24.dpi, 24.dpi)
            override fun onDraw(c: Canvas) { if (checked) Glyphs.draw(c, Glyph.CHECK, width / 2f, height / 2f, 20.dp, Theme.accent, 2.6f) }
        }
        add(kit.row(title, subtitle = subtitle, accessory = mark, onClick = onClick))
    }

    fun segmented(options: List<String>, selected: Int, title: String? = null, onChange: (Int) -> Unit) {
        val wrap = LinearLayout(box.context).apply { orientation = LinearLayout.VERTICAL; setPadding(16.dpi, 12.dpi, 16.dpi, 12.dpi) }
        if (title != null) wrap.addView(kit.text(title, 15.dp, Theme.text2, 500).apply { setPadding(0, 0, 0, 10.dpi) })
        wrap.addView(Segmented(box.context, options, selected, onChange))
        add(wrap)
    }

    fun slider(title: String, min: Float, max: Float, value: Float, step: Float, format: (Float) -> String, onMove: (Float) -> Unit = {}, onCommit: (Float) -> Unit) {
        val wrap = LinearLayout(box.context).apply { orientation = LinearLayout.VERTICAL; setPadding(16.dpi, 12.dpi, 16.dpi, 6.dpi) }
        val head = LinearLayout(box.context).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(kit.text(title, 17.dp, Theme.text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = kit.text(format(value), 17.dp, Theme.text2)
        head.addView(v)
        wrap.addView(head)
        wrap.addView(Slider(box.context, min, max, value, step, { x -> v.text = format(x); onMove(x) }, { x -> v.text = format(x); onCommit(x) }))
        add(wrap)
    }

    fun button(title: String, color: Int = Theme.accent, onClick: () -> Unit) =
        add(kit.row(title, titleColor = color, onClick = onClick))

    fun view(v: View) = add(v)
}

/** Glass-ish circular back button (iOS 26 style). */
class BackButton(ctx: Context) : FrameLayout(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setWillNotDraw(false) }
    override fun onDraw(c: Canvas) {
        p.color = if (Theme.dark) 0x33FFFFFF else 0x14000000
        c.drawCircle(width / 2f, height / 2f, width / 2f, p)
        Glyphs.draw(c, Glyph.CHEVRON_LEFT, width / 2f - 1.dp, height / 2f, 20.dp, Theme.text, 2.6f)
    }
}
