package dev.mango.os.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri

import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.OverScroller
import dev.mango.os.core.Prefs
import dev.mango.os.core.Spring
import dev.mango.os.core.Theme
import dev.mango.os.core.Ui
import dev.mango.os.core.dp
import dev.mango.os.core.dpi
import dev.mango.os.data.AppItem
import dev.mango.os.data.AppRepository
import dev.mango.os.data.FolderItem
import dev.mango.os.data.Item
import dev.mango.os.gfx.GlassPainter
import dev.mango.os.gfx.Glyph
import dev.mango.os.gfx.Glyphs
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rounded glass panel that hosts children. */
open class GlassPanel(ctx: Context) : FrameLayout(ctx), HasGlass {
    val glass = GlassPainter()
    var radius = 28.dp
    init { setWillNotDraw(false) }
    override fun onDraw(c: Canvas) {
        val sx = scrollX.toFloat()
        val sy = scrollY.toFloat()
        glass.drawRoundRect(c, this, sx, sy, sx + width, sy + height, radius, -sx, -sy)
    }
}

// =====================================================================================
// Folder
// =====================================================================================

/** The open-folder view: blurred home behind, editable title, glass panel of icons. */
@SuppressLint("ViewConstructor")
class FolderOverlay(private val home: HomeActivity) : FrameLayout(home) {
    var folder: FolderItem? = null
        private set
    private var sourceView: View? = null
    val panel = FolderPanel(home, this)
    private val title = EditText(home)
    private val spring = Spring(420f, 0.84f, 0.001f)
    private var progress = 0f
    var isOpen = false
        private set
    private var closing = false

    init {
        visibility = GONE
        title.apply {
            background = null
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 30.dp)
            typeface = Ui.font(700)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setShadowLayer(6.dp, 0f, 1.dp, 0x55000000)
            setOnEditorActionListener { _, _, _ -> commitTitle(); true }
            setOnFocusChangeListener { _, f -> if (!f) commitTitle() }
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(panel)
        spring.onUpdate = { v -> progress = v; apply() }
        spring.onEnd = {
            // the panel animates as a GPU texture; back to normal drawing once it settles
            panel.setLayerType(View.LAYER_TYPE_NONE, null)
            if (closing) finishClose()
        }
        setOnClickListener { close() }
    }

    private fun commitTitle() {
        if (readOnly) return
        val f = folder ?: return
        val t = title.text.toString().trim()
        if (t.isNotEmpty() && t != f.name) {
            f.name = t
            home.saveLayout()
            (sourceView as? Editable)?.refreshContent()
        }
        title.clearFocus()
        home.hideKeyboard(title)
    }

    /** App Library groups open like folders but can't be edited. */
    var readOnly = false
        private set
    private var sourceRect: RectF? = null

    fun openReadOnly(f: FolderItem, from: RectF) {
        readOnly = true
        title.isEnabled = false
        sourceRect = RectF(from)
        openInternal(f, null)
    }

    fun open(f: FolderItem, from: View) {
        readOnly = false
        title.isEnabled = true
        sourceRect = null
        openInternal(f, from)
    }

    private fun openInternal(f: FolderItem, from: View?) {
        folder = f
        sourceView = from
        isOpen = true
        closing = false
        title.setText(f.name)
        panel.bind(f)
        visibility = VISIBLE
        val g = home.geo
        val pw = (min(g.width - 2 * 36.dp, panel.columns * g.cellW + 2 * 18.dp)).roundToInt()
        val ph = panel.measureHeight(pw, (g.height * 0.56f).roundToInt())
        val lp = panel.layoutParams as LayoutParams
        lp.width = pw
        lp.height = ph
        lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        lp.topMargin = ((g.height - ph) / 2f).roundToInt()
        panel.layoutParams = lp
        val tlp = title.layoutParams as LayoutParams
        tlp.topMargin = lp.topMargin - 64.dpi
        title.layoutParams = tlp
        spring.snapTo(0f)
        progress = 0f
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        post { apply(); spring.animateTo(1f) }
        Ui.tick(this)
    }

    fun close(then: (() -> Unit)? = null) {
        if (!isOpen) { then?.invoke(); return }
        commitTitle()
        closing = true
        isOpen = false
        afterClose = then
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (Theme.reduceMotion) spring.animateTo(0f) else spring.animateTo(0f, -2f)
    }

    private var afterClose: (() -> Unit)? = null

    private fun finishClose() {
        visibility = GONE
        closing = false
        panel.unbind()
        home.onFolderClosed(if (readOnly) null else folder)
        folder = null
        sourceView = null
        afterClose?.invoke()
        afterClose = null
    }

    /** Scale the panel out of / back into the folder icon. */
    private fun apply() {
        val p = progress.coerceIn(0f, 1.2f)
        home.setOverlayBlur(p.coerceIn(0f, 1f))
        title.alpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
        val src = sourceView
        val rect = sourceRect
        if ((src == null && rect == null) || panel.width == 0 || Theme.reduceMotion) {
            panel.scaleX = 1f; panel.scaleY = 1f; panel.translationX = 0f; panel.translationY = 0f
            panel.alpha = p.coerceIn(0f, 1f)
            return
        }
        val s: Float
        val ix: Float
        val iy: Float
        if (rect != null) {
            s = rect.width(); ix = rect.left; iy = rect.top
        } else {
            val loc = IntArray(2)
            src!!.getLocationInWindow(loc)
            val iv = src as? IconView
            s = (iv?.iconSize ?: src.width).toFloat()
            ix = loc[0] + (iv?.iconLeft() ?: 0f)
            iy = loc[1] + (iv?.iconTop() ?: 0f)
        }
        val startScale = s / panel.width
        val sc = startScale + (1f - startScale) * p
        panel.pivotX = 0f
        panel.pivotY = 0f
        panel.scaleX = sc
        panel.scaleY = sc
        val endL = panel.left.toFloat()
        val endT = panel.top.toFloat()
        panel.translationX = (ix - endL) * (1f - p)
        panel.translationY = (iy - endT) * (1f - p)
        panel.alpha = (p * 3f).coerceIn(0f, 1f)
        panel.radius = 28.dp / sc.coerceAtLeast(0.2f) * (0.35f + 0.65f * p.coerceIn(0f, 1f))
        panel.invalidate()
    }

    fun editTitle() {
        title.requestFocus()
        title.selectAll()
        home.showKeyboard(title)
    }

    fun panelContains(x: Float, y: Float, margin: Float): Boolean {
        if (!isOpen) return false
        val loc = IntArray(2)
        panel.getLocationInWindow(loc)
        return x >= loc[0] - margin && x <= loc[0] + panel.width + margin && y >= loc[1] - margin && y <= loc[1] + panel.height + margin
    }
}

/** Grid of icons inside an open folder; scrolls when the folder is big. */
@SuppressLint("ViewConstructor")
class FolderPanel(private val home: HomeActivity, private val overlay: FolderOverlay) : GlassPanel(home), DropContainer {
    private var folder: FolderItem? = null
    private val views = HashMap<Long, IconView>()
    private val oldPos = HashMap<Long, Point>()
    private var animateNext = false
    val columns: Int get() = if ((folder?.apps?.size ?: 0) > 9) 4 else 3
    private val pad get() = 18.dp
    private val cellH get() = home.geo.iconSize + 34.dp
    private val scroller = OverScroller(home)
    private var lastY = 0f
    private var downY = 0f
    private var scrolling = false
    private val slop = ViewConfiguration.get(home).scaledTouchSlop
    private val loc = IntArray(2)

    init {
        clipChildren = true
        radius = 28.dp
        isClickable = true
    }

    fun bind(f: FolderItem) {
        folder = f
        scrollTo(0, 0)
        sync(false)
    }

    fun unbind() {
        removeAllViews()
        views.clear()
        folder = null
    }

    fun sync(animate: Boolean) {
        val f = folder ?: return
        oldPos.clear()
        if (animate) for ((id, v) in views) oldPos[id] = Point((v.left + v.translationX).roundToInt(), (v.top + v.translationY).roundToInt())
        val ids = f.apps.map { it.id }.toHashSet()
        val it = views.entries.iterator()
        while (it.hasNext()) { val e = it.next(); if (e.key !in ids) { removeView(e.value); it.remove() } }
        for (a in f.apps) {
            var v = views[a.id]
            if (v == null) {
                v = home.createItemView(a, this) as IconView
                v.onPanel = true
                views[a.id] = v
                addView(v)
            } else v.refreshContent()
            v.visibility = if (a.id == home.drag.draggingId) View.INVISIBLE else View.VISIBLE
            v.editMode = home.editMode
        }
        animateNext = animate
        requestLayout()
    }

    fun measureHeight(w: Int, maxH: Int): Int {
        val n = folder?.apps?.size ?: 0
        val rows = max(1, ceil(n / columns.toFloat()).toInt())
        return min((rows * cellH + pad * 2).roundToInt(), maxH)
    }

    private fun cellW() = (width - pad * 2) / columns

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = ((w - pad * 2) / columns).roundToInt()
        for (v in views.values) v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH.roundToInt(), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val f = folder ?: return
        val cw = cellW()
        for ((i, a) in f.apps.withIndex()) {
            val v = views[a.id] ?: continue
            v.iconTopOverride = 8.dp
            val x = (pad + (i % columns) * cw).roundToInt()
            val y = (pad + (i / columns) * cellH).roundToInt()
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
            val o = oldPos[a.id]
            if (animateNext && o != null && (o.x != x || o.y != y)) {
                v.translationX = (o.x - x).toFloat(); v.translationY = (o.y - y).toFloat()
                v.animate().translationX(0f).translationY(0f).setDuration(Ui.ms(320)).setInterpolator(Ui.easeOut).start()
            }
        }
        animateNext = false
    }

    private fun maxScroll(): Int {
        val n = folder?.apps?.size ?: 0
        val rows = max(1, ceil(n / columns.toFloat()).toInt())
        return max(0, (rows * cellH + pad * 2 - height).roundToInt())
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = ev.y; lastY = ev.y; scrolling = false; scroller.forceFinished(true) }
            MotionEvent.ACTION_MOVE -> if (!scrolling && maxScroll() > 0 && abs(ev.y - downY) > slop && home.pendingDrag == null) { scrolling = true; lastY = ev.y }
        }
        return scrolling
    }

    private var tracker: android.view.VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (tracker == null) tracker = android.view.VelocityTracker.obtain()
        tracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (scrolling) {
                scrollTo(0, (scrollY + (lastY - ev.y)).roundToInt().coerceIn(0, maxScroll()))
                lastY = ev.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (scrolling) {
                    tracker?.computeCurrentVelocity(1000)
                    scroller.fling(0, scrollY, 0, -(tracker?.yVelocity ?: 0f).toInt(), 0, 0, 0, maxScroll())
                    postInvalidateOnAnimation()
                }
                scrolling = false
                tracker?.recycle(); tracker = null
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(0, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    // ------------------------------------------------ DropContainer (folder contents)

    override fun canAccept(item: Item) = item is AppItem

    override fun hit(x: Float, y: Float, item: Item): Hit {
        val f = folder ?: return Hit(0, null, false)
        getLocationInWindow(loc)
        val lx = x - loc[0]
        val ly = y - loc[1] + scrollY
        val cw = cellW()
        val col = floor((lx - pad) / cw).toInt().coerceIn(0, columns - 1)
        val row = floor((ly - pad) / cellH).toInt().coerceAtLeast(0)
        val idx = row * columns + col
        val inList = f.apps.contains(item)
        if (idx >= f.apps.size) return Hit(if (inList) f.apps.size - 1 else f.apps.size, null, false)
        return Hit(idx, f.apps[idx], false)
    }

    override fun indexOf(item: Item) = folder?.apps?.indexOf(item) ?: -1
    override fun insert(item: Item, index: Int) {
        val f = folder ?: return
        if (item is AppItem) f.apps.add(index.coerceIn(0, f.apps.size), item)
    }
    override fun remove(item: Item) { folder?.apps?.remove(item) }
    override fun moveTo(item: Item, index: Int) {
        val f = folder ?: return
        val i = f.apps.indexOf(item)
        if (i < 0 || item !is AppItem) return
        f.apps.removeAt(i)
        f.apps.add(index.coerceIn(0, f.apps.size), item)
    }
    override fun refresh(animate: Boolean) = sync(animate)
    override fun viewFor(item: Item): View? = views[item.id]
}

// =====================================================================================
// Search
// =====================================================================================

/** Pull down on the home screen (or tap "Search") to find any app. */
@SuppressLint("ViewConstructor")
class SearchOverlay(private val home: HomeActivity) : FrameLayout(home) {
    private val bar = GlassPanel(home)
    private val field = EditText(home)
    private val results = SearchResults(home)
    private val spring = Spring(440f, 0.9f, 0.001f)
    private var progress = 0f
    var isOpen = false
        private set
    private var pulling = false

    init {
        visibility = GONE
        isClickable = true
        bar.radius = 26.dp
        field.apply {
            background = null
            hint = "Search apps"
            setHintTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 17.dp)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(46.dpi, 0, 16.dpi, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { results.query(s?.toString().orEmpty()) }
            })
            setOnEditorActionListener { _, action, ev ->
                if (action == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) { results.launchFirst(); true } else false
            }
        }
        bar.addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        bar.addView(object : View(home) {
            override fun onDraw(c: Canvas) = Glyphs.draw(c, Glyph.SEARCH, 26.dp, height / 2f, 18.dp, field.currentHintTextColor or 0xFF000000.toInt(), 2.4f)
        }, LayoutParams(48.dpi, LayoutParams.MATCH_PARENT))
        addView(results, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, 52.dpi))
        spring.onUpdate = { v -> progress = v; apply() }
        spring.onEnd = { if (progress < 0.01f) { visibility = GONE; field.setText("") } }
        setOnClickListener { close() }
        setOnApplyWindowInsetsListener { _, ins ->
            val ime = ins.getInsets(WindowInsets.Type.ime()).bottom
            results.setPadding(0, results.paddingTop, 0, ime + 20.dpi)
            ins
        }
    }

    fun themeChanged() {
        field.setTextColor(Theme.text)
        field.setHintTextColor(Theme.text2)
        results.query(field.text.toString())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val g = home.geo
        val m = 16.dpi
        val y = g.insetTop + 14.dpi
        bar.layout(m, y, width - m, y + 52.dpi)
        results.setPadding(0, y + 52.dpi + 18.dpi, 0, results.paddingBottom)
        results.layout(0, 0, width, height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        bar.measure(MeasureSpec.makeMeasureSpec(measuredWidth - 32.dpi, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(52.dpi, MeasureSpec.EXACTLY))
    }

    fun beginPull() {
        if (isOpen) return
        pulling = true
        visibility = VISIBLE
        themeChanged()
        results.query("")
        spring.cancel()
    }

    fun pull(dy: Float) {
        if (!pulling) return
        progress = (dy / 260.dp).coerceIn(0f, 1.15f)
        spring.setValue(progress)
        apply()
    }

    fun endPull(vy: Float) {
        if (!pulling) return
        pulling = false
        if (progress > 0.35f || vy > 900.dp) open() else { spring.animateTo(0f) }
    }

    fun open() {
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            themeChanged()
            results.query("")
            spring.snapTo(0f)
        }
        isOpen = true
        spring.animateTo(1f)
        field.requestFocus()
        home.showKeyboard(field)
    }

    fun close() {
        if (visibility != VISIBLE) return
        isOpen = false
        home.hideKeyboard(field)
        field.clearFocus()
        spring.animateTo(0f)
    }

    private fun apply() {
        val p = progress
        home.setOverlayBlur(p.coerceIn(0f, 1f), strong = true)
        bar.alpha = p.coerceIn(0f, 1f)
        bar.translationY = -(1f - p.coerceIn(0f, 1f)) * 40.dp + (p - 1f).coerceAtLeast(0f) * 30.dp
        results.alpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
        results.translationY = (1f - p.coerceIn(0f, 1f)) * 30.dp
        bar.invalidate()
    }
}

/** Suggestions + matching apps, laid out as a 4-wide icon grid, then web search. */
@SuppressLint("ViewConstructor")
class SearchResults(private val home: HomeActivity) : ViewGroup(home) {
    private val icons = ArrayList<IconView>()
    private var keys: List<String> = emptyList()
    private var q = ""
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val glass = GlassPainter()
    private val webRect = RectF()
    private var header = "Suggestions"

    init { setWillNotDraw(false) }

    fun query(s: String) {
        q = s.trim()
        keys = if (q.isEmpty()) {
            val top = Prefs.topLaunched(16).filter { AppRepository[it] != null }
            (top + AppRepository.visibleApps().map { it.key }).distinct().take(8)
        } else {
            val lq = q.lowercase()
            val all = AppRepository.apps.values
            val starts = all.filter { it.label.lowercase().startsWith(lq) }
            val words = all.filter { it !in starts && it.label.lowercase().split(' ', '-', '.').any { w -> w.startsWith(lq) } }
            val contains = all.filter { it !in starts && it !in words && it.label.lowercase().contains(lq) }
            (starts + words + contains).map { it.key }.take(16)
        }
        header = if (q.isEmpty()) "Suggestions" else if (keys.isEmpty()) "No apps found" else "Apps"
        while (icons.size > keys.size) removeView(icons.removeAt(icons.size - 1))
        while (icons.size < keys.size) {
            val v = IconView(home)
            v.onPanel = true
            v.canRemove = false
            icons.add(v)
            addView(v)
        }
        for ((i, k) in keys.withIndex()) {
            icons[i].bindApp(AppItem(-100L - i, k))
            icons[i].onPanel = !Theme.dark
        }
        requestLayout()
        invalidate()
    }

    fun launchFirst() {
        val k = keys.firstOrNull()
        if (k != null && q.isNotEmpty()) { home.launchApp(k, icons.firstOrNull()); home.search.close() }
        else if (q.isNotEmpty()) webSearch()
    }

    private fun webSearch() {
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q)
        try { home.startActivity(i) } catch (e: Exception) {
            try { home.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))) } catch (_: Exception) {}
        }
        home.search.close()
    }

    private val cols = 4
    private fun cellW() = (width - 2 * 12.dp) / cols
    private fun cellH() = home.geo.iconSize + 36.dp
    private fun gridTop() = paddingTop + 34.dp

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val cw = ((measuredWidth - 2 * 12.dp) / cols).roundToInt()
        for (v in icons) v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH().roundToInt(), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = cellW()
        for ((i, v) in icons.withIndex()) {
            v.iconTopOverride = 6.dp
            val x = (12.dp + (i % cols) * cw).roundToInt()
            val y = (gridTop() + (i / cols) * cellH()).roundToInt()
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        }
    }

    override fun onDraw(c: Canvas) {
        text.color = if (Theme.dark) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()
        text.textSize = 15.dp
        text.typeface = Ui.font(600)
        c.drawText(header, 22.dp, paddingTop + 20.dp, text)
        if (q.isNotEmpty()) {
            val rows = ceil(icons.size / cols.toFloat()).toInt()
            val top = gridTop() + rows * cellH() + 12.dp
            webRect.set(16.dp, top, width - 16.dp, top + 54.dp)
            glass.drawRoundRect(c, this, webRect.left, webRect.top, webRect.right, webRect.bottom, 18.dp)
            Glyphs.draw(c, Glyph.SEARCH, webRect.left + 28.dp, webRect.centerY(), 18.dp, Theme.text, 2.4f)
            text.color = Theme.text
            text.textSize = 16.dp
            text.typeface = Ui.font(500)
            val label = TextUtils.ellipsize("Search the web for “$q”", text, webRect.width() - 70.dp, TextUtils.TruncateAt.END)
            c.drawText(label, 0, label.length, webRect.left + 52.dp, webRect.centerY() + 6.dp, text)
        } else webRect.setEmpty()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            if (webRect.contains(ev.x, ev.y)) { webSearch(); return true }
            home.search.close()
        }
        return true
    }
}

// =====================================================================================
// Long-press menu
// =====================================================================================

class MenuEntry(
    val title: String,
    val glyph: Glyph? = null,
    val icon: Drawable? = null,
    val destructive: Boolean = false,
    val action: () -> Unit
)

/** iOS-style context menu: the pressed icon lifts above a blur, a glass menu appears beside it. */
@SuppressLint("ViewConstructor")
class PopupMenu(private val home: HomeActivity) : FrameLayout(home) {
    private val menu = MenuPanel()
    private val lifted = LiftedView()
    private var entries: List<MenuEntry> = emptyList()
    private val spring = Spring(560f, 0.78f, 0.001f)
    private var p = 0f
    var isShowing = false
        private set
    private val anchor = RectF()

    init {
        visibility = GONE
        isClickable = true
        addView(lifted, LayoutParams(0, 0))
        addView(menu, LayoutParams(0, 0))
        spring.onUpdate = { v -> p = v; apply() }
        spring.onEnd = { if (!isShowing) { visibility = GONE; home.onPopupGone() } }
        setOnClickListener { dismiss() }
    }

    fun show(anchorInWindow: RectF, snapshot: Bitmap?, list: List<MenuEntry>) {
        entries = list
        anchor.set(anchorInWindow)
        lifted.bmp = snapshot
        isShowing = true
        visibility = VISIBLE
        val g = home.geo
        val mw = min(250.dp, g.width - 32.dp).roundToInt()
        val mh = (list.size * 48.dp).roundToInt()
        val below = anchor.bottom + 14.dp + mh < g.height - g.insetBottom - 20.dp
        val mx = (anchor.centerX() - mw / 2f).coerceIn(16.dp, g.width - 16.dp - mw)
        val my = if (below) anchor.bottom + 14.dp else anchor.top - 14.dp - mh
        (menu.layoutParams as LayoutParams).apply { width = mw; height = mh; leftMargin = mx.roundToInt(); topMargin = my.roundToInt() }
        menu.pivotX = (anchor.centerX() - mx).coerceIn(0f, mw.toFloat())
        menu.pivotY = if (below) 0f else mh.toFloat()
        (lifted.layoutParams as LayoutParams).apply {
            width = anchor.width().roundToInt(); height = anchor.height().roundToInt()
            leftMargin = anchor.left.roundToInt(); topMargin = anchor.top.roundToInt()
        }
        requestLayout()
        menu.invalidate()
        spring.snapTo(0f)
        spring.animateTo(1f)
    }

    fun dismiss(immediate: Boolean = false) {
        if (!isShowing) return
        isShowing = false
        if (immediate) {
            spring.cancel()
            p = 0f
            apply()
            visibility = GONE
            home.onPopupGone()
        } else spring.animateTo(0f)
    }

    private fun apply() {
        home.setOverlayBlur(p.coerceIn(0f, 1f))
        val k = p.coerceIn(0f, 1.3f)
        menu.alpha = k.coerceIn(0f, 1f)
        val ms = 0.6f + 0.4f * k
        menu.scaleX = ms; menu.scaleY = ms
        val ls = 1f + 0.06f * k
        lifted.scaleX = ls; lifted.scaleY = ls
        lifted.alpha = if (isShowing || p > 0.05f) 1f else 0f
    }

    private inner class LiftedView : View(home) {
        var bmp: Bitmap? = null
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val b = bmp ?: return
            r.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawBitmap(b, null, r, paint)
        }
    }

    private inner class MenuPanel : GlassPanel(home) {
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val sep = Paint()
        private val hl = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pressed = -1
        init {
            radius = 18.dp
            glass.tintColor = if (Theme.dark) 0x99202024.toInt() else 0xB3F5F5F7.toInt()
            isClickable = true
        }

        override fun onDraw(c: Canvas) {
            glass.tintColor = if (Theme.dark) 0x8C1E1E22.toInt() else 0xB8F7F7F9.toInt()
            super.onDraw(c)
            val rowH = 48.dp
            tp.textSize = 16.5f.dp
            tp.typeface = Ui.font(400)
            sep.color = Theme.separator
            for ((i, e) in entries.withIndex()) {
                val top = i * rowH
                if (i == pressed) {
                    hl.color = if (Theme.dark) 0x1FFFFFFF else 0x14000000
                    c.drawRect(0f, top, width.toFloat(), top + rowH, hl)
                }
                val col = if (e.destructive) Theme.red else Theme.text
                tp.color = col
                val title = TextUtils.ellipsize(e.title, tp, width - 70.dp, TextUtils.TruncateAt.END)
                c.drawText(title, 0, title.length, 16.dp, top + rowH / 2f + tp.textSize * 0.35f, tp)
                val ic = e.icon
                if (ic != null) {
                    val s = 22.dpi
                    val l = (width - 16.dp - s).roundToInt()
                    val t = (top + (rowH - s) / 2f).roundToInt()
                    ic.setBounds(l, t, l + s, t + s)
                    ic.draw(c)
                } else e.glyph?.let { Glyphs.draw(c, it, width - 27.dp, top + rowH / 2f, 20.dp, col, 2f) }
                if (i < entries.size - 1) c.drawRect(0f, top + rowH - 0.5f.dp, width.toFloat(), top + rowH, sep)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val idx = (ev.y / 48.dp).toInt()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { val n = if (ev.x in 0f..width.toFloat() && idx in entries.indices) idx else -1; if (n != pressed) { pressed = n; invalidate() } }
                MotionEvent.ACTION_UP -> {
                    val e = entries.getOrNull(pressed)
                    pressed = -1
                    invalidate()
                    if (e != null) {
                        dismiss()
                        e.action()
                    }
                }
                MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
            }
            return true
        }
    }
}

/** Small glass pill button ("Done", "+", gear) shown at the top in edit mode. */
@SuppressLint("ViewConstructor")
class PillButton(ctx: Context, private val label: String?, private val glyph: Glyph?, private val bold: Boolean = false) : View(ctx), HasGlass {
    private val glass = GlassPainter()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var press = 0f
    private val spring = Spring(900f, 0.8f, 0.002f).apply { onUpdate = { press = it; invalidate() } }

    fun preferredWidth(): Int {
        if (label == null) return 40.dpi
        tp.textSize = 16.dp
        tp.typeface = Ui.font(if (bold) 600 else 500)
        return (tp.measureText(label) + 32.dp).roundToInt()
    }

    override fun onDraw(c: Canvas) {
        val sc = 1f - 0.06f * press
        c.save()
        c.scale(sc, sc, width / 2f, height / 2f)
        glass.overlay = if (press > 0.05f) Color.argb((40 * press).toInt(), 255, 255, 255) else 0
        glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), height.toFloat(), height / 2f)
        val col = if (Theme.dark) Color.WHITE else 0xFF1C1C1E.toInt()
        if (label != null) {
            tp.color = col
            tp.textSize = 16.dp
            tp.typeface = Ui.font(if (bold) 600 else 500)
            tp.textAlign = Paint.Align.CENTER
            c.drawText(label, width / 2f, height / 2f + tp.textSize * 0.35f, tp)
        } else glyph?.let { Glyphs.draw(c, it, width / 2f, height / 2f, 20.dp, col, 2.4f) }
        c.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> spring.animateTo(1f)
            MotionEvent.ACTION_UP -> { spring.animateTo(0f); if (ev.x in 0f..width.toFloat() && ev.y in 0f..height.toFloat()) performClick() }
            MotionEvent.ACTION_CANCEL -> spring.animateTo(0f)
        }
        return true
    }
}
