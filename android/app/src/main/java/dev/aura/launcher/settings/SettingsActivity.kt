package dev.aura.launcher.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.aura.launcher.BuildConfig
import dev.aura.launcher.core.AuraDialog
import dev.aura.launcher.core.Prefs
import dev.aura.launcher.core.Theme
import dev.aura.launcher.core.Ui
import dev.aura.launcher.core.dp
import dev.aura.launcher.core.dpi
import dev.aura.launcher.data.AppRepository
import dev.aura.launcher.data.NotificationHub
import dev.aura.launcher.data.WeatherRepo
import dev.aura.launcher.gfx.Backdrop
import dev.aura.launcher.gfx.Glyph
import dev.aura.launcher.gfx.IconFactory
import dev.aura.launcher.gfx.WallpaperArt
import dev.aura.launcher.home.HomeActivity
import dev.aura.launcher.home.HomeSwitch
import dev.aura.launcher.lock.LockController
import kotlin.math.roundToInt

/** Aura Settings: grouped iOS-style lists over a frosted copy of the wallpaper. */
class SettingsActivity : Activity() {
    private var snapReceiver: android.content.BroadcastReceiver? = null
    private lateinit var root: FrameLayout
    private val stack = ArrayList<ScreenView>()
    private var insets = android.graphics.Insets.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.update(this)
        window.setDecorFitsSystemWindows(false)
        root = object : FrameLayout(this) {
            private val p = Paint(Paint.FILTER_BITMAP_FLAG)
            private val dst = Rect()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                val b = Backdrop.blurred
                dst.set(0, 0, width, height)
                if (b != null && !Theme.reduceTransparency) c.drawBitmap(b, null, dst, p) else c.drawColor(if (Theme.dark) Color.BLACK else 0xFFF2F2F7.toInt())
                c.drawColor(if (Theme.dark) 0xB8000000.toInt() else 0xC7F2F2F7.toInt())
            }
        }
        root.setOnApplyWindowInsetsListener { _, ins ->
            insets = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            stack.forEach { it.applyInsets() }
            ins
        }
        setContentView(root)
        window.insetsController?.setSystemBarsAppearance(
            if (Theme.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
        Backdrop.ensure(this)
        Backdrop.listen(onWall)
        push("main", animate = false)
        intent.getStringExtra(EXTRA_SECTION)?.let { push(it, animate = false) }
        snapReceiver = dev.aura.launcher.core.DebugSnap.attach(this, "settings")
    }

    override fun onDestroy() {
        dev.aura.launcher.core.DebugSnap.detach(this, snapReceiver)
        Backdrop.unlisten(onWall)
        super.onDestroy()
    }

    private val onWall: () -> Unit = { root.invalidate() }

    override fun onResume() {
        super.onResume()
        stack.lastOrNull()?.rebuild()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) pop() else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ navigation

    private fun push(id: String, animate: Boolean = true) {
        val prev = stack.lastOrNull()
        val s = ScreenView(id, stack.isNotEmpty())
        stack.add(s)
        root.addView(s, FrameLayout.LayoutParams(-1, -1))
        s.rebuild()
        if (animate && !Theme.reduceMotion) {
            s.translationX = root.width.toFloat()
            s.animate().translationX(0f).setDuration(Ui.ms(340)).setInterpolator(Ui.easeOut).start()
            prev?.animate()?.translationX(-root.width * 0.3f)?.alpha(0.4f)?.setDuration(Ui.ms(340))?.setInterpolator(Ui.easeOut)?.withEndAction { prev.visibility = View.INVISIBLE }?.start()
        } else prev?.visibility = View.INVISIBLE
    }

    private fun pop() {
        if (stack.size <= 1) return
        val top = stack.removeAt(stack.size - 1)
        val prev = stack.last()
        prev.visibility = View.VISIBLE
        prev.rebuild()
        if (Theme.reduceMotion) {
            root.removeView(top); prev.translationX = 0f; prev.alpha = 1f
            return
        }
        prev.animate().translationX(0f).alpha(1f).setDuration(Ui.ms(320)).setInterpolator(Ui.easeOut).start()
        top.animate().translationX(root.width.toFloat()).setDuration(Ui.ms(320)).setInterpolator(Ui.easeOut).withEndAction { root.removeView(top) }.start()
    }

    private fun refresh() = stack.lastOrNull()?.rebuild()

    @SuppressLint("ViewConstructor")
    private inner class ScreenView(val id: String, val canGoBack: Boolean) : FrameLayout(this@SettingsActivity) {
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; clipToPadding = false }
        private val bar = FrameLayout(context)
        private val small = TextView(context)
        private var title = ""

        init {
            addView(scroll, LayoutParams(-1, -1))
            if (canGoBack) {
                val back = BackButton(context)
                back.setOnClickListener { pop() }
                bar.addView(back, LayoutParams(40.dpi, 40.dpi, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = 14.dpi })
            }
            small.setTextColor(Theme.text)
            small.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17.dp)
            small.typeface = Ui.font(600)
            small.alpha = 0f
            bar.addView(small, LayoutParams(-2, -2, Gravity.CENTER))
            addView(bar, LayoutParams(-1, 52.dpi))
            scroll.setOnScrollChangeListener { _, _, y, _, _ -> small.alpha = ((y - 30.dp) / 30.dp).coerceIn(0f, 1f) }
            applyInsets()
        }

        fun applyInsets() {
            (bar.layoutParams as LayoutParams).topMargin = insets.top
            bar.requestLayout()
            scroll.setPadding(0, insets.top + 52.dpi, 0, insets.bottom)
        }

        fun rebuild() {
            val y = scroll.scrollY
            scroll.removeAllViews()
            val page = build(id)
            title = page.title
            small.text = title
            scroll.addView(page.body)
            scroll.post { scroll.scrollTo(0, y) }
        }
    }

    // ------------------------------------------------------------------ screens

    private fun build(id: String): Page = when (id) {
        "appearance" -> appearance()
        "wallpaper" -> wallpaper()
        "icons" -> icons()
        "home" -> homeScreen()
        "widgets", "weather" -> widgets()
        "city" -> city()
        "lock" -> lock()
        "notifications" -> notifications()
        "motion" -> motion()
        "hidden" -> hidden()
        "about" -> about()
        else -> main()
    }

    private val purple = 0xFF5E5CE6.toInt()
    private val blue = 0xFF0A84FF.toInt()
    private val teal = 0xFF30B0C7.toInt()
    private val pink = 0xFFFF375F.toInt()
    private val indigo = 0xFF5856D6.toInt()
    private val green = 0xFF30D158.toInt()
    private val orange = 0xFFFF9F0A.toInt()
    private val red = 0xFFFF453A.toInt()
    private val gray = 0xFF8E8E93.toInt()

    private fun main(): Page {
        val p = Page(this, "Settings")
        val isDefault = HomeSwitch.isDefault(this)
        p.group {
            add(AuraCard(this@SettingsActivity).apply { setOnClickListener { push("about") } })
        }
        if (!isDefault) {
            p.group {
                add(kit.row("Finish Setting Up", Glyph.HOME, green, subtitle = "Make Aura your Home Screen", chevron = true,
                    accessory = Badge(this@SettingsActivity, "1")) { HomeSwitch.makeDefault(this@SettingsActivity) }, 60.dpi)
            }
        }
        p.group(footer = "Opens Android's “Default home app” list. Pick One UI Home to go back, or Aura to come back. You can also add the “Switch Home” tile to Quick Settings.") {
            nav("Switch Home Screen", Glyph.SWAP, indigo, if (isDefault) "Aura" else "One UI Home") { HomeSwitch.openPicker(this@SettingsActivity) }
            if (!isDefault) nav("Try Aura Now", Glyph.SPARKLES, purple, null, "Opens Aura without changing your default") {
                startActivity(Intent(this@SettingsActivity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        p.group {
            nav("Appearance", Glyph.APPEARANCE, blue, mapOf("dark" to "Dark", "light" to "Light", "auto" to "Automatic")[Prefs.appearance]) { push("appearance") }
            nav("Wallpaper", Glyph.FLOWER, teal, wallpaperName()) { push("wallpaper") }
            nav("App Icons", Glyph.GRID, pink, styleName(Prefs.iconStyle)) { push("icons") }
            nav("Home Screen", Glyph.PHONE, indigo, "${Prefs.columns}×${Prefs.rows}") { push("home") }
            nav("Widgets", Glyph.WIDGETS, blue) { push("widgets") }
            nav("Lock Screen", Glyph.LOCK, purple, if (Prefs.lockEnabled) "On" else "Off") { push("lock") }
            nav("Notifications", Glyph.BELL, red, if (NotificationHub.hasAccess(this@SettingsActivity)) null else "Needs access") { push("notifications") }
        }
        p.group {
            nav("Motion & Battery", Glyph.BATTERY, green) { push("motion") }
            nav("Hidden Apps", Glyph.EYE_SLASH, gray, Prefs.hidden.size.takeIf { it > 0 }?.toString()) { push("hidden") }
            nav("About Aura", Glyph.INFO, gray) { push("about") }
        }
        return p
    }

    private fun wallpaperName() = if (Prefs.wallpaper.startsWith("custom")) "Photo" else WallpaperArt.find(Prefs.wallpaper).name
    private fun styleName(s: String) = mapOf("colorful" to "Colourful", "dark" to "Dark", "clear" to "Clear", "tinted" to "Tinted")[s] ?: s

    private fun appearance(): Page {
        val p = Page(this, "Appearance")
        val opts = listOf("light", "dark", "auto")
        p.group(footer = "Automatic follows One UI's dark mode. Dark uses less battery on the A56's AMOLED screen.") {
            segmented(listOf("Light", "Dark", "Automatic"), opts.indexOf(Prefs.appearance).coerceAtLeast(0)) { i ->
                Prefs.appearance = opts[i]
                Theme.update(this@SettingsActivity)
                recreate()
            }
        }
        p.group(footer = "Glass panels show a blurred copy of your wallpaper. Turn this on for solid panels (a little lighter on battery).") {
            toggle("Reduce Transparency", Prefs.reduceTransparency) { Prefs.reduceTransparency = it; Theme.update(this@SettingsActivity); root.invalidate() }
        }
        return p
    }

    private fun wallpaper(): Page {
        val p = Page(this, "Wallpaper")
        val grid = WallpaperGrid()
        p.view(grid, 12.dpi)
        p.group {
            nav("Choose from Photos", Glyph.PHOTO, blue) { pickPhoto() }
            nav("Also Use for Phone Wallpaper", Glyph.FLOWER, teal, subtitle = "Sets it on One UI's lock screen and AOD too") {
                Backdrop.applyToSystem(this@SettingsActivity) { ok -> Toast.makeText(this@SettingsActivity, if (ok) "Phone wallpaper updated" else "Couldn't set the wallpaper", Toast.LENGTH_SHORT).show() }
            }
        }
        return p
    }

    private fun icons(): Page {
        val p = Page(this, "App Icons")
        val styles = listOf(
            "colorful" to "Original colours, glossy edge",
            "dark" to "Dark tiles, easier on the eyes at night",
            "clear" to "Frosted glass with white symbols",
            "tinted" to "One colour of your choice"
        )
        p.group {
            for ((id, desc) in styles) {
                add(IconStyleRow(id, styleName(id), desc, Prefs.iconStyle == id) { Prefs.iconStyle = id; refresh() })
            }
        }
        if (Prefs.iconStyle == "tinted") {
            p.group("Tint") { view(TintPicker()) }
        }
        p.group {
            slider("Icon Size", 0.8f, 1.2f, Prefs.iconScale, 0.05f, { "${(it * 100).roundToInt()}%" }) { Prefs.iconScale = it }
            toggle("Show App Names", Prefs.labels) { Prefs.labels = it }
        }
        return p
    }

    private fun homeScreen(): Page {
        val p = Page(this, "Home Screen")
        p.group("Layout", footer = "6 rows fits the Galaxy A56's tall 20:9 screen like an iPhone. Apps that don't fit move to the next page.") {
            segmented(listOf("4 columns", "5 columns"), if (Prefs.columns >= 5) 1 else 0, "Columns") { Prefs.columns = if (it == 1) 5 else 4 }
            segmented(listOf("5 rows", "6 rows", "7 rows"), (Prefs.rows - 5).coerceIn(0, 2), "Rows") { Prefs.rows = it + 5 }
        }
        p.group("Size", footer = "GUI Scale makes everything in Aura bigger or smaller: icons, text, widgets, dock and menus.") {
            slider("GUI Scale", 0.8f, 1.2f, Prefs.uiScale, 0.05f, { "${(it * 100).roundToInt()}%" }) { Prefs.uiScale = it; Theme.update(this@SettingsActivity); recreate() }
            slider("Icon Size", 0.8f, 1.2f, Prefs.iconScale, 0.05f, { "${(it * 100).roundToInt()}%" }) { Prefs.iconScale = it }
            toggle("Show App Names", Prefs.labels) { Prefs.labels = it }
        }
        p.group("Dock") {
            toggle("Show Dock", Prefs.dock) { Prefs.dock = it; refresh() }
            if (Prefs.dock) segmented(listOf("4 apps", "5 apps"), if (Prefs.dockCount >= 5) 1 else 0, "Dock size") { Prefs.dockCount = if (it == 1) 5 else 4 }
        }
        p.group("Gestures") {
            toggle("Search Button", Prefs.searchPill, subtitle = "The “Search” pill above the dock (shows page dots while swiping)") { Prefs.searchPill = it }
            val opts = listOf("search", "notifications", "none")
            segmented(listOf("Search", "Notifications", "Nothing"), opts.indexOf(Prefs.swipeDown).coerceAtLeast(0), "Swipe down on the Home Screen") { Prefs.swipeDown = opts[it] }
        }
        p.group {
            button("Edit Home Screen") { openHome("edit") }
            button("Add a Widget") { openHome("widgets") }
            button("Reset Home Screen Layout", Theme.red) {
                AuraDialog.show(this@SettingsActivity, "Reset Home Screen?", "Your pages, folders and widgets go back to the default layout.", listOf(
                    AuraDialog.Action("Cancel"),
                    AuraDialog.Action("Reset", AuraDialog.Style.DESTRUCTIVE) { Prefs.layoutReset = System.currentTimeMillis() }
                ))
            }
        }
        return p
    }

    private fun openHome(action: String) {
        startActivity(Intent(this, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_ACTION, action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun widgets(): Page {
        val p = Page(this, "Widgets")
        p.group(footer = "Aura has Weather, Clock, Calendar and Batteries widgets in small, medium and large sizes, and can show widgets from your other apps too. Long-press the Home Screen and tap + to add one.") {
            button("Add a Widget") { openHome("widgets") }
        }
        val hasLoc = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        p.group("Weather", footer = "Weather from Open-Meteo. Updates at most every 30 minutes, and only while a weather widget is on screen. Location uses the phone's network position (no GPS).") {
            toggle("Use My Location", Prefs.weatherUseLocation && hasLoc, Glyph.LOCATION, blue) { on ->
                Prefs.weatherUseLocation = on
                if (on && !hasLoc) requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
                Prefs.weatherTime = 0L
                WeatherRepo.refreshIfStale(this@SettingsActivity, force = true)
            }
            nav("City", Glyph.CLOUD_SUN, teal, Prefs.weatherCity.ifEmpty { "Not set" }) { push("city") }
            segmented(listOf("°C", "°F"), if (Prefs.weatherUnit == "f") 1 else 0, "Units") { Prefs.weatherUnit = if (it == 1) "f" else "c" }
            button("Refresh Now") {
                WeatherRepo.refreshIfStale(this@SettingsActivity, force = true)
                Toast.makeText(this@SettingsActivity, "Updating weather…", Toast.LENGTH_SHORT).show()
            }
        }
        return p
    }

    private fun city(): Page {
        val p = Page(this, "City")
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val field = EditText(this).apply {
            hint = "Search for a city"
            setHintTextColor(Theme.text2)
            setTextColor(Theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 17.dp)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            setPadding(16.dpi, 14.dpi, 16.dpi, 14.dpi)
        }
        var pending: Runnable? = null
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pending?.let { field.removeCallbacks(it) }
                val q = s?.toString()?.trim().orEmpty()
                if (q.length < 2) return
                val r = Runnable {
                    WeatherRepo.searchCity(q) { list ->
                        results.removeAllViews()
                        val kit = Kit(this@SettingsActivity)
                        val box = kit.groupBox()
                        val g = Group(kit, box)
                        if (list.isEmpty()) g.row("No results") else for (c in list) g.row(c.name, subtitle = c.detail) {
                            Prefs.weatherCity = c.name
                            Prefs.weatherLat = c.lat.toFloat()
                            Prefs.weatherLon = c.lon.toFloat()
                            Prefs.weatherUseLocation = false
                            Prefs.weatherTime = 0L
                            WeatherRepo.refreshIfStale(this@SettingsActivity, force = true)
                            hideKeyboard(field)
                            pop()
                        }
                        results.addView(box)
                    }
                }
                pending = r
                field.postDelayed(r, 350)
            }
        })
        p.group { view(field) }
        p.view(results)
        field.post { field.requestFocus(); getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(field, 0) }
        return p
    }

    private fun hideKeyboard(v: View) {
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun lock(): Page {
        val p = Page(this, "Lock Screen")
        val canShow = LockController.canShow(this)
        p.group(footer = "Aura's lock screen sits on top of One UI's lock. Swiping up asks for your PIN, pattern or fingerprint exactly as before, so your phone stays just as secure.") {
            toggle("Aura Lock Screen", Prefs.lockEnabled, Glyph.LOCK, purple) { on ->
                Prefs.lockEnabled = on
                if (on && !LockController.canShow(this@SettingsActivity)) {
                    AuraDialog.show(this@SettingsActivity, "One more step",
                        "To appear when you wake the phone, Aura needs to be your Home Screen, or be allowed to “Display over other apps”.",
                        listOf(
                            AuraDialog.Action("Display over apps") { requestOverlay() },
                            AuraDialog.Action("Make Aura Home", AuraDialog.Style.BOLD) { HomeSwitch.makeDefault(this@SettingsActivity) },
                            AuraDialog.Action("Later")
                        ))
                }
                refresh()
            }
            if (Prefs.lockEnabled) button("Preview Lock Screen") { LockController.show(this@SettingsActivity, preview = true) }
        }
        if (Prefs.lockEnabled) {
            p.group("Clock") {
                val styles = listOf("glass", "bold", "thin")
                segmented(listOf("Glass", "Bold", "Thin"), styles.indexOf(Prefs.lockClock).coerceAtLeast(0)) { Prefs.lockClock = styles[it] }
            }
            p.group("Show") {
                toggle("Widgets", Prefs.lockWidgets, subtitle = "Battery, weather, alarm and date under the clock") { Prefs.lockWidgets = it }
                toggle("Notifications", Prefs.lockNotifs) { Prefs.lockNotifs = it }
                toggle("Flashlight & Camera", Prefs.lockShortcuts) { Prefs.lockShortcuts = it }
            }
            p.group("Notification Previews") {
                val o = listOf("always", "unlocked", "never")
                segmented(listOf("Always", "When Unlocked", "Never"), o.indexOf(Prefs.lockPreviews).coerceAtLeast(0)) { Prefs.lockPreviews = o[it] }
            }
            p.group("Permissions", footer = "If a switch in Android settings is greyed out: open App info › ⋮ (top-right) › “Allow restricted settings”, then try again. Android does this for apps installed from an APK file.") {
                permissionRows(this, canShow)
            }
        }
        return p
    }

    private fun permissionRows(g: Group, canShow: Boolean) {
        val notif = NotificationHub.hasAccess(this)
        g.nav("Notification Access", Glyph.BELL, red, if (notif) "Allowed" else "Allow") { NotificationHub.requestAccess(this) }
        g.nav("Display Over Other Apps", Glyph.PHONE, indigo, if (Settings.canDrawOverlays(this)) "Allowed" else if (canShow) "Optional" else "Allow") { requestOverlay() }
        g.nav("App Info", Glyph.INFO, gray) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun requestOverlay() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
        }
    }

    private fun notifications(): Page {
        val p = Page(this, "Notifications")
        val o = listOf("count", "dot", "off")
        p.group("App Icon Badges") {
            segmented(listOf("Number", "Dot", "Off"), o.indexOf(Prefs.badges).coerceAtLeast(0)) { Prefs.badges = o[it] }
        }
        p.group(footer = "Aura reads notifications only to show them on its lock screen and as icon badges. Nothing leaves your phone.") {
            permissionRows(this, LockController.canShow(this@SettingsActivity))
        }
        return p
    }

    private fun motion(): Page {
        val p = Page(this, "Motion & Battery")
        p.group(footer = "Aura draws nothing while you're not touching it: no live wallpaper, no timers. Widgets refresh once a minute while visible, weather every 30 minutes.") {
            toggle("Reduce Motion", Prefs.reduceMotion, Glyph.SPARKLES, blue, "Fades instead of zooms, no icon jiggle, no bounce") { Prefs.reduceMotion = it; Theme.update(this@SettingsActivity) }
            toggle("Reduce Transparency", Prefs.reduceTransparency, Glyph.APPEARANCE, gray, "Solid panels instead of glass") { Prefs.reduceTransparency = it; Theme.update(this@SettingsActivity); root.invalidate() }
            toggle("Follow Power Saving", Prefs.autoLowPower, Glyph.BATTERY, green, "When One UI's Power saving is on, Aura switches to reduced motion and transparency") { Prefs.autoLowPower = it }
        }
        val speeds = listOf(0.8f, 1f, 1.3f)
        p.group("Animation Speed") {
            val idx = speeds.indexOfFirst { kotlin.math.abs(it - Prefs.animSpeed) < 0.05f }.let { if (it < 0) 1 else it }
            segmented(listOf("Relaxed", "Normal", "Fast"), idx) { Prefs.animSpeed = speeds[it]; Theme.update(this@SettingsActivity) }
        }
        p.group("Tips", footer = "Pure-black wallpapers (Midnight, Graphite) with Dark appearance save the most power: black pixels are switched off on AMOLED.") {
            nav("Use Midnight Wallpaper", Glyph.MOON, indigo) { Prefs.wallpaper = "midnight"; Prefs.appearance = "dark"; Theme.update(this@SettingsActivity); recreate() }
        }
        return p
    }

    private fun hidden(): Page {
        val p = Page(this, "Hidden Apps")
        val hidden = Prefs.hidden.mapNotNull { AppRepository[it] }.sortedBy { it.label.lowercase() }
        if (hidden.isEmpty()) {
            p.group(footer = "Long-press an app on the Home Screen and choose “Hide from Home Screen”. Hidden apps still show up in Search.") { row("No hidden apps") }
        } else {
            p.group(footer = "Tap Show to put an app back on the last page.") {
                for (e in hidden) {
                    val show = kit.text("Show", 17.dp, Theme.accent).apply { setPadding(12.dpi, 6.dpi, 4.dpi, 6.dpi) }
                    val row = kit.row(e.label, accessory = show) { Prefs.hidden = Prefs.hidden - e.key; refresh() }
                    add(row)
                }
            }
        }
        return p
    }

    private fun about(): Page {
        val p = Page(this, "About")
        p.group {
            add(AuraCard(this@SettingsActivity))
            row("Version", value = BuildConfig.VERSION_NAME)
            row("Made for", value = "Galaxy A56 · Android 15")
        }
        p.group(footer = "Aura is a home screen and lock screen app. It doesn't change One UI itself, so you can switch back to One UI Home at any time and everything is exactly as it was.") {
            nav("Switch Home Screen", Glyph.SWAP, indigo) { HomeSwitch.openPicker(this@SettingsActivity) }
            button("Restart Aura") {
                Prefs.sp.edit().commit()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        return p
    }

    // ------------------------------------------------------------------ pickers

    private fun pickPhoto() {
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
        else Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_PHOTO)
        } catch (e: Exception) {
            Toast.makeText(this, "No photo picker found", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show()
            Backdrop.importPhoto(this, uri) { ok ->
                if (!ok) Toast.makeText(this, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
        if (requestCode == HomeSwitch.REQ_ROLE) refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            Prefs.weatherTime = 0L
            WeatherRepo.refreshIfStale(this, force = true)
            refresh()
        }
    }

    // ------------------------------------------------------------------ custom rows

    /** "Aura" header card, like the account card at the top of iOS Settings. */
    @SuppressLint("ViewConstructor")
    private class AuraCard(a: Activity) : LinearLayout(a) {
        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dpi, 14.dpi, 16.dpi, 14.dpi)
            val icon = object : View(a) {
                val bmp by lazy { IconFactory.renderDrawable(a.packageManager.getApplicationIcon(a.packageName), 60.dpi, "colorful", 0) }
                override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(60.dpi, 60.dpi)
                override fun onDraw(c: Canvas) = c.drawBitmap(bmp, 0f, 0f, null)
            }
            addView(icon)
            val texts = LinearLayout(a).apply { orientation = VERTICAL; setPadding(14.dpi, 0, 0, 0) }
            texts.addView(Kit(a).text("Aura", 22.dp, Theme.text, 600))
            texts.addView(Kit(a).text("Home Screen, Lock Screen & Widgets", 14.dp, Theme.text2).apply { setPadding(0, 4.dpi, 0, 0) })
            addView(texts, LayoutParams(0, -2, 1f))
        }
    }

    @SuppressLint("ViewConstructor")
    private class Badge(a: Activity, private val s: String) : View(a) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(24.dpi, 24.dpi)
        override fun onDraw(c: Canvas) {
            p.color = 0xFFFF3B30.toInt()
            c.drawCircle(width / 2f, height / 2f, width / 2f, p)
            p.color = Color.WHITE
            p.textSize = 14.dp
            p.typeface = Ui.font(600)
            p.textAlign = Paint.Align.CENTER
            c.drawText(s, width / 2f, height / 2f + 5.dp, p)
        }
    }

    /** Built-in wallpapers as a 3-column grid of live thumbnails. */
    @SuppressLint("ViewConstructor")
    private inner class WallpaperGrid : ViewGroup(this@SettingsActivity) {
        private val all = WallpaperArt.all
        private val thumbs = HashMap<String, Bitmap>()
        private val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val r = RectF()
        private val cols = 3

        init {
            setWillNotDraw(false)
            val (sw, sh) = Backdrop.screenSize(this@SettingsActivity)
            val tw = sw / 5
            val th = sh / 5
            for (s in all) Backdrop.thumbnail(s.id, tw, th) { b -> thumbs[s.id] = b; invalidate() }
        }

        private fun cell(): Pair<Float, Float> {
            val (sw, sh) = Backdrop.screenSize(this@SettingsActivity)
            val gap = 12.dp
            val w = (width - gap * (cols - 1)) / cols
            return w to w * sh / sw + 30.dp
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val (sw, sh) = Backdrop.screenSize(this@SettingsActivity)
            val cw = (w - 12.dp * (cols - 1)) / cols
            val ch = cw * sh / sw + 30.dp
            val rows = (all.size + cols - 1) / cols
            setMeasuredDimension(w, (rows * (ch + 12.dp)).roundToInt() + 8.dpi)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}

        override fun onDraw(c: Canvas) {
            val (cw, ch) = cell()
            val gap = 12.dp
            for ((i, s) in all.withIndex()) {
                val x = (i % cols) * (cw + gap)
                val y = 8.dp + (i / cols) * (ch + gap)
                r.set(x, y, x + cw, y + ch - 30.dp)
                val b = thumbs[s.id]
                if (b != null) {
                    val sh = android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                    val m = android.graphics.Matrix()
                    m.setScale(r.width() / b.width, r.height() / b.height)
                    m.postTranslate(r.left, r.top)
                    sh.setLocalMatrix(m)
                    p.shader = sh
                    c.drawRoundRect(r, 14.dp, 14.dp, p)
                    p.shader = null
                } else {
                    p.color = Theme.fill
                    c.drawRoundRect(r, 14.dp, 14.dp, p)
                }
                if (Prefs.wallpaper == s.id) {
                    ring.color = Theme.accent
                    ring.strokeWidth = 3.dp
                    r.inset(-4.dp, -4.dp)
                    c.drawRoundRect(r, 17.dp, 17.dp, ring)
                    r.inset(4.dp, 4.dp)
                }
                tp.color = Theme.text
                tp.textSize = 14.dp
                tp.typeface = Ui.font(if (Prefs.wallpaper == s.id) 600 else 400)
                c.drawText(s.name, x + cw / 2f, y + ch - 10.dp, tp)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
                val (cw, ch) = cell()
                val gap = 12.dp
                val col = (ev.x / (cw + gap)).toInt()
                val row = ((ev.y - 8.dp) / (ch + gap)).toInt()
                val i = row * cols + col
                if (col in 0 until cols && i in all.indices) {
                    Prefs.wallpaper = all[i].id
                    Backdrop.ensure(this@SettingsActivity)
                    Ui.tick(this)
                    invalidate()
                }
            }
            return true
        }
    }

    /** One icon style with four real app icons rendered in that style. */
    @SuppressLint("ViewConstructor")
    private inner class IconStyleRow(private val style: String, private val name: String, private val desc: String, private val selected: Boolean, onClick: () -> Unit) :
        LinearLayout(this@SettingsActivity) {
        private val samples = ArrayList<Bitmap>()

        init {
            orientation = VERTICAL
            setPadding(16.dpi, 12.dpi, 16.dpi, 12.dpi)
            val head = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val texts = LinearLayout(context).apply { orientation = VERTICAL }
            texts.addView(Kit(context).text(name, 17.dp, Theme.text, if (selected) 600 else 400))
            texts.addView(Kit(context).text(desc, 13.dp, Theme.text2).apply { setPadding(0, 3.dpi, 0, 0) })
            head.addView(texts, LayoutParams(0, -2, 1f))
            head.addView(object : View(context) {
                override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(24.dpi, 24.dpi)
                override fun onDraw(c: Canvas) { if (selected) dev.aura.launcher.gfx.Glyphs.draw(c, Glyph.CHECK, width / 2f, height / 2f, 20.dp, Theme.accent, 2.6f) }
            })
            addView(head)
            val strip = object : View(context) {
                private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                private val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40808088 }
                private val rr = RectF()
                override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), 60.dpi)
                override fun onDraw(c: Canvas) {
                    val s = 48.dp
                    for ((i, b) in samples.withIndex()) {
                        val x = i * (s + 14.dp)
                        rr.set(x, 6.dp, x + s, 6.dp + s)
                        if (style == "clear") c.drawPath(dev.aura.launcher.gfx.Squircle.path(s).apply { offset(x, 6.dp) }, plate)
                        c.drawBitmap(b, null, rr, bp)
                    }
                }
            }
            addView(strip, LayoutParams(-1, 60.dpi).apply { topMargin = 8.dpi })
            setOnClickListener { onClick() }
            val tv = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            // render samples off the main thread
            val keys = sampleKeys()
            val size = 48.dpi * 2
            Thread {
                val out = keys.mapNotNull { k -> AppRepository[k]?.let { IconFactory.render(it, size, style, Prefs.tintColor) } }
                post { samples.clear(); samples.addAll(out); strip.invalidate() }
            }.start()
        }
    }

    private fun sampleKeys(): List<String> {
        val pkgs = listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "com.sec.android.app.camera", "com.android.chrome", "com.sec.android.gallery3d", "com.android.settings", "com.google.android.youtube")
        val found = pkgs.mapNotNull { AppRepository.forPackage(it)?.key }.distinct()
        return (found + AppRepository.visibleApps().map { it.key }).distinct().take(5)
    }

    /** Colour dots for the Tinted icon style. */
    @SuppressLint("ViewConstructor")
    private inner class TintPicker : View(this@SettingsActivity) {
        private val colors = intArrayOf(0xFF64D2FF.toInt(), 0xFF0A84FF.toInt(), 0xFF5E5CE6.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF375F.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(), 0xFF30D158.toInt(), 0xFFE5E5EA.toInt())
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), 64.dpi)
        private fun step() = (width - 32.dp) / colors.size
        override fun onDraw(c: Canvas) {
            val st = step()
            for ((i, col) in colors.withIndex()) {
                val cx = 16.dp + st * (i + 0.5f)
                p.style = Paint.Style.FILL
                p.color = col
                c.drawCircle(cx, height / 2f, 13.dp, p)
                if (Prefs.tintColor == col) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.5f.dp
                    p.color = Theme.text
                    c.drawCircle(cx, height / 2f, 18.dp, p)
                }
            }
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
                val i = ((ev.x - 16.dp) / step()).toInt().coerceIn(0, colors.size - 1)
                Prefs.tintColor = colors[i]
                refresh()
            }
            return true
        }
    }

    companion object {
        const val EXTRA_SECTION = "section"
        private const val REQ_PHOTO = 61
        private const val REQ_LOCATION = 62
    }
}
