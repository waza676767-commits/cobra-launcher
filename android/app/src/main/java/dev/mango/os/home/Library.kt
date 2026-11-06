package dev.mango.os.home

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
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
import android.view.ViewGroup
import android.widget.ScrollView
import dev.mango.os.core.Prefs
import dev.mango.os.core.Spring
import dev.mango.os.core.Theme
import dev.mango.os.core.Ui
import dev.mango.os.core.dp
import dev.mango.os.data.AppEntry
import dev.mango.os.data.AppRepository
import dev.mango.os.gfx.GlassPainter
import dev.mango.os.gfx.Glyph
import dev.mango.os.gfx.Glyphs
import dev.mango.os.gfx.IconFactory
import dev.mango.os.gfx.Squircle
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sorts every app into iOS-style App Library groups. */
object LibraryGroups {
    class Group(val name: String, val keys: List<String>)

    private val rules = listOf(
        "Social" to listOf("whatsapp", "instagram", "facebook", "messenger", "telegram", "snapchat", "musically", "tiktok", "twitter",
            "discord", "reddit", "threads", "signal", "viber", "linkedin", "pinterest", "messaging", ".mms", "contacts", "dialer", "phone"),
        "Entertainment" to listOf("youtube", "netflix", "primevideo", "disney", "hbo", "max.", "twitch", "video", ".tv", "voyo", "crunchyroll"),
        "Photo & Video" to listOf("camera", "gallery", "photos", "snapseed", "lightroom", "picsart", "capcut", "editor"),
        "Music" to listOf("spotify", "music", "soundcloud", "shazam", "deezer", "audio", "radio", "podcast"),
        "Productivity" to listOf("docs", "sheets", "slides", "office", "notes", "calendar", "keep", "drive", "android.gm", "email", "outlook",
            "mail", "todo", "notion", "onenote", "word", "excel", "powerpoint", "classroom", "teams", "zoom", "meet"),
        "Shopping" to listOf("amazon", "ebay", "shop", "emag", "olx", "temu", "aliexpress", "shein", "vinted"),
        "Travel" to listOf("maps", "uber", "bolt", "waze", "booking", "airbnb", "transit", "ryanair", "wizz", "glovo", "wolt", "tazz"),
        "Finance" to listOf("bank", "revolut", "paypal", "wallet", ".pay", "banca", "ing.", "raiffeisen", "bt.", "george"),
        "News" to listOf("news", "bbc", "cnn", "digi24", "hotnews"),
        "Games" to listOf("game", "games", "roblox", "minecraft", "supercell", "king.", "epicgames")
    )

    fun categoryOf(e: AppEntry): String {
        when (e.category) {
            ApplicationInfo.CATEGORY_GAME -> return "Games"
            ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
            ApplicationInfo.CATEGORY_VIDEO -> return "Entertainment"
            ApplicationInfo.CATEGORY_IMAGE -> return "Photo & Video"
            ApplicationInfo.CATEGORY_AUDIO -> return "Music"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity"
            ApplicationInfo.CATEGORY_MAPS -> return "Travel"
            ApplicationInfo.CATEGORY_NEWS -> return "News"
        }
        val p = e.pkg.lowercase()
        for ((name, words) in rules) if (words.any { p.contains(it) }) return name
        return if (e.isSystem) "Utilities" else "Other"
    }

    fun build(): List<Group> {
        val all = AppRepository.apps.values.toList()
        if (all.isEmpty()) return emptyList()
        val out = ArrayList<Group>()
        val suggestions = Prefs.topLaunched(12).filter { AppRepository[it] != null }.take(8)
            .ifEmpty { all.take(8).map { it.key } }
        out.add(Group("Suggestions", suggestions))
        out.add(Group("Recently Added", all.sortedByDescending { it.firstInstall }.take(8).map { it.key }))
        val byCat = LinkedHashMap<String, MutableList<AppEntry>>()
        for (e in all) byCat.getOrPut(categoryOf(e)) { ArrayList() }.add(e)
        val order = listOf("Social", "Entertainment", "Photo & Video", "Productivity", "Shopping", "Travel", "Finance", "Music", "News", "Games", "Utilities", "Other")
        for (name in order) {
            val list = byCat[name] ?: continue
            // most used first, like iOS
            out.add(Group(name, list.sortedByDescending { Prefs.launchCount(it.key) }.map { it.key }))
        }
        return out
    }
}

/**
 * The last page: an "App Library" search bar and every app grouped by category.
 * Tap an icon to open it, tap the small cluster to see the whole group.
 */
@SuppressLint("ViewConstructor")
class LibraryPage(private val home: HomeActivity) : ViewGroup(home), HasGlass {
    private val bar = SearchBar()
    val scroll = ScrollView(home).apply { isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER; clipToPadding = false }
    val grid = LibraryGrid(home)

    init {
        scroll.addView(grid)
        addView(scroll)
        addView(bar)
        bar.setOnClickListener { home.search.open() }
    }

    fun refresh() {
        grid.reload()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val bh = 46.dp.roundToInt()
        bar.measure(MeasureSpec.makeMeasureSpec(w - 2 * 18.dp.roundToInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY))
        scroll.setPadding(0, bh + 22.dp.roundToInt(), 0, 24.dp.roundToInt())
        scroll.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        scroll.layout(0, 0, r - l, b - t)
        val m = 18.dp.roundToInt()
        bar.layout(m, 6.dp.roundToInt(), r - l - m, 6.dp.roundToInt() + bar.measuredHeight)
    }

    fun invalidateGlass() {
        bar.invalidate()
        grid.invalidate()
    }

    /** Glass "App Library" search field. */
    private inner class SearchBar : View(home) {
        private val glass = GlassPainter()
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            glass.drawRoundRect(c, this, 0f, 0f, width.toFloat(), height.toFloat(), height / 2f)
            val col = if (Theme.dark) 0xB3FFFFFF.toInt() else 0x99000000.toInt()
            Glyphs.draw(c, Glyph.SEARCH, 26.dp, height / 2f, 18.dp, col, 2.4f)
            tp.color = col
            tp.textSize = 17.dp
            tp.typeface = Ui.font(500)
            c.drawText("App Library", 46.dp, height / 2f + 6.dp, tp)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            return true
        }
    }
}

/** Two-column grid of group tiles (drawn directly: one view, cheap to scroll). */
@SuppressLint("ViewConstructor")
class LibraryGrid(private val home: HomeActivity) : View(home), HasGlass {
    private var groups: List<LibraryGroups.Group> = emptyList()
    private val glass = GlassPainter()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val path = Path()
    private var pressed = -1
    private var pressV = 0f
    private val press = Spring(1100f, 0.75f, 0.002f).apply { onUpdate = { pressV = it; invalidate() } }
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(home).scaledTouchSlop

    fun reload() {
        groups = LibraryGroups.build()
        requestLayout()
        invalidate()
    }

    private val pad get() = 20.dp
    private val gap get() = 18.dp
    private val tileSize get() = (width - pad * 2 - gap) / 2f
    private val rowH get() = tileSize + 30.dp

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val ts = (w - pad * 2 - gap) / 2f
        val rows = (groups.size + 1) / 2
        setMeasuredDimension(w, (rows * (ts + 30.dp)).roundToInt())
    }

    private fun tileRect(i: Int, out: RectF) {
        val ts = tileSize
        val x = pad + (i % 2) * (ts + gap)
        val y = (i / 2) * rowH
        out.set(x, y, x + ts, y + ts)
    }

    /** Slot 0-2: big icons, slot 3: 4th icon or a cluster of minis. */
    private fun slotRect(tile: RectF, slot: Int, out: RectF) {
        val inner = tile.width() * 0.1f
        val g = tile.width() * 0.08f
        val s = (tile.width() - inner * 2 - g) / 2f
        val x = tile.left + inner + (slot % 2) * (s + g)
        val y = tile.top + inner + (slot / 2) * (s + g)
        out.set(x, y, x + s, y + s)
    }

    override fun onDraw(c: Canvas) {
        val slot = RectF()
        val mini = RectF()
        val radius = tileSize * 0.2f
        for ((i, g) in groups.withIndex()) {
            tileRect(i, r)
            glass.drawRoundRect(c, this, r.left, r.top, r.right, r.bottom, radius)
            val keys = g.keys
            val cluster = keys.size > 4
            for (k in 0 until minOf(if (cluster) 3 else 4, keys.size)) {
                slotRect(r, k, slot)
                drawIcon(c, keys[k], slot, i * 10 + k)
            }
            if (cluster) {
                slotRect(r, 3, slot)
                val mg = slot.width() * 0.1f
                val ms = (slot.width() - mg) / 2f
                for (m in 0 until minOf(4, keys.size - 3)) {
                    val mx = slot.left + (m % 2) * (ms + mg)
                    val my = slot.top + (m / 2) * (ms + mg)
                    mini.set(mx, my, mx + ms, my + ms)
                    drawIcon(c, keys[3 + m], mini, -1)
                }
                if (pressed == i * 10 + 3 && pressV > 0.01f) {
                    shade.color = Color.argb((60 * pressV).toInt(), 0, 0, 0)
                    c.drawRoundRect(slot, slot.width() * 0.22f, slot.width() * 0.22f, shade)
                }
            }
            label.color = if (Theme.dark) Color.WHITE else 0xFF1C1C1E.toInt()
            label.textSize = 13.5f.dp
            label.typeface = Ui.font(600)
            label.setShadowLayer(if (Theme.dark) 3.dp else 0f, 0f, 1f, 0x55000000)
            val name = TextUtils.ellipsize(g.name, label, r.width(), TextUtils.TruncateAt.END)
            c.drawText(name, 0, name.length, r.centerX(), r.bottom + 20.dp, label)
        }
    }

    private fun drawIcon(c: Canvas, key: String, dst: RectF, pressId: Int) {
        val s = dst.width()
        if (IconFactory.isClear) {
            Squircle.into(path, dst.left, dst.top, s)
            shade.color = if (Theme.dark) 0x33FFFFFF else 0x24000000
            c.drawPath(path, shade)
        }
        val b = IconFactory.get(key) { invalidate() }
        val sc = if (pressId == pressed) 1f - 0.08f * pressV else 1f
        c.save()
        c.scale(sc, sc, dst.centerX(), dst.centerY())
        if (b != null) c.drawBitmap(b, null, dst, bmpPaint)
        if (pressId == pressed && pressV > 0.01f) {
            Squircle.into(path, dst.left, dst.top, s)
            shade.color = Color.argb((70 * pressV).toInt(), 0, 0, 0)
            c.drawPath(path, shade)
        }
        c.restore()
    }

    /** Which icon (group*10+slot) is under (x, y), or -1. */
    private fun hit(x: Float, y: Float): Int {
        val slot = RectF()
        for (i in groups.indices) {
            tileRect(i, r)
            if (!r.contains(x, y)) continue
            for (k in 0 until 4) {
                slotRect(r, k, slot)
                if (slot.contains(x, y)) return if (k < groups[i].keys.size) i * 10 + k else -1
            }
            return -1
        }
        return -1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                pressed = hit(ev.x, ev.y)
                if (pressed >= 0) press.animateTo(1f)
            }
            MotionEvent.ACTION_MOVE -> if (pressed >= 0 && (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop)) {
                press.animateTo(0f); pressed = -1
            }
            MotionEvent.ACTION_UP -> {
                val id = pressed
                press.animateTo(0f)
                if (id >= 0) {
                    val g = groups.getOrNull(id / 10)
                    val k = id % 10
                    if (g != null) {
                        if (k == 3 && g.keys.size > 4) openGroup(g, id / 10)
                        else g.keys.getOrNull(k)?.let { key ->
                            tileRect(id / 10, r)
                            val slot = RectF()
                            slotRect(r, k, slot)
                            home.launchApp(key, this, slot)
                        }
                    }
                }
                pressed = -1
            }
            MotionEvent.ACTION_CANCEL -> { press.animateTo(0f); pressed = -1 }
        }
        return true
    }

    private fun openGroup(g: LibraryGroups.Group, index: Int) {
        tileRect(index, r)
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val from = RectF(r)
        from.offset(loc[0].toFloat(), loc[1].toFloat())
        home.openLibraryGroup(g, from)
    }
}
