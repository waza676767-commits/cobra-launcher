package dev.aura.launcher.home

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import dev.aura.launcher.core.Prefs
import dev.aura.launcher.core.Theme
import dev.aura.launcher.core.dp
import dev.aura.launcher.gfx.Backdrop
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Draws the wallpaper (and its blurred twin while overlays are open). */
class WallpaperView(private val home: HomeActivity) : View(home) {
    var blur = 0f
        set(v) { if (field != v) { field = v; invalidate() } }
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val dst = android.graphics.Rect()

    override fun onDraw(c: Canvas) {
        dst.set(0, 0, width, height)
        val b = Backdrop.full
        if (b == null) c.drawColor(0xFF05070D.toInt()) else { paint.alpha = 255; c.drawBitmap(b, null, dst, paint) }
        if (blur > 0f) Backdrop.blurred?.let { paint.alpha = (255 * blur).toInt(); c.drawBitmap(it, null, dst, paint) }
        val dim = (if (Theme.dark) 0.16f else 0f) + 0.18f * blur
        if (dim > 0f) c.drawColor(android.graphics.Color.argb((255 * dim).toInt(), 0, 0, 0))
    }
}

/** Lays out workspace, page pill, dock and the edit-mode buttons from [Geometry]. */
@SuppressLint("ViewConstructor")
class HomeContent(private val home: HomeActivity) : ViewGroup(home) {
    init { clipChildren = false }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        home.updateGeometry(w, h)
        val g = home.geo
        fun exact(v: Int) = MeasureSpec.makeMeasureSpec(v.coerceAtLeast(0), MeasureSpec.EXACTLY)
        home.workspace.measure(exact(w), exact(g.wsHeight))
        home.pill.measure(exact(w), exact(g.pillH))
        home.dock.measure(exact(w - 2 * g.dockMargin), exact(g.dockH))
        home.editBar.measure(exact(w), exact(44.dp.roundToInt()))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val g = home.geo
        home.workspace.layout(0, g.wsTop, width, g.wsTop + g.wsHeight)
        home.pill.layout(0, g.pillTop, width, g.pillTop + g.pillH)
        home.dock.layout(g.dockMargin, g.dockTop, width - g.dockMargin, g.dockTop + g.dockH)
        val et = g.insetTop + 4.dp.roundToInt()
        home.editBar.layout(0, et, width, et + home.editBar.measuredHeight)
    }
}

/** "+", gear and "Done" shown at the top while editing. */
@SuppressLint("ViewConstructor")
class EditBar(home: HomeActivity) : ViewGroup(home) {
    val add = PillButton(home, null, dev.aura.launcher.gfx.Glyph.PLUS)
    val settings = PillButton(home, null, dev.aura.launcher.gfx.Glyph.GEAR)
    val done = PillButton(home, "Done", null, bold = true)

    init {
        addView(add); addView(settings); addView(done)
        add.setOnClickListener { home.openWidgetPicker() }
        settings.setOnClickListener { home.openSettings(null) }
        done.setOnClickListener { home.exitEditMode() }
        alpha = 0f
        visibility = GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val hs = MeasureSpec.makeMeasureSpec(h - 4.dp.roundToInt(), MeasureSpec.EXACTLY)
        add.measure(MeasureSpec.makeMeasureSpec(h - 4.dp.roundToInt(), MeasureSpec.EXACTLY), hs)
        settings.measure(MeasureSpec.makeMeasureSpec(h - 4.dp.roundToInt(), MeasureSpec.EXACTLY), hs)
        done.measure(MeasureSpec.makeMeasureSpec(done.preferredWidth(), MeasureSpec.EXACTLY), hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val m = 16.dp.roundToInt()
        add.layout(m, 0, m + add.measuredWidth, add.measuredHeight)
        val sl = m + add.measuredWidth + 10.dp.roundToInt()
        settings.layout(sl, 0, sl + settings.measuredWidth, settings.measuredHeight)
        done.layout(width - m - done.measuredWidth, 0, width - m, done.measuredHeight)
    }
}

/**
 * Root of the home screen. Decides who owns a gesture: page swipe, pull-down search,
 * drag-and-drop, or the views underneath (taps on icons, widgets…).
 */
@SuppressLint("ViewConstructor")
class HomeRoot(private val home: HomeActivity) : FrameLayout(home) {
    private enum class Mode { NONE, PAGE, PULL, DRAG, SHADE }

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var tracker: VelocityTracker? = null
    private val slop = ViewConfiguration.get(home).scaledTouchSlop
    private var emptyDown = false
    private val longEmpty = Runnable {
        if (mode == Mode.NONE && emptyDown) {
            emptyDown = false
            home.onEmptyLongPress()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            tracker?.recycle()
            tracker = VelocityTracker.obtain()
        }
        tracker?.addMovement(ev)
        val r = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            tracker?.recycle()
            tracker = null
        }
        return r
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (home.drag.active) {
            mode = Mode.DRAG
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { home.drag.drop(); mode = Mode.NONE }
                else -> home.drag.move(ev.x, ev.y)
            }
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x
                mode = Mode.NONE
                if (home.workspace.isSettling && !home.anyOverlayOpen() && ev.y < home.geo.pillTop) {
                    mode = Mode.PAGE
                    home.workspace.beginDrag()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> return decide(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (home.pendingDrag != null && ev.actionMasked == MotionEvent.ACTION_UP) home.pendingDrag = null
            }
        }
        return mode != Mode.NONE
    }

    /** Called with MOVE events until somebody owns the gesture. */
    private fun decide(ev: MotionEvent): Boolean {
        if (mode != Mode.NONE) return true
        val pd = home.pendingDrag
        if (pd != null) {
            if (hypot(ev.x - downX, ev.y - downY) > slop * 1.2f) {
                home.startPendingDrag(ev.x, ev.y)
                mode = Mode.DRAG
                return true
            }
            return false
        }
        if (home.anyOverlayOpen()) return false
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (abs(dx) > slop && abs(dx) > abs(dy)) {
            mode = Mode.PAGE
            lastX = ev.x
            home.workspace.beginDrag()
            return true
        }
        if (dy > slop && dy > abs(dx) * 1.2f && !home.editMode && downY < home.geo.dockTop) {
            when (Prefs.swipeDown) {
                "search" -> { mode = Mode.PULL; home.search.beginPull(); return true }
                "notifications" -> { mode = Mode.SHADE; home.expandNotifications(); return true }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x
                emptyDown = mode == Mode.NONE
                if (emptyDown) postDelayed(longEmpty, 480)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.NONE) {
                    if (hypot(ev.x - downX, ev.y - downY) > slop) { removeCallbacks(longEmpty); emptyDown = false }
                    if (!decide(ev)) return true
                }
                when (mode) {
                    Mode.PAGE -> { home.workspace.dragBy(lastX - ev.x); lastX = ev.x }
                    Mode.PULL -> home.search.pull(ev.y - downY)
                    Mode.DRAG -> home.drag.move(ev.x, ev.y)
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longEmpty)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                val vy = tracker?.yVelocity ?: 0f
                when (mode) {
                    Mode.PAGE -> home.workspace.release(vx)
                    Mode.PULL -> home.search.endPull(vy)
                    Mode.DRAG -> home.drag.drop()
                    Mode.NONE -> if (ev.actionMasked == MotionEvent.ACTION_UP && emptyDown) home.onEmptyTap()
                    else -> {}
                }
                mode = Mode.NONE
                emptyDown = false
            }
        }
        return true
    }
}
