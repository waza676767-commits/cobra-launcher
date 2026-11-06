package dev.aura.launcher.home

import android.animation.TimeAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import dev.aura.launcher.core.AuraDialog
import dev.aura.launcher.core.Prefs
import dev.aura.launcher.core.Theme
import dev.aura.launcher.core.Ui
import dev.aura.launcher.core.dp
import dev.aura.launcher.core.dpi
import dev.aura.launcher.data.AppItem
import dev.aura.launcher.data.AppRepository
import dev.aura.launcher.data.FolderItem
import dev.aura.launcher.data.HomeLayout
import dev.aura.launcher.data.Item
import dev.aura.launcher.data.NotificationHub
import dev.aura.launcher.data.WeatherRepo
import dev.aura.launcher.data.WidgetItem
import dev.aura.launcher.gfx.Backdrop
import dev.aura.launcher.gfx.Glyph
import dev.aura.launcher.gfx.Glyphs
import dev.aura.launcher.gfx.GlassPainter
import dev.aura.launcher.gfx.IconFactory
import dev.aura.launcher.settings.SettingsActivity
import dev.aura.launcher.widgets.BatteryWidget
import dev.aura.launcher.widgets.ClockWidget
import dev.aura.launcher.widgets.CalendarWidget
import dev.aura.launcher.widgets.DeviceState
import dev.aura.launcher.widgets.GlassWidget
import dev.aura.launcher.widgets.HostedWidgetFrame
import dev.aura.launcher.widgets.WeatherWidget
import dev.aura.launcher.widgets.WidgetFactory
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

class PendingDrag(val view: View, val item: Item, val container: DropContainer, val x: Float, val y: Float)

/** The Aura home screen. */
class HomeActivity : Activity() {
    private var snapReceiver: android.content.BroadcastReceiver? = null
    val geo = Geometry()
    lateinit var layout: HomeLayout
        private set
    lateinit var root: HomeRoot
        private set
    lateinit var wallpaper: WallpaperView
        private set
    lateinit var content: HomeContent
        private set
    lateinit var workspace: Workspace
        private set
    lateinit var pill: PagePill
        private set
    lateinit var dock: DockView
        private set
    lateinit var editBar: EditBar
        private set
    lateinit var drag: DragController
        private set
    lateinit var folder: FolderOverlay
        private set
    lateinit var search: SearchOverlay
        private set
    private lateinit var popup: PopupMenu
    private lateinit var picker: WidgetPicker
    private var banner: SetupBanner? = null
    private lateinit var widgetHost: AppWidgetHost

    var editMode = false
        private set
    var pendingDrag: PendingDrag? = null
    private var insets: Insets = Insets.NONE
    private var badges: Map<String, Int> = emptyMap()
    private var dirty = false
    private var resumed = false
    private var wasStopped = false
    private var returning = false
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var jiggle: TimeAnimator? = null

    val searchPillOn get() = Prefs.searchPill

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.update(this)
        window.setDecorFitsSystemWindows(false)
        geo.loadPrefs()
        layout = HomeLayout.load(this) ?: DefaultLayout.build(this, geo.cols, geo.rows).also { saveNow(it) }
        syncAppsWithLayout()
        drag = DragController(this)
        widgetHost = AppWidgetHost(applicationContext, 0xA0A)

        root = HomeRoot(this)
        wallpaper = WallpaperView(this)
        content = HomeContent(this)
        workspace = Workspace(this)
        pill = PagePill(this)
        dock = DockView(this)
        editBar = EditBar(this)
        content.addView(workspace)
        content.addView(pill)
        content.addView(dock)
        content.addView(editBar)
        folder = FolderOverlay(this)
        search = SearchOverlay(this)
        popup = PopupMenu(this)
        picker = WidgetPicker(this)
        val mp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(wallpaper, mp)
        root.addView(content, FrameLayout.LayoutParams(mp))
        root.addView(folder, FrameLayout.LayoutParams(mp))
        root.addView(search, FrameLayout.LayoutParams(mp))
        root.addView(popup, FrameLayout.LayoutParams(mp))
        root.addView(picker, FrameLayout.LayoutParams(mp))
        root.setOnApplyWindowInsetsListener { _, ins ->
            // ignoring visibility: hiding the status bar in edit mode must not move the grid
            val i = ins.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            if (i != insets) {
                insets = i
                geo.invalidate()
                root.requestLayout()
            }
            ins
        }
        setContentView(root)

        bindAll()
        Backdrop.listen(onWallpaper)
        AppRepository.listen(onApps)
        NotificationHub.listen(onNotifications)
        WeatherRepo.listen(onWeather)
        Prefs.listen(onPref)
        Backdrop.ensure(this)
        WeatherRepo.init()
        badges = NotificationHub.badgeCounts()
        updateSystemBars()
        handleIntent(intent)
        snapReceiver = dev.aura.launcher.core.DebugSnap.attach(this, "home")
    }

    override fun onDestroy() {
        dev.aura.launcher.core.DebugSnap.detach(this, snapReceiver)
        Backdrop.unlisten(onWallpaper)
        AppRepository.unlisten(onApps)
        NotificationHub.unlisten(onNotifications)
        WeatherRepo.unlisten(onWeather)
        Prefs.unlisten(onPref)
        saveNow(layout)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        val wasDark = Theme.dark
        val wasReduced = Theme.reduceTransparency
        Theme.update(this)
        if (dirty || wasDark != Theme.dark || wasReduced != Theme.reduceTransparency) {
            dirty = false
            rebuildAll()
        }
        try { widgetHost.startListening() } catch (e: Exception) {}
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        registerReceiver(timeReceiver, f)
        if (layout.widgets().any { it.kind == "battery" }) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryRegistered = true
        }
        if (layout.widgets().any { it.kind == "weather" }) WeatherRepo.refreshIfStale(this)
        refreshBadges()
        refreshWidgets()
        updateBanner()
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
        try { widgetHost.stopListening() } catch (e: Exception) {}
        try { unregisterReceiver(timeReceiver) } catch (e: Exception) {}
        if (batteryRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (e: Exception) {}
            batteryRegistered = false
        }
        closeOverlays(animate = false)
        if (editMode) exitEditMode()
        saveNow(layout)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (returning && !Theme.reduceMotion) {
            content.scaleX = 1.06f; content.scaleY = 1.06f; content.alpha = 0.5f
            content.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(Ui.ms(380)).setInterpolator(Ui.easeOut).start()
        }
        returning = false
        wasStopped = false
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_ACTION) != null) {
            handleIntent(intent)
            return
        }
        if (resumed && !wasStopped) homePressedOnHome() else returning = true
    }

    private fun handleIntent(i: Intent?) {
        when (i?.getStringExtra(EXTRA_ACTION)) {
            "widgets" -> root.post { openWidgetPicker() }
            "edit" -> root.post { enterEditMode() }
        }
        i?.removeExtra(EXTRA_ACTION)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!closeOverlays(animate = true) && editMode) exitEditMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val was = Theme.dark
        Theme.update(this)
        if (was != Theme.dark) rebuildAll()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            HomeSwitch.REQ_ROLE -> {
                updateBanner()
                if (HomeSwitch.isDefault(this)) Toast.makeText(this, "Aura is now your Home Screen", Toast.LENGTH_SHORT).show()
            }
            REQ_BIND -> {
                val info = pendingWidgetInfo
                if (resultCode == RESULT_OK && info != null) configureOrPlace(pendingWidgetId, info)
                else if (pendingWidgetId != -1) widgetHost.deleteAppWidgetId(pendingWidgetId)
            }
            REQ_CONFIG -> {
                val info = pendingWidgetInfo
                if (resultCode == RESULT_OK && info != null) placeAndroidWidget(pendingWidgetId, info)
                else if (pendingWidgetId != -1) widgetHost.deleteAppWidgetId(pendingWidgetId)
            }
        }
    }

    // ------------------------------------------------------------------ building

    private fun bindAll() {
        workspace.bindPages(layout.pages)
        dock.bind(layout.dock)
        dock.visibility = if (geo.dockOn) View.VISIBLE else View.GONE
        pill.count = layout.pages.size
    }

    /** Settings changed: re-read everything and recreate the views. */
    fun rebuildAll() {
        Theme.update(this)
        geo.loadPrefs()
        geo.invalidate()
        moveDockOverflow()
        syncAppsWithLayout()
        workspace.reset()
        dock.reset()
        bindAll()
        search.themeChanged()
        invalidateTree(root)
        updateSystemBars()
        root.requestLayout()
        Backdrop.ensure(this)
    }

    private fun moveDockOverflow() {
        val keep = if (Prefs.dock) Prefs.dockCount.coerceIn(3, 6) else 0
        if (layout.dock.size > keep) {
            val extra = layout.dock.subList(keep, layout.dock.size).toList()
            layout.dock.removeAll(extra.toSet())
            if (layout.pages.isEmpty()) layout.pages.add(ArrayList())
            layout.pages.last().addAll(extra)
        }
    }

    /** Adds newly installed apps at the end, drops uninstalled / hidden ones. */
    private fun syncAppsWithLayout() {
        val hidden = Prefs.hidden
        val have = layout.appKeys()
        var changed = false
        for (k in have) if (AppRepository[k] == null || k in hidden) changed = layout.removeApp(k) || changed
        val now = layout.appKeys()
        val missing = AppRepository.visibleApps().filter { it.key !in now }.sortedBy { it.firstInstall }
        if (missing.isNotEmpty()) {
            if (layout.pages.isEmpty()) layout.pages.add(ArrayList())
            layout.pages.last().addAll(missing.map { AppItem(layout.newId(), it.key) })
            changed = true
        }
        layout.reflow(geo.cols, geo.rows, dropEmpty = true)
        if (changed && ::workspace.isInitialized) saveLayout()
    }

    fun updateGeometry(w: Int, h: Int) {
        if (!geo.compute(w, h, insets.top, insets.bottom, insets.left, insets.right)) return
        if (IconFactory.configure(geo.iconSize, Prefs.iconStyle, Prefs.tintColor)) {
            forEachItemView { (it as? Editable)?.refreshContent() }
            IconFactory.preload(layout.appKeys())
        }
        workspace.pages.forEach { it.requestLayout() }
    }

    fun createItemView(item: Item, c: DropContainer): View = when (item) {
        is AppItem -> IconView(this).apply { bindApp(item); showLabel = geo.showLabels }
        is FolderItem -> FolderIconView(this).apply { bindFolder(item); showLabel = geo.showLabels }
        is WidgetItem -> if (item.kind == "android") hostedView(item) else WidgetFactory.create(this, item.kind, item, item.sx, item.sy)
    }

    private fun hostedView(item: WidgetItem): View {
        val info = try { AppWidgetManager.getInstance(this).getAppWidgetInfo(item.appWidgetId) } catch (e: Exception) { null }
        val hv = if (info != null) try { widgetHost.createView(this, item.appWidgetId, info) } catch (e: Exception) { null } else null
        return HostedWidgetFrame(this, item, hv)
    }

    fun forEachItemView(block: (View) -> Unit) {
        workspace.pages.forEach { p -> p.allViews().forEach(block) }
        dock.allViews().forEach(block)
    }

    private fun invalidateTree(v: View) {
        v.invalidate()
        if (v is ViewGroup) for (i in 0 until v.childCount) invalidateTree(v.getChildAt(i))
    }

    // ------------------------------------------------------------------ saving

    private val saveRunnable = Runnable { saveNow(layout) }

    fun saveLayout() {
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, 400)
    }

    private fun saveNow(l: HomeLayout) {
        main.removeCallbacks(saveRunnable)
        val json = l.toJson()
        val f = HomeLayout.file(this)
        io.execute {
            try {
                val tmp = java.io.File(f.parentFile, f.name + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(f)
            } catch (e: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ listeners

    private val onWallpaper: () -> Unit = {
        invalidateTree(root)
        updateSystemBars()
    }

    private val onApps: () -> Unit = {
        val before = layout.toJson()
        syncAppsWithLayout()
        if (layout.toJson() != before) {
            workspace.bindPages(layout.pages)
            dock.sync(true)
            pill.count = layout.pages.size
        } else forEachItemView { (it as? Editable)?.refreshContent() }
    }

    private val onNotifications: () -> Unit = {
        main.removeCallbacks(badgeRunnable)
        main.postDelayed(badgeRunnable, 120)
    }
    private val badgeRunnable = Runnable { refreshBadges() }

    private val onWeather: () -> Unit = { refreshWidgets() }

    private val rebuildKeys = setOf(
        "appearance", "wallpaper", "iconStyle", "tintColor", "uiScale", "iconScale", "columns", "rows", "labels",
        "dock", "dockCount", "searchPill", "badges", "reduceMotion", "reduceTransparency", "autoLowPower", "animSpeed", "hidden"
    )

    private val onPref: (String) -> Unit = { key ->
        if (key in rebuildKeys) {
            if (resumed) main.post { rebuildAll() } else dirty = true
        }
        if (key == "wallpaper") Backdrop.ensure(this)
        if (key == "layoutReset") {
            geo.loadPrefs()
            layout = DefaultLayout.build(this, geo.cols, geo.rows)
            saveNow(layout)
            if (resumed) main.post { rebuildAll() } else dirty = true
        }
        if (key == "weatherUnit" || key == "weatherLat" || key == "weatherUseLocation") {
            if (key != "weatherUnit") Prefs.weatherTime = 0L
            refreshWidgets()
        }
    }

    fun badgeFor(pkg: String): Int = badges[pkg] ?: 0

    private fun refreshBadges() {
        badges = if (Prefs.badges == "off") emptyMap() else NotificationHub.badgeCounts()
        forEachItemView {
            if (it is FolderIconView) it.refreshContent()
            else if (it is IconView) { it.updateBadge(); it.invalidate() }
        }
    }

    private fun refreshWidgets() {
        forEachItemView { if (it is GlassWidget) it.invalidate() }
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            forEachItemView { if (it is ClockWidget || it is CalendarWidget) it.invalidate() }
        }
    }

    private var batteryRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val newLevel = if (level >= 0) level * 100 / scale.coerceAtLeast(1) else -1
            val ch = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (newLevel != DeviceState.battery || ch != DeviceState.charging) {
                DeviceState.battery = newLevel
                DeviceState.charging = ch
                forEachItemView { if (it is BatteryWidget) it.invalidate() }
            }
        }
    }

    // ------------------------------------------------------------------ system bars & blur

    fun updateSystemBars() {
        val darkIcons = !Theme.dark && Backdrop.topLuminance > 0.62f
        window.insetsController?.setSystemBarsAppearance(
            if (darkIcons) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }

    private var blurLevel = -1f

    /** Blurs + dims the home screen behind folders, search, menus (only while they're open). */
    fun setOverlayBlur(p: Float, strong: Boolean = false) {
        val v = p.coerceIn(0f, 1f)
        if (v == blurLevel && strong == blurStrong) return
        blurLevel = v
        blurStrong = strong
        wallpaper.blur = if (Theme.reduceTransparency) 0f else v
        if (v <= 0.002f || Theme.reduceTransparency) {
            content.setRenderEffect(null)
        } else {
            val r = (24.dp * v).coerceAtLeast(0.5f)
            content.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        }
        content.alpha = 1f - (if (Theme.reduceTransparency || strong) 0.85f else 0.3f) * v
    }

    private var blurStrong = false

    fun anyOverlayOpen() = folder.isOpen || search.visibility == View.VISIBLE || popup.isShowing || picker.isOpen

    /** Returns true if something was open. */
    private fun closeOverlays(animate: Boolean): Boolean {
        var any = false
        if (popup.isShowing) { popup.dismiss(!animate); any = true }
        if (picker.isOpen) { picker.close(); any = true }
        if (search.visibility == View.VISIBLE) { search.close(); any = true }
        if (folder.isOpen) { folder.close(); any = true }
        if (!animate) setOverlayBlur(0f)
        pendingDrag = null
        return any
    }

    private fun homePressedOnHome() {
        if (closeOverlays(animate = true)) return
        if (editMode) { exitEditMode(); return }
        if (workspace.current != 0) workspace.snapTo(0)
    }

    fun showKeyboard(v: View) {
        v.post {
            v.requestFocus()
            window.insetsController?.show(WindowInsets.Type.ime())
            getSystemService(InputMethodManager::class.java)?.showSoftInput(v, 0)
        }
    }

    fun hideKeyboard(v: View) {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    @SuppressLint("WrongConstant")
    fun expandNotifications() {
        try {
            val sbm = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        } catch (e: Throwable) {
            search.open()
        }
    }

    // ------------------------------------------------------------------ workspace callbacks

    fun onWorkspaceScrolled(pos: Float) {
        pill.position = pos
        if (workspace.touching || workspace.isSettling) pill.showDots()
        val first = floor(pos).toInt()
        for (i in first..first + 1) {
            workspace.pages.getOrNull(i)?.allViews()?.forEach {
                if (it is GlassWidget || it is FolderIconView || (IconFactory.isClear && it is IconView)) it.invalidate()
            }
        }
    }

    fun onPageChanging(page: Int) = pill.showDots()

    fun onPageSettled(page: Int) {
        pill.position = page.toFloat()
    }

    // ------------------------------------------------------------------ taps & menus

    fun launchApp(key: String, from: View?) {
        if (AppRepository.launch(this, key, from) && search.isOpen) root.postDelayed({ search.close() }, 300)
    }

    fun onItemClick(v: View) {
        when (val item = (v as? Editable)?.item) {
            is AppItem -> launchApp(item.key, v)
            is FolderItem -> folder.open(item, v)
            else -> {}
        }
    }

    fun onEmptyTap() {
        if (editMode) exitEditMode()
    }

    fun onEmptyLongPress() {
        if (anyOverlayOpen()) return
        Ui.thud(root)
        enterEditMode()
    }

    private fun toWindow(rawX: Float, rawY: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        return (rawX - loc[0]) to (rawY - loc[1])
    }

    fun onItemLongPress(v: View, rawX: Float, rawY: Float) {
        val item = (v as? Editable)?.item ?: return
        val c = v.parent as? DropContainer ?: return
        if (search.visibility == View.VISIBLE) return
        val (x, y) = toWindow(rawX, rawY)
        if (editMode) {
            drag.start(v, item, c, x, y)
            return
        }
        Ui.thud(v)
        pendingDrag = PendingDrag(v, item, c, x, y)
        showMenu(v, item, c)
    }

    fun startPendingDrag(x: Float, y: Float) {
        val pd = pendingDrag ?: return
        pendingDrag = null
        popup.dismiss(immediate = true)
        // grab the icon where the finger first pressed it, then catch up with the finger
        drag.start(pd.view, pd.item, pd.container, pd.x, pd.y)
        drag.move(x, y)
    }

    fun onPopupGone() {
        if (!folder.isOpen && search.visibility != View.VISIBLE) setOverlayBlur(0f)
    }

    private fun showMenu(v: View, item: Item, c: DropContainer) {
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        val anchor = RectF()
        val snap: Bitmap
        if (v is IconView) {
            anchor.set(loc[0] + v.iconLeft(), loc[1] + v.iconTop(), loc[0] + v.iconLeft() + v.iconSize, loc[1] + v.iconTop() + v.iconSize)
            snap = v.snapshotIcon()
        } else {
            anchor.set(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + v.width).toFloat(), (loc[1] + v.height).toFloat())
            snap = snapshot(v)
        }
        val list = ArrayList<MenuEntry>()
        when (item) {
            is AppItem -> {
                val key = item.key
                val dpi = resources.displayMetrics.densityDpi
                for (s in AppRepository.shortcuts(key)) {
                    list.add(MenuEntry(s.shortLabel?.toString() ?: "", icon = AppRepository.shortcutIcon(s, dpi), glyph = Glyph.SHORTCUT) { AppRepository.startShortcut(s) })
                }
                list.add(MenuEntry("Edit Home Screen", Glyph.GRID) { enterEditMode() })
                list.add(MenuEntry("App Info", Glyph.INFO) { AppRepository.openAppInfo(this, key) })
                if (c !is FolderPanel) list.add(MenuEntry("Hide from Home Screen", Glyph.EYE_SLASH) { hideApp(key) })
                val e = AppRepository[key]
                if (e != null && (!e.isSystem || e.isUpdatedSystem)) list.add(MenuEntry("Uninstall App", Glyph.TRASH, destructive = true) { confirmUninstall(key) })
            }
            is FolderItem -> {
                list.add(MenuEntry("Rename Folder", Glyph.PENCIL) { folder.open(item, v); root.postDelayed({ folder.editTitle() }, 350) })
                list.add(MenuEntry("Edit Home Screen", Glyph.GRID) { enterEditMode() })
                list.add(MenuEntry("Remove Folder", Glyph.TRASH, destructive = true) { ungroup(item, c) })
            }
            is WidgetItem -> {
                if (item.kind == "weather") list.add(MenuEntry("Edit Location", Glyph.LOCATION) { openSettings("weather") })
                if (item.kind == "android") {
                    val info = try { AppWidgetManager.getInstance(this).getAppWidgetInfo(item.appWidgetId) } catch (e: Exception) { null }
                    if (info?.configure != null) list.add(MenuEntry("Edit Widget", Glyph.PENCIL) { reconfigure(item) })
                }
                list.add(MenuEntry("Edit Home Screen", Glyph.GRID) { enterEditMode() })
                list.add(MenuEntry("Remove Widget", Glyph.MINUS, destructive = true) { removeItem(item, c) })
            }
        }
        popup.show(anchor, snap, list)
    }

    /** Picture of a view for menus / dragging (widgets from other apps may refuse; then a blank card). */
    fun snapshot(v: View): Bitmap {
        val b = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            v.draw(Canvas(b))
        } catch (e: Exception) {
            b.eraseColor(if (Theme.dark) 0x99303034.toInt() else 0x99F2F2F7.toInt())
        }
        return b
    }

    fun onRemoveTapped(v: View) {
        val item = (v as? Editable)?.item ?: return
        val c = v.parent as? DropContainer ?: return
        when (item) {
            is AppItem -> {
                val e = AppRepository[item.key]
                val name = e?.label ?: "app"
                val actions = ArrayList<AuraDialog.Action>()
                if (e != null && (!e.isSystem || e.isUpdatedSystem)) actions.add(AuraDialog.Action("Uninstall App", AuraDialog.Style.DESTRUCTIVE) { AppRepository.uninstall(this, item.key) })
                actions.add(AuraDialog.Action("Hide from Home Screen") { hideApp(item.key) })
                actions.add(AuraDialog.Action("Cancel", AuraDialog.Style.BOLD))
                AuraDialog.show(this, "Remove “$name”?", "Hidden apps stay installed. Find them with Search or in Aura Settings › Hidden Apps.", actions)
            }
            is FolderItem -> AuraDialog.show(this, "Remove “${item.name}”?", "The apps inside go back onto the Home Screen.", listOf(
                AuraDialog.Action("Cancel"),
                AuraDialog.Action("Remove", AuraDialog.Style.DESTRUCTIVE) { ungroup(item, c) }
            ))
            is WidgetItem -> AuraDialog.show(this, "Remove widget?", null, listOf(
                AuraDialog.Action("Cancel"),
                AuraDialog.Action("Remove", AuraDialog.Style.DESTRUCTIVE) { removeItem(item, c) }
            ))
        }
    }

    private fun confirmUninstall(key: String) {
        val name = AppRepository[key]?.label ?: "this app"
        AuraDialog.show(this, "Uninstall “$name”?", "Uninstalling also deletes its data.", listOf(
            AuraDialog.Action("Cancel"),
            AuraDialog.Action("Uninstall", AuraDialog.Style.DESTRUCTIVE) { AppRepository.uninstall(this, key) }
        ))
    }

    private fun hideApp(key: String) {
        Prefs.hidden = Prefs.hidden + key
        layout.removeApp(key)
        afterLayoutEdit()
        Toast.makeText(this, "Hidden. Find it with Search or in Settings › Hidden Apps", Toast.LENGTH_LONG).show()
    }

    private fun removeItem(item: Item, c: DropContainer) {
        c.remove(item)
        if (item is WidgetItem && item.appWidgetId != -1) try { widgetHost.deleteAppWidgetId(item.appWidgetId) } catch (e: Exception) {}
        afterLayoutEdit()
    }

    private fun ungroup(f: FolderItem, c: DropContainer) {
        if (folder.isOpen) folder.close()
        val idx = c.indexOf(f)
        c.remove(f)
        if (c is DockView) {
            // the dock can't take them all; put them on the current page instead
            val page = layout.pages.getOrNull(workspace.current) ?: layout.pages.last()
            page.addAll(f.apps)
        } else f.apps.reversed().forEach { c.insert(it, idx) }
        afterLayoutEdit()
    }

    private fun afterLayoutEdit() {
        cleanupFolders()
        layout.reflow(geo.cols, geo.rows, dropEmpty = true)
        workspace.bindPages(layout.pages)
        workspace.pages.forEach { it.sync(true) }
        dock.sync(true)
        pill.count = layout.pages.size
        saveLayout()
    }

    // ------------------------------------------------------------------ edit mode

    fun enterEditMode() {
        if (editMode) return
        editMode = true
        popup.dismiss()
        forEachItemView { (it as? Editable)?.editMode = true }
        editBar.visibility = View.VISIBLE
        editBar.animate().alpha(1f).setDuration(Ui.ms(200)).start()
        window.insetsController?.let {
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsets.Type.statusBars())
        }
        if (!Theme.reduceMotion) startJiggle()
    }

    fun exitEditMode() {
        if (!editMode) return
        editMode = false
        jiggle?.cancel()
        jiggle = null
        forEachItemView { it.rotation = 0f; (it as? Editable)?.editMode = false }
        editBar.animate().alpha(0f).setDuration(Ui.ms(180)).withEndAction { if (!editMode) editBar.visibility = View.GONE }.start()
        window.insetsController?.show(WindowInsets.Type.statusBars())
        afterLayoutEdit()
    }

    private fun startJiggle() {
        jiggle?.cancel()
        val t = TimeAnimator()
        t.setTimeListener { _, total, _ ->
            val s = total / 1000.0
            val cur = workspace.current
            var i = 0
            fun wiggle(v: View, amp: Float) {
                i++
                if (v.visibility != View.VISIBLE) return
                v.rotation = (sin(s * 2 * Math.PI * 3.6 + i * 1.7) * amp).toFloat()
            }
            for (p in cur - 1..cur + 1) workspace.pages.getOrNull(p)?.allViews()?.forEach { wiggle(it, if (it is IconView) 1.6f else 0.6f) }
            dock.allViews().forEach { wiggle(it, 1.6f) }
        }
        t.start()
        jiggle = t
    }

    // ------------------------------------------------------------------ drag & drop support

    fun dropTargetAt(x: Float, y: Float, item: Item): DropContainer? {
        if (folder.isOpen) {
            if (folder.panelContains(x, y, 24.dp)) return folder.panel
            folder.close()
            return null
        }
        if (geo.dockOn && dock.visibility == View.VISIBLE && y >= geo.dockTop - 12.dp && item !is WidgetItem) return dock
        return workspace.currentPage
    }

    fun reflowDuringDrag() {
        val n = layout.pages.size
        layout.reflow(geo.cols, geo.rows, dropEmpty = false)
        if (layout.pages.size != n) workspace.bindPages(layout.pages)
        workspace.pages.forEach { it.sync(true) }
        pill.count = layout.pages.size
    }

    fun flipPageWhileDragging(dir: Int) {
        val next = workspace.current + dir
        if (next < 0) return
        if (next >= layout.pages.size) {
            val cur = layout.pages.getOrNull(workspace.current) ?: return
            if (cur.size <= 1) return
            layout.pages.add(ArrayList())
            workspace.bindPages(layout.pages)
            pill.count = layout.pages.size
        }
        workspace.snapTo(next)
        Ui.tick(root)
    }

    fun mergeIntoFolder(app: AppItem, target: Item, c: DropContainer): View? {
        return when (target) {
            is FolderItem -> {
                target.apps.add(app)
                c.refresh(true)
                c.viewFor(target)
            }
            is AppItem -> {
                val idx = c.indexOf(target)
                val f = FolderItem(layout.newId(), DefaultLayout.folderName(target.key, app.key), mutableListOf(target, app))
                c.remove(target)
                c.insert(f, idx)
                c.refresh(true)
                c.viewFor(f)
            }
            else -> null
        }
    }

    private fun cleanupFolders() {
        val open = folder.folder
        fun clean(list: MutableList<Item>) {
            val it = list.listIterator()
            while (it.hasNext()) {
                val f = it.next() as? FolderItem ?: continue
                if (f === open) continue
                when (f.apps.size) {
                    0 -> it.remove()
                    1 -> it.set(f.apps[0])
                }
            }
        }
        layout.pages.forEach { clean(it) }
        clean(layout.dock)
    }

    fun onDragEnded() {
        cleanupFolders()
        layout.reflow(geo.cols, geo.rows, dropEmpty = true)
        workspace.bindPages(layout.pages)
        if (workspace.current >= layout.pages.size) workspace.snapTo(layout.pages.size - 1)
        dock.sync(true)
        if (folder.isOpen) folder.panel.sync(true)
        pill.count = layout.pages.size
        saveLayout()
    }

    fun onFolderClosed(f: FolderItem?) {
        if (f != null && f.apps.size <= 1) {
            cleanupFolders()
            afterLayoutEdit()
        } else {
            forEachItemView { if (it is FolderIconView && it.item === f) it.refreshContent() }
        }
        if (!popup.isShowing && search.visibility != View.VISIBLE) setOverlayBlur(0f)
    }

    fun revealAll() {
        forEachItemView { if (it.visibility == View.INVISIBLE && (it as? Editable)?.item?.id != drag.draggingId) it.visibility = View.VISIBLE }
        if (folder.isOpen) folder.panel.sync(false)
    }

    // ------------------------------------------------------------------ widgets

    fun openWidgetPicker() {
        popup.dismiss(true)
        picker.open()
    }

    fun openSettings(section: String?) {
        val i = Intent(this, SettingsActivity::class.java)
        if (section != null) i.putExtra(SettingsActivity.EXTRA_SECTION, section)
        startActivity(i)
    }

    private fun insertOnCurrentPage(item: Item) {
        if (layout.pages.isEmpty()) layout.pages.add(ArrayList())
        val page = layout.pages[workspace.current.coerceIn(0, layout.pages.size - 1)]
        page.add(0, item)
        layout.reflow(geo.cols, geo.rows, dropEmpty = true)
        workspace.bindPages(layout.pages)
        pill.count = layout.pages.size
        val idx = layout.pages.indexOfFirst { it.contains(item) }
        if (idx >= 0 && idx != workspace.current) workspace.snapTo(idx)
        saveLayout()
    }

    fun addWidget(kind: String, sx: Int, sy: Int) {
        insertOnCurrentPage(WidgetItem(layout.newId(), kind, sx, sy))
        if (kind == "weather") {
            if (!WeatherRepo.hasPlace) openSettings("weather") else WeatherRepo.refreshIfStale(this)
        }
        if (kind == "battery" && !batteryRegistered) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryRegistered = true
        }
    }

    private var pendingWidgetId = -1
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null

    fun addAndroidWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        pendingWidgetInfo = info
        val ok = try { AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) } catch (e: Exception) { false }
        if (ok) configureOrPlace(id, info) else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                widgetHost.deleteAppWidgetId(id)
            }
        }
    }

    private fun configureOrPlace(id: Int, info: AppWidgetProviderInfo) {
        val optional = (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info.configure != null && !optional) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
                return
            } catch (e: Exception) {
            }
        }
        placeAndroidWidget(id, info)
    }

    private fun placeAndroidWidget(id: Int, info: AppWidgetProviderInfo) {
        val (sx, sy) = spanFor(info)
        insertOnCurrentPage(WidgetItem(layout.newId(), "android", sx, sy, id, info.provider.flattenToString()))
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    private fun reconfigure(item: WidgetItem) {
        try {
            widgetHost.startAppWidgetConfigureActivityForResult(this, item.appWidgetId, 0, REQ_RECONFIG, null)
        } catch (e: Exception) {
        }
    }

    fun spanFor(info: AppWidgetProviderInfo): Pair<Int, Int> {
        var sx: Int
        var sy: Int
        if (info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            sx = info.targetCellWidth; sy = info.targetCellHeight
        } else {
            val cw = geo.cellW.coerceAtLeast(1f)
            val ch = geo.cellH.coerceAtLeast(1f)
            sx = ceil(info.minWidth / cw).toInt()
            sy = ceil(info.minHeight / ch).toInt()
        }
        sx = sx.coerceIn(1, geo.cols)
        sy = sy.coerceIn(1, geo.rows)
        if (sx == 3 && geo.cols == 4) sx = 4
        return sx to sy
    }

    // ------------------------------------------------------------------ setup banner

    private fun updateBanner() {
        val show = !HomeSwitch.isDefault(this) && !Prefs.setupBannerHidden
        if (show && banner == null) {
            val b = SetupBanner(this)
            banner = b
            root.addView(b, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 76.dpi))
            b.translationY = -120.dp
            root.post { b.translationY = (geo.insetTop + 8.dp) - 120.dp; b.animate().translationY(geo.insetTop + 8.dp).setDuration(Ui.ms(420)).setInterpolator(Ui.easeOut).start() }
        } else if (!show && banner != null) {
            root.removeView(banner)
            banner = null
        }
    }

    fun dismissBanner(forever: Boolean) {
        if (forever) Prefs.setupBannerHidden = true
        banner?.let { b -> b.animate().alpha(0f).setDuration(Ui.ms(200)).withEndAction { root.removeView(b) }.start() }
        banner = null
    }

    /** "Make Aura your Home Screen" card, shown while One UI Home is still the default. */
    @SuppressLint("ViewConstructor")
    class SetupBanner(private val home: HomeActivity) : View(home), HasGlass {
        private val glass = GlassPainter()
        private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val btn = RectF()
        private val close = RectF()

        override fun onDraw(c: Canvas) {
            val m = 12.dp
            val l = m; val r = width - m; val t = 0f; val b = height.toFloat()
            glass.drawRoundRect(c, this, l, t, r, b, 24.dp)
            Glyphs.draw(c, Glyph.HOME, l + 30.dp, b / 2f, 22.dp, Theme.text, 2.2f)
            tp.color = Theme.text
            tp.textSize = 15.dp
            tp.typeface = Ui.font(600)
            c.drawText("Make Aura your Home Screen", l + 54.dp, b / 2f - 3.dp, tp)
            tp.color = Theme.text2
            tp.textSize = 12.5f.dp
            tp.typeface = Ui.font(400)
            c.drawText("Switch back anytime in Aura Settings", l + 54.dp, b / 2f + 15.dp, tp)
            btn.set(r - 104.dp, b / 2f - 17.dp, r - 40.dp, b / 2f + 17.dp)
            tp.color = Theme.accent
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.accent }
            c.drawRoundRect(btn, 17.dp, 17.dp, p)
            tp.color = android.graphics.Color.WHITE
            tp.typeface = Ui.font(600)
            tp.textSize = 15.dp
            tp.textAlign = Paint.Align.CENTER
            c.drawText("Set", btn.centerX(), btn.centerY() + 5.dp, tp)
            tp.textAlign = Paint.Align.LEFT
            close.set(r - 36.dp, b / 2f - 16.dp, r - 4.dp, b / 2f + 16.dp)
            Glyphs.draw(c, Glyph.CLOSE, close.centerX(), close.centerY(), 14.dp, Theme.text2, 2.4f)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_UP) {
                when {
                    close.contains(ev.x, ev.y) -> home.dismissBanner(true)
                    else -> HomeSwitch.makeDefault(home)
                }
            }
            return true
        }
    }

    companion object {
        const val EXTRA_ACTION = "aura.action"
        private const val REQ_BIND = 51
        private const val REQ_CONFIG = 52
        private const val REQ_RECONFIG = 53
    }
}
