package dev.mango.os.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import dev.mango.os.core.Spring
import dev.mango.os.core.Ui
import dev.mango.os.core.dp
import dev.mango.os.data.AppItem
import dev.mango.os.data.FolderItem
import dev.mango.os.data.Item
import dev.mango.os.data.WidgetItem

/** The picked-up icon that follows the finger. */
class DragView(home: HomeActivity, private val bmp: Bitmap) : View(home) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    override fun onDraw(c: Canvas) {
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawBitmap(bmp, null, dst, paint)
    }
}

/**
 * iOS-style rearranging: the item keeps its slot (invisible) while you drag; hovering over a
 * new spot moves that slot, so the other icons slide out of the way. Hover on an icon's
 * centre to make a folder, hold at the screen edge to change page.
 */
class DragController(private val home: HomeActivity) {
    var active = false
        private set
    var draggingId = -1L
        private set
    private var item: Item? = null
    private var container: DropContainer? = null
    private var view: DragView? = null
    private var offX = 0f
    private var offY = 0f
    private var baseW = 0
    private var baseH = 0
    private var lastX = 0f
    private var lastY = 0f

    private var hoverItem: Item? = null
    private var hoverContainer: DropContainer? = null
    private var hoverSince = 0L
    private var folderReady = false
    private var pendingIndex = -1
    private var pendingSince = 0L
    private var edgeSince = 0L
    private var edgeDir = 0

    private val lift = Spring(700f, 0.7f, 0.002f)
    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            evaluate(lastX, lastY)
            home.root.postDelayed(this, 50)
        }
    }

    fun start(source: View, item: Item, from: DropContainer, x: Float, y: Float) {
        if (active) return
        home.enterEditMode()
        active = true
        this.item = item
        container = from
        draggingId = item.id

        val loc = IntArray(2)
        source.getLocationInWindow(loc)
        val bmp: Bitmap
        val left: Float
        val top: Float
        if (source is IconView) {
            bmp = source.snapshotIcon()
            left = loc[0] + source.iconLeft()
            top = loc[1] + source.iconTop()
        } else {
            val e = source as? Editable
            val was = e?.editMode ?: false
            e?.editMode = false
            bmp = home.snapshot(source)
            e?.editMode = was
            left = loc[0].toFloat()
            top = loc[1].toFloat()
        }
        baseW = bmp.width
        baseH = bmp.height
        offX = x - left
        offY = y - top
        val dv = DragView(home, bmp)
        home.root.addView(dv, android.widget.FrameLayout.LayoutParams(baseW, baseH))
        dv.pivotX = offX
        dv.pivotY = offY
        dv.translationX = left
        dv.translationY = top
        dv.elevation = 0f
        view = dv
        lift.onUpdate = { s -> dv.scaleX = s; dv.scaleY = s; dv.alpha = 1f - (s - 1f) * 0.6f }
        lift.snapTo(1f)
        lift.animateTo(if (item is WidgetItem) 1.04f else 1.14f)
        source.visibility = View.INVISIBLE
        Ui.thud(home.root)
        lastX = x; lastY = y
        home.root.postDelayed(ticker, 50)
    }

    fun move(x: Float, y: Float) {
        if (!active) return
        lastX = x; lastY = y
        view?.let { it.translationX = x - offX; it.translationY = y - offY }
        evaluate(x, y)
    }

    private fun evaluate(x: Float, y: Float) {
        val it = item ?: return
        val now = SystemClock.uptimeMillis()
        val target = home.dropTargetAt(x, y, it) ?: return
        val cur = container ?: return
        if (target !== cur) {
            if (!target.canAccept(it)) { clearHover(); return }
            val hit = target.hit(x, y, it)
            cur.remove(it)
            cur.refresh(true)
            target.insert(it, hit.index)
            target.refresh(true)
            container = target
            clearHover()
            pendingIndex = -1
            home.reflowDuringDrag()
            return
        }
        val hit = target.hit(x, y, it)
        val over = hit.over
        if (over != null && over !== it && hit.center && it is AppItem && over !is WidgetItem) {
            if (hoverItem !== over) {
                clearHover()
                hoverItem = over
                hoverContainer = target
                hoverSince = now
            } else if (!folderReady && now - hoverSince > 380) {
                folderReady = true
                (target.viewFor(over) as? IconView)?.setFolderHover(true)
                Ui.tick(home.root)
            }
            pendingIndex = -1
        } else {
            clearHover()
            val curIdx = target.indexOf(it)
            val want = if (over === it) curIdx else hit.index
            if (want != curIdx && want >= 0 && curIdx >= 0) {
                if (pendingIndex != want) {
                    pendingIndex = want
                    pendingSince = now
                } else if (now - pendingSince > 200) {
                    target.moveTo(it, want)
                    target.refresh(true)
                    pendingIndex = -1
                    home.reflowDuringDrag()
                }
            } else pendingIndex = -1
        }

        // hold near the screen edge to flip pages
        val edge = 22.dp
        val dir = if (target is PageView) when {
            x < edge -> -1
            x > home.root.width - edge -> 1
            else -> 0
        } else 0
        if (dir == 0) {
            edgeSince = 0L; edgeDir = 0
        } else if (dir != edgeDir) {
            edgeDir = dir; edgeSince = now
        } else if (now - edgeSince > 600) {
            edgeSince = now
            home.flipPageWhileDragging(dir)
        }
    }

    private fun clearHover() {
        if (folderReady) {
            hoverItem?.let { h -> (hoverContainer?.viewFor(h) as? IconView)?.setFolderHover(false) }
        }
        hoverItem = null
        hoverContainer = null
        folderReady = false
    }

    fun drop() {
        if (!active) return
        active = false
        home.root.removeCallbacks(ticker)
        val it = item ?: return
        val dv = view
        val target = hoverItem
        val tc = hoverContainer
        if (folderReady && target != null && tc != null && it is AppItem) {
            (tc.viewFor(target) as? IconView)?.setFolderHover(false)
            container?.remove(it)
            val folderView = home.mergeIntoFolder(it, target, tc)
            container?.refresh(true)
            finish(dv, folderView, intoFolder = true)
        } else {
            val v = container?.viewFor(it)
            finish(dv, v, intoFolder = false)
        }
        hoverItem = null
        hoverContainer = null
        folderReady = false
        item = null
        container = null
        home.onDragEnded()
    }

    /** Fly the drag shadow to where the icon ended up, then reveal the real icon. */
    private fun finish(dv: DragView?, targetView: View?, intoFolder: Boolean) {
        val reveal = {
            draggingId = -1L
            dv?.let { home.root.removeView(it) }
            home.revealAll()
        }
        if (dv == null || targetView == null || !targetView.isAttachedToWindow) {
            draggingId = -1L
            dv?.let { home.root.removeView(it) }
            home.revealAll()
            return
        }
        val loc = IntArray(2)
        targetView.getLocationInWindow(loc)
        var tx = loc[0].toFloat()
        var ty = loc[1].toFloat()
        var scale = 1f
        if (targetView is IconView) {
            tx += targetView.iconLeft()
            ty += targetView.iconTop()
            if (intoFolder) {
                val s = targetView.iconSize
                tx += s * 0.35f; ty += s * 0.35f
                scale = 0.3f
            }
        }
        lift.cancel()
        // pivot is the finger point; convert the target's top-left into translation for that pivot
        val px = dv.pivotX
        val py = dv.pivotY
        val endTx = tx - px * (1f - scale)
        val endTy = ty - py * (1f - scale)
        dv.animate()
            .translationX(endTx).translationY(endTy)
            .scaleX(scale).scaleY(scale)
            .alpha(if (intoFolder) 0f else 1f)
            .setDuration(Ui.ms(260))
            .setInterpolator(Ui.easeOut)
            .withEndAction { reveal() }
            .start()
        if (!intoFolder) targetView.visibility = View.INVISIBLE
    }
}
