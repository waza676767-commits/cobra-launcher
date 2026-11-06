package dev.aura.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import dev.aura.launcher.core.Prefs
import dev.aura.launcher.core.Spring
import dev.aura.launcher.core.Theme
import dev.aura.launcher.core.Ui
import dev.aura.launcher.core.dp
import dev.aura.launcher.data.AppItem
import dev.aura.launcher.data.AppRepository
import dev.aura.launcher.data.FolderItem
import dev.aura.launcher.data.Item
import dev.aura.launcher.gfx.GlassPainter
import dev.aura.launcher.gfx.Glyph
import dev.aura.launcher.gfx.Glyphs
import dev.aura.launcher.gfx.IconFactory
import dev.aura.launcher.gfx.Squircle
import kotlin.math.abs
import kotlin.math.hypot

/** Views that draw glass need a redraw when they move over the wallpaper (page scroll). */
interface HasGlass

/** Views that show the jiggle / minus badge in edit mode. */
interface Editable {
    var editMode: Boolean
    val item: Item?
    fun refreshContent() {}
}

/** Tap / long-press detection shared by icons and widgets. */
class ItemTouch(private val v: View, private val home: HomeActivity, private val onPress: (Boolean) -> Unit) {
    private var downX = 0f
    private var downY = 0f
    var rawX = 0f; private set
    var rawY = 0f; private set
    var longPressed = false; private set
    private var active = false
    private val slop = ViewConfiguration.get(v.context).scaledTouchSlop
    private val pressDelay = Runnable { if (active) onPress(true) }
    private val long = Runnable {
        if (active) {
            longPressed = true
            home.onItemLongPress(v, rawX, rawY)
        }
    }

    fun handle(ev: MotionEvent, onTap: (Float, Float) -> Unit): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = true
                longPressed = false
                downX = ev.x; downY = ev.y
                rawX = ev.rawX; rawY = ev.rawY
                v.postDelayed(pressDelay, 40)
                v.postDelayed(long, if (home.editMode) 160L else 420L)
            }
            MotionEvent.ACTION_MOVE -> {
                if (active && !longPressed && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) {
                    cancel()
                }
            }
            MotionEvent.ACTION_UP -> {
                val tap = active && !longPressed
                cancel()
                if (tap) {
                    onPress(true)
                    v.postDelayed({ onPress(false) }, 90)
                    onTap(ev.x, ev.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    private fun cancel() {
        v.removeCallbacks(long)
        v.removeCallbacks(pressDelay)
        onPress(false)
        active = false
    }
}

/** An app (or folder) icon with its label, badge and edit-mode minus button. */
open class IconView(val home: HomeActivity) : View(home), HasGlass, Editable {
    override var item: Item? = null
    var key: String? = null
        private set
    var label: CharSequence = ""
    var icon: Bitmap? = null
    var inDock = false
    /** Inside an open folder or search results (labels use the theme text colour). */
    var onPanel = false
    var showLabel = true
    var badge = 0
    var canRemove = true
    override var editMode = false
        set(v) { field = v; invalidate() }
    /** 0..1 while a dragged icon hovers to make a folder. */
    var folderHover = 0f
        set(v) { field = v; invalidate() }
    private val hoverSpring = Spring(520f, 0.8f, 0.002f).apply { onUpdate = { folderHover = it } }

    protected val glass = GlassPainter()
    protected val path = Path()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.font(500)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.font(600)
        color = Color.WHITE
    }
    private val dst = RectF()
    private var shown: CharSequence = ""
    private var shownFor: CharSequence? = null
    private var shownWidth = 0

    private var pressV = 0f
    private val press = Spring(1100f, 0.72f, 0.002f).apply { onUpdate = { pressV = it; invalidate() } }
    private val touch = ItemTouch(this, home) { down -> press.animateTo(if (down) 1f else 0f) }

    fun bindApp(app: AppItem) {
        item = app
        if (key != app.key) icon = null
        key = app.key
        refreshContent()
    }

    override fun refreshContent() {
        val k = key ?: return
        label = AppRepository[k]?.label ?: ""
        loadIcon()
        updateBadge()
        invalidate()
    }

    fun loadIcon() {
        val k = key ?: return
        val got = IconFactory.get(k) { b -> if (key == k) { icon = b; invalidate() } }
        if (got != null) icon = got
    }

    fun updateBadge() {
        val k = key ?: return
        val pkg = AppRepository[k]?.pkg
        badge = if (pkg == null || Prefs.badges == "off") 0 else home.badgeFor(pkg)
    }

    fun setFolderHover(on: Boolean) = hoverSpring.animateTo(if (on) 1f else 0f)

    val iconSize: Int get() = home.geo.iconSize
    var iconTopOverride = -1f

    fun iconLeft(): Float = (width - iconSize) / 2f
    fun iconTop(): Float = when {
        iconTopOverride >= 0f -> iconTopOverride
        inDock -> (height - iconSize) / 2f
        else -> home.geo.iconTop
    }

    override fun onDraw(c: Canvas) {
        val s = iconSize.toFloat()
        val l = iconLeft()
        val t = iconTop()
        if (folderHover > 0.01f) {
            val grow = s * 0.16f * folderHover
            Squircle.into(path, l - grow, t - grow, s + grow * 2)
            glass.alpha = folderHover
            glass.drawPath(c, this, path, l - grow, t - grow, s + grow * 2, s + grow * 2)
            glass.alpha = 1f
        }
        val sc = 1f - 0.08f * pressV
        c.save()
        c.scale(sc, sc, l + s / 2f, t + s / 2f)
        drawIcon(c, l, t, s)
        if (pressV > 0.01f) {
            shade.color = Color.argb((70 * pressV).toInt(), 0, 0, 0)
            Squircle.into(path, l, t, s)
            c.drawPath(path, shade)
        }
        c.restore()
        if (showLabel && !inDock) drawLabel(c, t + s)
        if (badge > 0 && !editMode) drawBadge(c, l, t, s)
        if (editMode && canRemove) drawMinus(c, l, t, s)
    }

    open fun drawIcon(c: Canvas, l: Float, t: Float, s: Float) {
        if (IconFactory.isClear || icon == null) {
            Squircle.into(path, l, t, s)
            glass.drawPath(c, this, path, l, t, s, s)
        }
        icon?.let {
            dst.set(l, t, l + s, t + s)
            c.drawBitmap(it, null, dst, bmpPaint)
        }
    }

    private fun drawLabel(c: Canvas, iconBottom: Float) {
        val g = home.geo
        labelPaint.textSize = g.labelSize
        if (onPanel) {
            labelPaint.color = Theme.text
            labelPaint.clearShadowLayer()
        } else {
            labelPaint.color = Color.WHITE
            labelPaint.setShadowLayer(3.dp, 0f, 0.6f.dp, 0x66000000)
        }
        if (shownFor !== label || shownWidth != width) {
            shown = TextUtils.ellipsize(label, labelPaint, width - 4.dp, TextUtils.TruncateAt.END)
            shownFor = label
            shownWidth = width
        }
        c.drawText(shown, 0, shown.length, width / 2f, iconBottom + g.labelGap + g.labelSize * 0.95f, labelPaint)
    }

    private fun drawBadge(c: Canvas, l: Float, t: Float, s: Float) {
        badgePaint.color = 0xFFFF3B30.toInt()
        if (Prefs.badges == "dot") {
            val r = s * 0.1f
            c.drawCircle(l + s - r * 0.7f, t + r * 0.7f, r, badgePaint)
            return
        }
        val txt = if (badge > 99) "99+" else badge.toString()
        badgeText.textSize = s * 0.21f
        val r = s * 0.16f
        val tw = badgeText.measureText(txt)
        val w = maxOf(r * 2, tw + r)
        val cx = l + s - r * 0.35f
        val cy = t + r * 0.35f
        dst.set(cx - w / 2f, cy - r, cx + w / 2f, cy + r)
        c.drawRoundRect(dst, r, r, badgePaint)
        c.drawText(txt, cx, cy + badgeText.textSize * 0.36f, badgeText)
    }

    private fun drawMinus(c: Canvas, l: Float, t: Float, s: Float) {
        val r = s * 0.15f
        val cx = l + r * 0.25f
        val cy = t + r * 0.25f
        badgePaint.color = if (Theme.dark) 0xF23A3A3C.toInt() else 0xF2E5E5EA.toInt()
        c.drawCircle(cx, cy, r, badgePaint)
        Glyphs.draw(c, Glyph.MINUS, cx, cy, r * 1.5f, Theme.text, 3f)
    }

    fun minusHit(x: Float, y: Float): Boolean {
        val s = iconSize.toFloat()
        val r = s * 0.15f
        return hypot(x - (iconLeft() + r * 0.25f), y - (iconTop() + r * 0.25f)) < r * 1.9f
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = touch.handle(ev) { x, y ->
        if (editMode) {
            if (canRemove && minusHit(x, y)) home.onRemoveTapped(this)
            else if (item is FolderItem) home.onItemClick(this)
        } else {
            home.onItemClick(this)
        }
    }

    /** Just the icon square, for the drag shadow. */
    fun snapshotIcon(): Bitmap {
        val s = iconSize
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.translate(-iconLeft(), -iconTop())
        drawIcon(c, iconLeft(), iconTop(), s.toFloat())
        return b
    }
}

/** Folder: a glass squircle with up to nine mini icons. */
class FolderIconView(home: HomeActivity) : IconView(home) {
    private val mini = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val r = RectF()
    private val miniPath = Path()

    fun bindFolder(f: FolderItem) {
        item = f
        refreshContent()
    }

    override fun refreshContent() {
        val f = item as? FolderItem ?: return
        label = f.name
        badge = if (Prefs.badges == "off") 0 else f.apps.sumOf { a -> AppRepository[a.key]?.pkg?.let { home.badgeFor(it) } ?: 0 }
        invalidate()
    }

    override fun drawIcon(c: Canvas, l: Float, t: Float, s: Float) {
        Squircle.into(path, l, t, s)
        glass.drawPath(c, this, path, l, t, s, s)
        val f = item as? FolderItem ?: return
        val pad = s * 0.13f
        val gap = s * 0.055f
        val m = (s - pad * 2 - gap * 2) / 3f
        for (i in 0 until minOf(9, f.apps.size)) {
            val k = f.apps[i].key
            val b = IconFactory.get(k) { invalidate() } ?: continue
            val x = l + pad + (i % 3) * (m + gap)
            val y = t + pad + (i / 3) * (m + gap)
            if (IconFactory.isClear) {
                Squircle.into(miniPath, x, y, m)
                c.drawPath(miniPath, plate)
            }
            r.set(x, y, x + m, y + m)
            c.drawBitmap(b, null, r, mini)
        }
    }
}
