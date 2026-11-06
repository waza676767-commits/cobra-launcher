package dev.aura.launcher.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import dev.aura.launcher.core.Spring
import dev.aura.launcher.core.Theme
import dev.aura.launcher.core.Ui
import dev.aura.launcher.core.dp
import dev.aura.launcher.data.FolderItem
import dev.aura.launcher.data.Item
import dev.aura.launcher.data.Packer
import dev.aura.launcher.data.Placement
import dev.aura.launcher.data.WidgetItem
import dev.aura.launcher.gfx.Backdrop
import dev.aura.launcher.gfx.GlassPainter
import dev.aura.launcher.gfx.Glyph
import dev.aura.launcher.gfx.Glyphs
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

class Hit(val index: Int, val over: Item?, val center: Boolean)

/** Somewhere an icon can be dropped: a page, the dock, an open folder. */
interface DropContainer {
    fun canAccept(item: Item): Boolean
    fun hit(x: Float, y: Float, item: Item): Hit
    fun indexOf(item: Item): Int
    fun insert(item: Item, index: Int)
    fun remove(item: Item)
    fun moveTo(item: Item, index: Int)
    fun refresh(animate: Boolean)
    fun viewFor(item: Item): View?
}

/** Animates children from where they were to where the new layout puts them (FLIP). */
private fun animateFrom(v: View, old: Point?, animate: Boolean) {
    if (!animate || old == null) return
    val dx = (old.x - v.left).toFloat()
    val dy = (old.y - v.top).toFloat()
    if (abs(dx) < 1f && abs(dy) < 1f) return
    v.animate().cancel()
    v.translationX = dx
    v.translationY = dy
    v.animate().translationX(0f).translationY(0f)
        .setDuration(Ui.ms(if (Theme.reduceMotion) 160 else 360))
        .setInterpolator(Ui.easeOut)
        .withEndAction { if (v is HasGlass) v.invalidate() }
        .setUpdateListener { if (v is HasGlass) v.invalidate() }
        .start()
}

/** One home screen page: a grid laid out with [Packer]. */
class PageView(val home: HomeActivity) : ViewGroup(home), DropContainer {
    var items: MutableList<Item> = ArrayList()
        private set
    private val views = HashMap<Long, View>()
    private val placed = ArrayList<Placement>()
    private val byId = HashMap<Long, Placement>()
    private var grid: Array<Array<Item?>> = emptyArray()
    private val oldPos = HashMap<Long, Point>()
    private var animateNext = false
    private val rect = RectF()
    private val loc = IntArray(2)

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun bind(list: MutableList<Item>) {
        items = list
        sync(false)
    }

    fun sync(animate: Boolean) {
        val g = home.geo
        placed.clear()
        val overflow = ArrayList<Item>()
        Packer.pack(items, g.cols, g.rows, placed, overflow)
        byId.clear()
        grid = Array(g.rows) { arrayOfNulls<Item>(g.cols) }
        for (p in placed) {
            byId[p.item.id] = p
            for (y in p.y until p.y + p.sy) for (x in p.x until p.x + p.sx) grid[y][x] = p.item
        }
        oldPos.clear()
        if (animate) for ((id, v) in views) oldPos[id] = Point((v.left + v.translationX).roundToInt(), (v.top + v.translationY).roundToInt())
        val it = views.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!byId.containsKey(e.key)) {
                e.value.animate().cancel()
                removeView(e.value)
                it.remove()
            }
        }
        for (p in placed) {
            var v = views[p.item.id]
            if (v == null) {
                v = home.createItemView(p.item, this)
                views[p.item.id] = v
                addView(v)
            } else {
                (v as? Editable)?.refreshContent()
            }
            v.visibility = if (p.item.id == home.drag.draggingId) View.INVISIBLE else View.VISIBLE
            (v as? Editable)?.editMode = home.editMode
        }
        animateNext = animate
        requestLayout()
        invalidate()
    }

    fun allViews(): Collection<View> = views.values

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        for (p in placed) {
            val v = views[p.item.id] ?: continue
            home.geo.rectFor(p, rect)
            v.measure(
                MeasureSpec.makeMeasureSpec(rect.width().roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rect.height().roundToInt(), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (p in placed) {
            val v = views[p.item.id] ?: continue
            home.geo.rectFor(p, rect)
            val left = rect.left.roundToInt()
            val top = rect.top.roundToInt()
            v.layout(left, top, left + v.measuredWidth, top + v.measuredHeight)
            animateFrom(v, oldPos[p.item.id], animateNext)
        }
        animateNext = false
        oldPos.clear()
    }

    // ---------------------------------------------------------------- DropContainer

    override fun canAccept(item: Item) = true

    override fun hit(x: Float, y: Float, item: Item): Hit {
        getLocationInWindow(loc)
        val g = home.geo
        val lx = x - loc[0]
        val ly = y - loc[1]
        val cx = floor((lx - g.hPad) / g.cellW).toInt().coerceIn(0, g.cols - 1)
        val cy = floor(ly / g.cellH).toInt().coerceIn(0, g.rows - 1)
        val over = grid.getOrNull(cy)?.getOrNull(cx)
        val inList = items.contains(item)
        if (over == null) return Hit(if (inList) items.size - 1 else items.size, null, false)
        val p = byId[over.id]
        var center = false
        if (p != null && over !is WidgetItem) {
            val icx = g.hPad + p.x * g.cellW + g.cellW / 2f
            val icy = p.y * g.cellH + g.iconTop + g.iconSize / 2f
            center = hypot(lx - icx, ly - icy) < g.iconSize * 0.42f
        }
        return Hit(items.indexOf(over), over, center)
    }

    override fun indexOf(item: Item) = items.indexOf(item)
    override fun insert(item: Item, index: Int) { items.add(index.coerceIn(0, items.size), item) }
    override fun remove(item: Item) { items.remove(item) }
    override fun moveTo(item: Item, index: Int) {
        val i = items.indexOf(item)
        if (i < 0) return
        items.removeAt(i)
        items.add(index.coerceIn(0, items.size), item)
    }
    override fun refresh(animate: Boolean) = sync(animate)
    override fun viewFor(item: Item): View? = views[item.id]
}

/** The floating glass dock. */
class DockView(val home: HomeActivity) : ViewGroup(home), DropContainer, HasGlass {
    var items: MutableList<Item> = ArrayList()
        private set
    private val views = HashMap<Long, View>()
    private val oldPos = HashMap<Long, Point>()
    private var animateNext = false
    private val glass = GlassPainter()
    private val loc = IntArray(2)

    init {
        setWillNotDraw(false)
        clipChildren = false
    }

    fun bind(list: MutableList<Item>) {
        items = list
        sync(false)
    }

    fun reset() {
        removeAllViews()
        views.clear()
    }

    fun sync(animate: Boolean) {
        oldPos.clear()
        if (animate) for ((id, v) in views) oldPos[id] = Point((v.left + v.translationX).roundToInt(), (v.top + v.translationY).roundToInt())
        val ids = items.map { it.id }.toHashSet()
        val it = views.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in ids) { removeView(e.value); it.remove() }
        }
        for (item in items) {
            var v = views[item.id]
            if (v == null) {
                v = home.createItemView(item, this)
                views[item.id] = v
                addView(v)
            } else (v as? Editable)?.refreshContent()
            (v as? IconView)?.let { iv -> iv.inDock = true; iv.showLabel = false }
            v.visibility = if (item.id == home.drag.draggingId) View.INVISIBLE else View.VISIBLE
            (v as? Editable)?.editMode = home.editMode
        }
        animateNext = animate
        requestLayout()
        invalidate()
    }

    fun allViews(): Collection<View> = views.values

    private val slotW: Float get() = (width - 2 * 8.dp) / home.geo.dockSlots.coerceAtLeast(1)
    private fun startX(n: Int) = (width - n * slotW) / 2f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val sw = ((w - 2 * 8.dp) / home.geo.dockSlots.coerceAtLeast(1)).roundToInt()
        for (v in views.values) v.measure(MeasureSpec.makeMeasureSpec(sw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val n = items.size
        val sx = startX(n)
        for ((i, item) in items.withIndex()) {
            val v = views[item.id] ?: continue
            val left = (sx + i * slotW).roundToInt()
            v.layout(left, 0, left + v.measuredWidth, v.measuredHeight)
            animateFrom(v, oldPos[item.id], animateNext)
        }
        animateNext = false
    }

    override fun onDraw(c: Canvas) {
        val r = height * 0.42f
        glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), height.toFloat(), r)
    }

    override fun canAccept(item: Item) = item !is WidgetItem && (items.contains(item) || items.size < home.geo.dockSlots)

    override fun hit(x: Float, y: Float, item: Item): Hit {
        getLocationInWindow(loc)
        val lx = x - loc[0]
        val inList = items.contains(item)
        val n = items.size
        val sx = startX(n)
        val slot = floor((lx - sx) / slotW).toInt()
        if (slot in 0 until n) {
            val over = items[slot]
            val cx = sx + slot * slotW + slotW / 2f
            val center = abs(lx - cx) < home.geo.iconSize * 0.4f
            return Hit(slot, over, center)
        }
        val idx = if (slot < 0) 0 else if (inList) n - 1 else n
        return Hit(idx, null, false)
    }

    override fun indexOf(item: Item) = items.indexOf(item)
    override fun insert(item: Item, index: Int) { items.add(index.coerceIn(0, items.size), item) }
    override fun remove(item: Item) { items.remove(item) }
    override fun moveTo(item: Item, index: Int) {
        val i = items.indexOf(item)
        if (i < 0) return
        items.removeAt(i)
        items.add(index.coerceIn(0, items.size), item)
    }
    override fun refresh(animate: Boolean) = sync(animate)
    override fun viewFor(item: Item): View? = views[item.id]
}

/** Horizontal pager holding the pages. Spring-driven, rubber-bands at the ends. */
class Workspace(val home: HomeActivity) : ViewGroup(home) {
    val pages = ArrayList<PageView>()
    var current = 0
        private set
    private val spring = Spring(520f, 0.94f, 0.5f)
    private var raw = 0f
    var touching = false
        private set

    init {
        clipChildren = false
        spring.onUpdate = { v -> scrollTo(v.roundToInt(), 0) }
        spring.onEnd = { home.onPageSettled(current) }
    }

    fun reset() {
        removeAllViews()
        pages.clear()
    }

    fun bindPages(lists: List<MutableList<Item>>) {
        while (pages.size > lists.size) removeView(pages.removeAt(pages.size - 1))
        while (pages.size < lists.size) PageView(home).also { pages.add(it); addView(it) }
        for ((i, l) in lists.withIndex()) pages[i].bind(l)
        if (current >= pages.size) current = pages.size - 1
        requestLayout()
        if (width > 0) scrollTo(current * width, 0)
    }

    val position: Float get() = if (width == 0) 0f else scrollX / width.toFloat()
    val isSettling: Boolean get() = spring.running
    val currentPage: PageView? get() = pages.getOrNull(current)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val ws = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (p in pages) p.measure(ws, hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for ((i, p) in pages.withIndex()) p.layout(i * w, 0, (i + 1) * w, b - t)
        if (!spring.running && !touching) scrollTo(current * w, 0)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child.right <= scrollX - 4 || child.left >= scrollX + width + 4) return false
        return super.drawChild(canvas, child, drawingTime)
    }

    fun beginDrag() {
        spring.cancel()
        touching = true
        raw = scrollX.toFloat()
    }

    fun dragBy(dx: Float) {
        raw += dx
        val max = ((pages.size - 1) * width).toFloat()
        val shown = when {
            raw < 0f -> -rubber(-raw)
            raw > max -> max + rubber(raw - max)
            else -> raw
        }
        scrollTo(shown.roundToInt(), 0)
    }

    private fun rubber(x: Float): Float {
        val d = width.toFloat().coerceAtLeast(1f)
        return (1f - 1f / (x * 0.55f / d + 1f)) * d
    }

    fun release(velocityX: Float) {
        touching = false
        if (width == 0) return
        val pos = scrollX / width.toFloat()
        val v = -velocityX
        val threshold = 300.dp
        val target = when {
            v > threshold -> kotlin.math.ceil(pos.toDouble()).toInt()
            v < -threshold -> floor(pos.toDouble()).toInt()
            else -> pos.roundToInt()
        }
        snapTo(target, v)
    }

    fun snapTo(index: Int, velocity: Float = 0f, animate: Boolean = true) {
        val t = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val changed = t != current
        current = t
        if (!animate || width == 0) {
            spring.cancel()
            scrollTo(t * width, 0)
            home.onPageSettled(t)
            return
        }
        spring.setValue(scrollX.toFloat())
        spring.animateTo((t * width).toFloat(), velocity)
        if (changed) home.onPageChanging(t)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        home.onWorkspaceScrolled(position)
    }
}

/** "Search" pill above the dock that turns into page dots while you swipe. */
class PagePill(val home: HomeActivity) : View(home), HasGlass {
    var count = 1
        set(v) { field = v; invalidate() }
    var position = 0f
        set(v) { field = v; invalidate() }
    private var dots = 0f
    private val spring = Spring(320f, 1f, 0.002f).apply { onUpdate = { dots = it; invalidate() } }
    private val glass = GlassPainter()
    private val text = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(500) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hide = Runnable { if (!home.workspace.touching) spring.animateTo(0f) }
    private val pill = RectF()

    init {
        setOnClickListener { if (home.searchPillOn && !home.editMode) home.search.open() }
    }

    fun showDots() {
        if (count <= 1) return
        spring.animateTo(1f)
        removeCallbacks(hide)
        postDelayed(hide, 1400)
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val dotR = 3.6f.dp
        val gap = 12.dp
        val dotsW = count * gap + 14.dp
        val showSearch = home.searchPillOn
        val d = if (showSearch && count > 1) dots else if (showSearch) 0f else 1f
        text.textSize = 15.dp
        val label = "Search"
        val searchW = text.measureText(label) + 16.dp + 34.dp
        val w = if (showSearch) searchW + (dotsW - searchW) * d else dotsW
        val l = (width - w) / 2f
        pill.set(l, 0f, l + w, h)
        if (showSearch && !(Backdrop.blurred == null && !Theme.reduceTransparency)) {
            glass.drawRoundRect(c, this, pill.left, pill.top, pill.right, pill.bottom, h / 2f)
        }
        if (showSearch && d < 1f) {
            val a = 1f - d
            val col = if (Theme.dark || Backdrop.topLuminance < 0.6f) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
            text.color = col
            text.alpha = (230 * a).toInt()
            val tw = text.measureText(label)
            val gx = width / 2f - (tw + 22.dp) / 2f
            Glyphs.draw(c, Glyph.SEARCH, gx + 8.dp, h / 2f, 16.dp, (text.color and 0x00FFFFFF) or ((230 * a).toInt() shl 24), 2.4f)
            c.drawText(label, gx + 22.dp, h / 2f + text.textSize * 0.35f, text)
        }
        if (count > 1 && d > 0f) {
            val sx = width / 2f - (count - 1) * gap / 2f
            for (i in 0 until count) {
                val near = (1f - abs(position - i)).coerceIn(0f, 1f)
                dot.color = 0xFFFFFFFF.toInt()
                dot.alpha = ((0.35f + 0.65f * near) * 255 * d).toInt()
                c.drawCircle(sx + i * gap, h / 2f, dotR, dot)
            }
        }
    }
}
