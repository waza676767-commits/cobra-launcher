package app.velo.browser

import android.app.ActivityManager
import android.app.DownloadManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.velo.browser.browser.AdBlocker
import app.velo.browser.browser.BrowserHost
import app.velo.browser.browser.TabManager
import app.velo.browser.data.SavedSession
import app.velo.browser.data.Settings
import app.velo.browser.data.UrlTools
import java.io.File

enum class Screen { BROWSER, TABS, SETTINGS, HISTORY }

data class LinkTarget(val link: String?, val image: String?)

class SitePermissionRequest(
    val site: String,
    val what: String,
    val androidPermissions: List<String>,
    val onResult: (Boolean) -> Unit,
)

class ExternalRequest(val intent: Intent, val description: String, val fallbackUrl: String?)

data class DownloadRequest(val url: String, val userAgent: String?, val fileName: String, val mimeType: String?)

/** A pin being created ([id] null) or edited. */
data class PinEdit(val id: Long?, val title: String, val url: String)

data class ToastMessage(val id: Long, val text: String)

/**
 * Everything the screen needs to know and do: which panel is open, pending questions for the
 * user, and the actions behind every button. The interface reads this; it never talks to the
 * engine directly.
 */
class Browser(private val activity: MainActivity) : BrowserHost {
    val app: VeloApp = VeloApp.from(activity)
    val settings: Settings = app.settings
    private val main = Handler(Looper.getMainLooper())

    var systemDark by mutableStateOf(isSystemDark(activity.resources.configuration))
    override val dark: Boolean
        get() = when (settings.themeMode) {
            Settings.THEME_LIGHT -> false
            Settings.THEME_DARK -> true
            else -> systemDark
        }

    val tabs = TabManager(activity, app, this)
    val ai get() = app.ai

    // ── What's on screen ───────────────────────────────────────────────────────────────────────
    var screen by mutableStateOf(Screen.BROWSER)

    /** Settings page to open straight into, e.g. "vpn". Empty for the overview. */
    var settingsPage by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
        private set
    private var searchInNewTab = false
    var menuOpen by mutableStateOf(false)
    var aiOpen by mutableStateOf(false)
    var findOpen by mutableStateOf(false)
    var barHidden by mutableStateOf(false)
    var inFullscreen by mutableStateOf(false)
        private set

    // ── Questions waiting for the user ─────────────────────────────────────────────────────────
    var restoreOffer by mutableStateOf<SavedSession?>(null)
        private set
    var linkMenu by mutableStateOf<LinkTarget?>(null)
    var sitePermission by mutableStateOf<SitePermissionRequest?>(null)
        private set
    var externalRequest by mutableStateOf<ExternalRequest?>(null)
    var downloadRequest by mutableStateOf<DownloadRequest?>(null)
    var pinEdit by mutableStateOf<PinEdit?>(null)
    var toast by mutableStateOf<ToastMessage?>(null)
    private var toastId = 0L

    // ── Memory ─────────────────────────────────────────────────────────────────────────────────
    var memoryUsedMb by mutableIntStateOf(0)
        private set
    var adListUpdating by mutableStateOf(false)
        private set

    val deviceMemoryMb: Int by lazy {
        val info = ActivityManager.MemoryInfo()
        (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)).toInt()
    }

    val memoryLimitMb: Int
        get() = if (settings.ramLimitMb > 0) settings.ramLimitMb else (deviceMemoryMb / 5).coerceIn(350, 1200)

    // ── Start-up ───────────────────────────────────────────────────────────────────────────────

    fun start(recreated: Boolean, intent: Intent?) {
        app.io.execute {
            app.adBlocker.load()
            maybeRefreshAdList()
        }
        app.vpn.onChanged = { tabs.applySettingsToAll() }
        app.vpn.apply()

        if (recreated) {
            // The system rebuilt the screen behind our back; just put things back as they were.
            app.session.readCurrent()?.let { tabs.restore(it, replace = true) }
        } else {
            val previous = app.session.takePrevious()
            when {
                previous == null -> Unit
                settings.restoreMode == Settings.RESTORE_ALWAYS -> {
                    tabs.restore(previous, replace = true)
                    app.session.discardPrevious()
                }
                settings.restoreMode == Settings.RESTORE_NEVER -> app.session.discardPrevious()
                else -> restoreOffer = previous
            }
        }
        if (tabs.tabs.isEmpty()) tabs.newTab()
        settings.firstRunDone = true
        handleIntent(intent)
    }

    fun acceptRestore() {
        val offer = restoreOffer ?: return
        restoreOffer = null
        tabs.restore(offer, replace = false)
        app.session.discardPrevious()
        notify(if (offer.realTabs.size == 1) "Tab restored" else "${offer.realTabs.size} tabs restored")
    }

    fun declineRestore() {
        restoreOffer = null
        app.session.discardPrevious()
    }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.startsWith("http") }?.let { openFromOutside(it) }
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH ->
                intent.getStringExtra(SearchManager.QUERY)?.let { openFromOutside(it) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                openFromOutside(URL_IN_TEXT.find(text)?.value ?: text)
            }
            ACTION_SEARCH -> {
                closePanels()
                openSearch(newTab = tabs.active?.showHome == false)
            }
            ACTION_ASK_AI -> {
                closePanels()
                aiOpen = true
            }
            ACTION_NEW_TAB -> {
                closePanels()
                newTab()
            }
        }
    }

    private fun openFromOutside(input: String) {
        closePanels()
        val current = tabs.active
        if (current != null && current.showHome && current.url.isEmpty()) {
            tabs.load(current, input)
        } else {
            tabs.newTab(input).external = true
        }
    }

    private fun closePanels() {
        screen = Screen.BROWSER
        searchOpen = false
        menuOpen = false
        aiOpen = false
        closeFind()
    }

    // ── Everyday actions ───────────────────────────────────────────────────────────────────────

    /** Text the address field starts with: the current address or search terms, selected. */
    var searchSeed: String = ""
        private set

    fun openSearch(newTab: Boolean = false) {
        searchInNewTab = newTab
        val tab = tabs.active
        searchSeed = if (newTab || tab == null || tab.showHome) "" else UrlTools.searchQuery(tab.url) ?: tab.url
        searchOpen = true
        barHidden = false
    }

    fun closeSearch() {
        searchOpen = false
    }

    fun submitSearch(input: String) {
        if (input.isBlank()) return
        searchOpen = false
        screen = Screen.BROWSER
        if (searchInNewTab) tabs.newTab(input) else tabs.loadInActive(input)
    }

    fun newTab(url: String? = null) {
        screen = Screen.BROWSER
        barHidden = false
        tabs.newTab(url)
    }

    fun openInNewTab(url: String, background: Boolean = false) {
        val opener = tabs.active?.id ?: -1L
        val tab = tabs.newTab(null, select = !background, openerId = opener)
        tabs.load(tab, url)
        if (background) notify("Opened in a new tab") else screen = Screen.BROWSER
    }

    fun openTabs() {
        tabs.active?.let { tabs.captureThumbnail(it) }
        closeFind()
        screen = Screen.TABS
    }

    fun openSettings(page: String = "") {
        settingsPage = page
        menuOpen = false
        aiOpen = false
        screen = Screen.SETTINGS
    }

    fun onBack() {
        when {
            inFullscreen -> exitFullscreen()
            findOpen -> closeFind()
            screen != Screen.BROWSER -> screen = Screen.BROWSER
            tabs.goBack() -> barHidden = false
            else -> activity.moveTaskToBack(true)
        }
    }

    fun closeFind() {
        if (findOpen) {
            findOpen = false
            tabs.clearFind()
        }
    }

    fun togglePin() {
        val tab = tabs.active ?: return
        if (tab.showHome || tab.url.isEmpty()) return
        val pinned = app.pins.toggle(tab.title, tab.url)
        notify(if (pinned) "Pinned to your start page" else "Removed from your start page")
    }

    fun sharePage() {
        val tab = tabs.active ?: return
        if (tab.showHome) return
        shareText(tab.url, tab.title)
    }

    fun shareText(text: String, subject: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (!subject.isNullOrBlank()) send.putExtra(Intent.EXTRA_SUBJECT, subject)
        runCatching { activity.startActivity(Intent.createChooser(send, null)) }
    }

    fun copy(text: String, confirmation: String = "Copied") {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Velo", text))
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < 33) notify(confirmation)
    }

    fun addToHomeScreen() {
        val tab = tabs.active ?: return
        if (tab.showHome || tab.url.isEmpty()) return
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(activity)) {
            notify("Your home screen doesn't support shortcuts")
            return
        }
        val icon = tab.favicon?.let { IconCompat.createWithBitmap(it) }
            ?: IconCompat.createWithResource(activity, R.mipmap.ic_launcher)
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(tab.url), activity, MainActivity::class.java)
        val shortcut = ShortcutInfoCompat.Builder(activity, "page_" + tab.url.hashCode())
            .setShortLabel(tab.displayTitle.take(24))
            .setIcon(icon)
            .setIntent(open)
            .build()
        ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null)
    }

    fun openDownloads() {
        runCatching {
            activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { notify("Open your Files app to see downloads") }
    }

    fun openDefaultAppsSettings() {
        runCatching {
            activity.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    override fun notify(message: String) {
        toast = ToastMessage(++toastId, message)
    }

    // ── Appearance ─────────────────────────────────────────────────────────────────────────────

    /** Pushes the light/dark choice to the window and to loaded pages. */
    fun applyTheme(backgroundArgb: Int) {
        activity.window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
        tabs.setDark(dark)
    }

    /** Sets whether the status and navigation bar icons are dark (for light backgrounds). */
    fun setSystemBarIcons(darkIcons: Boolean) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
    }

    fun pickWallpaper() {
        runCatching {
            activity.imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.onFailure { notify("No photo picker available") }
    }

    fun onWallpaperPicked(uri: Uri?) {
        uri ?: return
        app.io.execute {
            val ok = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1400 || bounds.outHeight / (sample * 2) >= 2400) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = activity.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: error("unreadable")
                wallpaperFile(activity).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bitmap.recycle()
            }.isSuccess
            main.post {
                if (ok) {
                    settings.wallpaperType = Settings.WALLPAPER_CUSTOM
                    settings.wallpaperVersion++
                } else {
                    notify("That picture couldn't be opened")
                }
            }
        }
    }

    // ── Ad blocker list ────────────────────────────────────────────────────────────────────────

    private fun maybeRefreshAdList() {
        val week = 7L * 24 * 60 * 60 * 1000
        if (settings.adBlock && System.currentTimeMillis() - settings.adListUpdatedAt > week) refreshAdList(silent = true)
    }

    /** Downloads the latest community ad-server list on top of the built-in one. */
    fun refreshAdList(silent: Boolean = false) {
        if (adListUpdating) return
        main.post { adListUpdating = true }
        app.io.execute {
            val result = runCatching { app.adBlocker.installUpdate(app.net.getText(AdBlocker.UPDATE_URL)) }
            main.post {
                adListUpdating = false
                if (result.isSuccess) {
                    settings.adListUpdatedAt = System.currentTimeMillis()
                    if (!silent) notify("Block list updated")
                } else if (!silent) {
                    notify("Couldn't update the block list")
                }
            }
        }
    }

    // ── Memory limit ───────────────────────────────────────────────────────────────────────────

    private val memoryTick = object : Runnable {
        override fun run() {
            checkMemory()
            main.postDelayed(this, 5000)
        }
    }

    /**
     * Estimates how much memory Velo is using and, if that is over the user's limit, puts the
     * least recently used background tabs to sleep until it isn't.
     */
    fun checkMemory() {
        app.io.execute {
            val ownMb = (Debug.getPss() / 1024).toInt()
            main.post {
                var used = estimateMb(ownMb, tabs.awakeCount)
                val limit = memoryLimitMb
                while (used > limit && tabs.sleepOldest()) used = estimateMb(ownMb, tabs.awakeCount)
                memoryUsedMb = used
            }
        }
    }

    /**
     * Android doesn't let an app measure its web-page process directly, so loaded pages are
     * counted at a typical size on top of Velo's own measured memory.
     */
    private fun estimateMb(ownMb: Int, awake: Int): Int =
        ownMb + if (awake > 0) ENGINE_BASE_MB + awake * PAGE_MB else 0

    fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) tabs.sleepAllBackground()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────────────────────

    fun onResume() {
        tabs.onResume()
        main.removeCallbacks(memoryTick)
        main.postDelayed(memoryTick, 1500)
    }

    fun onPause() {
        tabs.onPause()
        main.removeCallbacks(memoryTick)
    }

    fun onStop() {
        tabs.saveNow()
    }

    fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        app.vpn.onChanged = null
        tabs.saveNow()
        tabs.destroy()
    }

    fun onConfigurationChanged(config: Configuration) {
        systemDark = isSystemDark(config)
    }

    // ── Engine callbacks ───────────────────────────────────────────────────────────────────────

    private var fileCallback: ValueCallback<Array<Uri>>? = null

    override fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams) {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        val intent = runCatching { params.createIntent() }.getOrNull()
            ?: Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            activity.filePicker.launch(intent)
        } catch (e: ActivityNotFoundException) {
            fileCallback = null
            callback.onReceiveValue(null)
            notify("No app on this phone can pick files")
        }
    }

    fun onFilesPicked(resultCode: Int, data: Intent?) {
        val callback = fileCallback ?: return
        fileCallback = null
        val clip = data?.clipData
        val result = if (resultCode == android.app.Activity.RESULT_OK && clip != null && clip.itemCount > 0) {
            Array(clip.itemCount) { clip.getItemAt(it).uri }
        } else {
            WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        }
        callback.onReceiveValue(result)
    }

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        view.setBackgroundColor(Color.BLACK)
        (activity.window.decorView as ViewGroup).addView(
            view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        inFullscreen = true
    }

    override fun exitFullscreen() {
        val view = customView ?: return
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        (activity.window.decorView as ViewGroup).removeView(view)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        inFullscreen = false
        callback?.onCustomViewHidden()
    }

    override fun showLinkMenu(link: String?, image: String?) {
        linkMenu = LinkTarget(link?.takeIf { it.isNotBlank() }, image?.takeIf { it.isNotBlank() })
    }

    private var awaitingAndroidPermission: SitePermissionRequest? = null

    override fun askSitePermission(
        origin: String,
        what: String,
        androidPermissions: List<String>,
        onResult: (Boolean) -> Unit,
    ) {
        sitePermission?.onResult?.invoke(false)
        sitePermission = SitePermissionRequest(UrlTools.displayHost(origin), what, androidPermissions, onResult)
    }

    fun answerSitePermission(allow: Boolean) {
        val request = sitePermission ?: return
        sitePermission = null
        if (!allow) {
            request.onResult(false)
            return
        }
        val missing = request.androidPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            request.onResult(true)
        } else {
            awaitingAndroidPermission = request
            activity.permissionPrompt.launch(missing.toTypedArray())
        }
    }

    fun onAndroidPermissionResult() {
        val request = awaitingAndroidPermission ?: return
        awaitingAndroidPermission = null
        val granted = request.androidPermissions.map {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
        // Location is fine with either precise or approximate; camera and microphone need all.
        val isLocation = request.androidPermissions.any { it.contains("LOCATION") }
        val ok = if (isLocation) granted.any { it } else granted.all { it }
        request.onResult(ok)
        if (!ok) notify("Velo doesn't have that Android permission")
    }

    override fun openExternal(uri: Uri) {
        val intent = runCatching {
            if (uri.scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, uri)
        }.getOrNull() ?: return
        // A page must not be able to aim the intent at a private component of some app.
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("http") }
        val description = when (intent.scheme ?: uri.scheme) {
            "tel" -> "This page wants to open your phone app."
            "mailto" -> "This page wants to open your email app."
            "sms", "smsto" -> "This page wants to open your messages app."
            "market" -> "This page wants to open your app store."
            else -> "This page wants to open another app on your phone."
        }
        externalRequest = ExternalRequest(intent, description, fallback)
    }

    fun confirmExternal() {
        val request = externalRequest ?: return
        externalRequest = null
        try {
            activity.startActivity(request.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            if (request.fallbackUrl != null) tabs.loadInActive(request.fallbackUrl)
            else notify("No app on this phone can open that link")
        }
    }

    override fun requestDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            notify("Velo can't save this kind of download yet")
            return
        }
        downloadRequest = DownloadRequest(url, userAgent, URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType)
    }

    fun downloadImage(url: String) {
        requestDownload(url, null, null, null)
    }

    fun confirmDownload() {
        val request = downloadRequest ?: return
        downloadRequest = null
        runCatching {
            val download = DownloadManager.Request(Uri.parse(request.url))
                .setTitle(request.fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.mimeType?.takeIf { it.isNotBlank() }?.let { download.setMimeType(it) }
            CookieManager.getInstance().getCookie(request.url)?.let { download.addRequestHeader("Cookie", it) }
            request.userAgent?.let { download.addRequestHeader("User-Agent", it) }
            if (Build.VERSION.SDK_INT >= 29) {
                download.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, request.fileName)
            } else {
                download.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, request.fileName)
            }
            (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(download)
        }.onSuccess {
            notify("Downloading ${request.fileName}")
        }.onFailure {
            notify("The download couldn't start")
        }
    }

    private var scrollAccumulated = 0
    private var scrollQuietUntil = 0L

    override fun onPageScrolled(dy: Int) {
        if (!settings.hideBarOnScroll || dy == 0) return
        val now = System.currentTimeMillis()
        // Showing or hiding the bar resizes the page, which itself reports as scrolling.
        if (now < scrollQuietUntil) return
        if ((dy > 0) != (scrollAccumulated > 0)) scrollAccumulated = 0
        scrollAccumulated += dy
        val density = activity.resources.displayMetrics.density
        if (!barHidden && scrollAccumulated > 36 * density) {
            barHidden = true
            scrollAccumulated = 0
            scrollQuietUntil = now + 350
        } else if (barHidden && scrollAccumulated < -18 * density) {
            barHidden = false
            scrollAccumulated = 0
            scrollQuietUntil = now + 350
        }
    }

    companion object {
        const val ACTION_SEARCH = "app.velo.browser.action.SEARCH"
        const val ACTION_ASK_AI = "app.velo.browser.action.ASK_AI"
        const val ACTION_NEW_TAB = "app.velo.browser.action.NEW_TAB"

        private const val ENGINE_BASE_MB = 70
        private const val PAGE_MB = 55
        private val URL_IN_TEXT = Regex("https?://\\S+")

        fun wallpaperFile(context: Context) = File(context.filesDir, "wallpaper.jpg")

        fun isSystemDark(config: Configuration): Boolean =
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
