package dev.mango.os.lock

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextPaint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.mango.os.core.Prefs
import dev.mango.os.core.Spring
import dev.mango.os.core.Theme
import dev.mango.os.core.Ui
import dev.mango.os.core.dp
import dev.mango.os.core.dpi
import dev.mango.os.data.NotifEntry
import dev.mango.os.data.NotificationHub
import dev.mango.os.data.WeatherRepo
import dev.mango.os.gfx.Backdrop
import dev.mango.os.gfx.Glyph
import dev.mango.os.gfx.Glyphs
import dev.mango.os.gfx.IconFactory
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mango's lock screen. Shown over One UI's keyguard; swiping up asks the system to unlock
 * (PIN / pattern / fingerprint as usual), so security stays exactly as before.
 */
class LockActivity : Activity() {
    private var snapReceiver: android.content.BroadcastReceiver? = null
    private lateinit var root: LockRoot
    private lateinit var clock: ClockView
    private lateinit var widgets: LockWidgets
    private lateinit var media: MediaCard
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var torch: RoundButton
    private lateinit var camera: RoundButton
    private lateinit var content: ViewGroup
    private var preview = false
    private val expanded = HashSet<String>()
    private var insets = android.graphics.Insets.NONE
    private var torchId: String? = null
    private var torchOn = false
    private var afterUnlock: (() -> Unit)? = null
    private val km by lazy { getSystemService(KeyguardManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        window.setDecorFitsSystemWindows(false)
        Theme.update(this)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        if (IconFactory.size == 0) IconFactory.configure(60.dpi, Prefs.iconStyle, Prefs.tintColor)

        root = LockRoot(this)
        content = object : ViewGroup(this) {
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
            override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
        }
        clock = ClockView(this)
        widgets = LockWidgets(this)
        media = MediaCard(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.BOTTOM }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(28.dpi)
        }
        torch = RoundButton(this, Glyph.FLASHLIGHT).apply { setOnClickListener { toggleTorch() } }
        camera = RoundButton(this, Glyph.CAMERA).apply { setOnClickListener { openCamera() } }

        content.addView(clock)
        content.addView(widgets)
        content.addView(media)
        content.addView(scroll)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.addView(root.glyphView)
        root.addView(torch)
        root.addView(camera)
        root.addView(Hint(this))
        root.setOnApplyWindowInsetsListener { _, ins ->
            insets = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            root.requestLayout()
            ins
        }
        setContentView(root)
        window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        findTorch()
        Backdrop.ensure(this)
        snapReceiver = dev.mango.os.core.DebugSnap.attach(this, "lock")
    }

    override fun onStart() {
        super.onStart()
        NotificationHub.listen(onNotifs)
        NotificationHub.listenMedia(onMedia)
        Backdrop.listen(onWall)
        WeatherRepo.listen(onWall)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(receiver, f)
        if (Build.VERSION.SDK_INT >= 33) {
            try { km.addKeyguardLockedStateListener(mainExecutor, lockedListener) } catch (e: Exception) {}
        }
        rebuildList(animateKey = null)
        onMedia()
    }

    /** One UI unlocked underneath us (fingerprint / face while this screen was showing). */
    private fun unlockedBySystem() {
        root.lockGlyphChanged()
        if (Prefs.lockFastUnlock && !preview && isResumedNow && !km.isKeyguardLocked) root.unlock()
    }

    private val lockedListener = KeyguardManager.KeyguardLockedStateListener { locked -> if (!locked) unlockedBySystem() }
    private var isResumedNow = false

    override fun onStop() {
        super.onStop()
        NotificationHub.unlisten(onNotifs)
        NotificationHub.unlistenMedia(onMedia)
        Backdrop.unlisten(onWall)
        WeatherRepo.unlisten(onWall)
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= 33) {
            try { km.removeKeyguardLockedStateListener(lockedListener) } catch (e: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        isResumedNow = false
        dev.mango.os.core.FrameBoost.detach(this)
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        dev.mango.os.core.FrameBoost.attach(this)
        root.reset()
        clock.invalidate()
        widgets.invalidate()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        expanded.clear()
        root.reset()
        rebuildList(null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (preview) finishUnlocked()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // pressed Home while the phone wasn't actually locked: just get out of the way
        if (preview || !km.isKeyguardLocked) finishUnlocked()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            clock.invalidate()
            widgets.invalidate()
            if (i.action == Intent.ACTION_USER_PRESENT) unlockedBySystem() else root.lockGlyphChanged()
        }
    }

    private val onWall: () -> Unit = { root.invalidateAll() }
    private val onMedia: () -> Unit = {
        media.visibility = if (media.hasMedia) View.VISIBLE else View.GONE
        media.invalidate()
        root.requestLayout()
    }
    private val onNotifs: () -> Unit = { rebuildList(NotificationHub.takeLastPosted()) }

    // ------------------------------------------------------------------ notifications

    private fun hidePreviews(): Boolean = when (Prefs.lockPreviews) {
        "never" -> true
        "unlocked" -> km.isKeyguardLocked && km.isKeyguardSecure
        else -> false
    }

    private fun rebuildList(animateKey: String?) {
        list.removeAllViews()
        if (!Prefs.lockNotifs) return
        val all = NotificationHub.list()
        val groups = LinkedHashMap<String, MutableList<NotifEntry>>()
        for (e in all) groups.getOrPut(e.pkg) { ArrayList() }.add(e)
        val hide = hidePreviews()
        val cards = ArrayList<View>()
        // newest at the bottom, closest to your thumb (like iOS)
        for ((pkg, entries) in groups.entries.reversed()) {
            if (entries.size > 1 && pkg !in expanded) {
                cards.add(card(entries[0], entries.size, hide) {
                    expanded.add(pkg); rebuildList(null)
                }.also { c -> c.onDismiss = { NotificationHub.dismissAll(entries.filter { it.clearable }.map { it.key }) } })
            } else {
                for (e in entries.reversed()) cards.add(card(e, 1, hide) { open(e) }.also { c -> c.onDismiss = { if (e.clearable) NotificationHub.dismiss(e.key) else rebuildList(null) } })
            }
        }
        for (c in cards) {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 8.dpi
            list.addView(c, lp)
        }
        if (animateKey != null) {
            val last = cards.lastOrNull() as? NotifCard
            if (last?.entry?.key == animateKey && !Theme.reduceMotion) {
                last.translationY = 80.dp
                last.alpha = 0f
                last.animate().translationY(0f).alpha(1f).setDuration(Ui.ms(420)).setInterpolator(Ui.easeOut).start()
            }
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun card(e: NotifEntry, count: Int, hide: Boolean, tap: () -> Unit) = NotifCard(this, this).apply {
        bind(e, count, hide)
        onTap = tap
    }

    private fun open(e: NotifEntry) {
        runAfterUnlock { NotificationHub.open(this, e) }
    }

    // ------------------------------------------------------------------ unlock

    fun runAfterUnlock(action: () -> Unit) {
        afterUnlock = action
        root.unlock()
    }

    /** Called when the swipe-up passes the threshold. */
    fun requestUnlock() {
        if (preview || !km.isKeyguardLocked) {
            finishUnlocked()
            return
        }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finishUnlocked()
            override fun onDismissCancelled() {
                afterUnlock = null
                root.restore()
            }
            override fun onDismissError() {
                if (!km.isKeyguardLocked) finishUnlocked() else { afterUnlock = null; root.restore() }
            }
        })
    }

    private fun finishUnlocked() {
        val a = afterUnlock
        afterUnlock = null
        a?.invoke()
        if (torchOn) setTorch(false)
        finish()
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        else @Suppress("DEPRECATION") overridePendingTransition(0, android.R.anim.fade_out)
    }

    // ------------------------------------------------------------------ flashlight & camera

    private fun findTorch() {
        val cm = getSystemService(CameraManager::class.java) ?: return
        try {
            torchId = cm.cameraIdList.firstOrNull { id ->
                val ch = cm.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) { torchOn = enabled; torch.on = enabled }
                }
            }, null)
        } catch (e: Exception) {
        }
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        try { getSystemService(CameraManager::class.java)?.setTorchMode(id, on) } catch (e: Exception) {}
    }

    private fun toggleTorch() = setTorch(!torchOn)

    private fun openCamera() {
        val secure = km.isKeyguardLocked
        val i = Intent(if (secure) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (e: Exception) {}
    }

    // ------------------------------------------------------------------ views

    /** Wallpaper with a soft dark fade at the top and bottom for legibility. */
    class LockWallpaper(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.FILTER_BITMAP_FLAG)
        private val shade = Paint()
        private val dst = Rect()
        override fun onDraw(c: Canvas) {
            dst.set(0, 0, width, height)
            val b = Backdrop.full
            if (b == null) c.drawColor(Color.BLACK) else c.drawBitmap(b, null, dst, p)
            shade.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), intArrayOf(0x40000000, 0x00000000, 0x00000000, 0x59000000), floatArrayOf(0f, 0.3f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        }
    }

    private inner class Hint(ctx: Context) : View(ctx) {
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xB3FFFFFF.toInt() }
        override fun onDraw(c: Canvas) {
            tp.textSize = 14.dp
            tp.typeface = Ui.font(500)
            val secureLocked = !preview && km.isKeyguardLocked && km.isKeyguardSecure
            c.drawText(if (secureLocked && Prefs.lockFastUnlock) "Use fingerprint or swipe up" else "Swipe up to open", width / 2f, height / 2f + 5.dp, tp)
        }
    }

    /** Lays everything out and turns an upward swipe into an unlock. */
    @SuppressLint("ViewConstructor")
    inner class LockRoot(ctx: Context) : FrameLayout(ctx) {
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var tracker: VelocityTracker? = null
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private val spring = Spring(420f, 0.85f, 0.5f)
        private var offset = 0f
        private var unlocking = false
        private var lockOpen = false
        val wall = LockWallpaper(ctx)
        val glyphView = object : View(ctx) {
            override fun onDraw(c: Canvas) = Glyphs.draw(c, if (lockOpen) Glyph.LOCK_OPEN else Glyph.LOCK, width / 2f, height / 2f, 20.dp, Color.WHITE, 2.2f)
        }

        init {
            addView(wall, LayoutParams(-1, -1))
            spring.onUpdate = { v -> setOffset(v) }
            spring.onEnd = { if (unlocking) requestUnlock() }
        }

        fun reset() {
            spring.cancel()
            unlocking = false
            setOffset(0f)
            lockGlyphChanged()
        }

        fun restore() {
            unlocking = false
            spring.animateTo(0f)
        }

        fun unlock() {
            if (unlocking) return
            unlocking = true
            spring.animateTo(-height * 0.45f, -3000.dp)
        }

        fun lockGlyphChanged() {
            val open = !km.isKeyguardLocked || preview
            if (open && !lockOpen && !Theme.reduceMotion) {
                // little "unlocked" pop, like Face ID
                glyphView.animate().scaleX(1.25f).scaleY(1.25f).setDuration(Ui.ms(140)).withEndAction {
                    glyphView.animate().scaleX(1f).scaleY(1f).setDuration(Ui.ms(220)).setInterpolator(Ui.easeOut).start()
                }.start()
            }
            lockOpen = open
            glyphView.invalidate()
            for (i in 0 until childCount) { val ch = getChildAt(i); if (ch is Hint) ch.invalidate() }
        }

        fun invalidateAll() {
            for (i in 0 until childCount) getChildAt(i).invalidate()
            clock.invalidate(); widgets.invalidate(); media.invalidate()
            for (i in 0 until list.childCount) list.getChildAt(i).invalidate()
        }

        private fun setOffset(v: Float) {
            offset = v
            val k = if (height <= 0) 0f else (-v / (height * 0.45f)).coerceIn(0f, 1f)
            content.translationY = v
            content.alpha = 1f - k
            glyphView.translationY = v
            glyphView.alpha = 1f - k
            torch.alpha = 1f - k
            camera.alpha = 1f - k
            for (i in 0 until childCount) { val ch = getChildAt(i); if (ch is Hint) { ch.alpha = 1f - k; ch.translationY = v * 0.5f } }
            val s = 1f + 0.05f * k
            wall.scaleX = s
            wall.scaleY = s
        }

        private val rClock = Rect()
        private val rWidgets = Rect()
        private val rMedia = Rect()
        private val rList = Rect()
        private val rTorch = Rect()
        private val rCamera = Rect()
        private val rHint = Rect()

        private fun compute(w: Int, h: Int) {
            clock.availW = w
            val top = insets.top
            val ch = clock.preferredHeight()
            val clockTop = top + 44.dpi
            rClock.set(0, clockTop, w, clockTop + ch)
            var y = clockTop + ch + 4.dpi
            val wh = if (Prefs.lockWidgets) 70.dpi else 0
            rWidgets.set(0, y, w, y + wh)
            y += wh + 18.dpi
            val btn = 52.dpi
            val bottomPad = insets.bottom.coerceAtLeast(14.dpi) + 20.dpi
            val by = h - bottomPad - btn
            val side = 40.dpi
            rTorch.set(side, by, side + btn, by + btn)
            rCamera.set(w - side - btn, by, w - side, by + btn)
            rHint.set(side + btn, by, w - side - btn, by + btn)
            val m = 12.dpi
            var listBottom = by - 14.dpi
            if (media.visibility == View.VISIBLE) {
                val mh = 86.dpi
                rMedia.set(m, listBottom - mh, w - m, listBottom)
                listBottom -= mh + 10.dpi
            } else rMedia.setEmpty()
            rList.set(m, y, w - m, listBottom.coerceAtLeast(y))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(w, h)
            compute(w, h)
            fun ex(v: Int) = MeasureSpec.makeMeasureSpec(v.coerceAtLeast(0), MeasureSpec.EXACTLY)
            wall.measure(ex(w), ex(h))
            content.measure(ex(w), ex(h))
            glyphView.measure(ex(w), ex(28.dpi))
            clock.measure(ex(rClock.width()), ex(rClock.height()))
            widgets.measure(ex(rWidgets.width()), ex(rWidgets.height()))
            media.measure(ex(rMedia.width()), ex(rMedia.height()))
            scroll.measure(ex(rList.width()), ex(rList.height()))
            torch.measure(ex(rTorch.width()), ex(rTorch.height()))
            camera.measure(ex(rCamera.width()), ex(rCamera.height()))
            for (i in 0 until childCount) { val c = getChildAt(i); if (c is Hint) c.measure(ex(rHint.width()), ex(rHint.height())) }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = r - l
            val h = b - t
            compute(w, h)
            wall.layout(0, 0, w, h)
            content.layout(0, 0, w, h)
            val top = insets.top
            glyphView.layout(0, top + 6.dpi, w, top + 34.dpi)
            fun place(v: View, rc: Rect) = v.layout(rc.left, rc.top, rc.right, rc.bottom)
            place(clock, rClock)
            widgets.visibility = if (Prefs.lockWidgets) View.VISIBLE else View.GONE
            place(widgets, rWidgets)
            place(media, rMedia)
            place(scroll, rList)
            val showButtons = Prefs.lockShortcuts
            torch.visibility = if (showButtons && torchId != null) View.VISIBLE else View.GONE
            camera.visibility = if (showButtons) View.VISIBLE else View.GONE
            place(torch, rTorch)
            place(camera, rCamera)
            for (i in 0 until childCount) { val c = getChildAt(i); if (c is Hint) place(c, rHint) }
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            dev.mango.os.core.FrameBoost.ping()
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) { tracker?.recycle(); tracker = VelocityTracker.obtain() }
            tracker?.addMovement(ev)
            return super.dispatchTouchEvent(ev)
        }

        private fun inList(x: Float, y: Float): Boolean {
            val r = Rect()
            scroll.getHitRect(r)
            return r.contains(x.roundToInt(), y.roundToInt()) && (scroll.canScrollVertically(1) || scroll.canScrollVertically(-1))
        }

        private var startedInList = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y; dragging = false
                    startedInList = inList(ev.x, ev.y)
                    if (unlocking) return true
                    spring.cancel()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.y - downY
                    if (!startedInList && -dy > slop && abs(dy) > abs(ev.x - downX)) { dragging = true; return true }
                    if (startedInList && -dy > slop && !scroll.canScrollVertically(1) && abs(dy) > abs(ev.x - downX)) { dragging = true; return true }
                }
            }
            return dragging
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = ev.y; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.y - downY
                    if (!dragging && -dy > slop) dragging = true
                    if (dragging) setOffset(if (dy < 0) dy else dy * 0.2f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.computeCurrentVelocity(1000)
                    val vy = tracker?.yVelocity ?: 0f
                    if (dragging) {
                        spring.setValue(offset)
                        if (offset < -height * 0.16f || vy < -900.dp) {
                            unlocking = true
                            spring.animateTo(-height * 0.45f, vy)
                        } else spring.animateTo(0f, vy)
                    }
                    dragging = false
                }
            }
            return true
        }
    }

    override fun onDestroy() {
        dev.mango.os.core.DebugSnap.detach(this, snapReceiver)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
    }
}
