package app.velo.browser.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.velo.browser.R
import app.velo.browser.VeloApp
import app.velo.browser.data.SavedSession
import app.velo.browser.data.SavedTab
import app.velo.browser.data.UrlTools
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the tabs and their WebViews: creating them, moving the visible one into the screen,
 * putting background ones to sleep, and saving the session.
 */
class TabManager(
    private val activity: Activity,
    private val app: VeloApp,
    private val host: BrowserHost,
) {
    private val settings = app.settings
    private val main = Handler(Looper.getMainLooper())

    val tabs = mutableStateListOf<Tab>()
    var activeId by mutableLongStateOf(-1L)
        private set
    val active: Tab? get() = tabs.firstOrNull { it.id == activeId }

    /** Matches for find-in-page: current (1-based) and total. */
    var findCurrent by mutableIntStateOf(0)
        private set
    var findTotal by mutableIntStateOf(0)
        private set

    private var nextId = 1L
    private var container: FrameLayout? = null
    private var dark = host.dark
    private var resumed = true

    // WebViews read isLightTheme from their context to decide whether pages should go dark, so
    // each one gets a swappable context instead of the activity's own theme.
    private val lightContext by lazy { ContextThemeWrapper(activity, R.style.Theme_Velo_Web_Light) }
    private val darkContext by lazy { ContextThemeWrapper(activity, R.style.Theme_Velo_Web_Dark) }

    private val baseUserAgent: String by lazy {
        runCatching { WebSettings.getDefaultUserAgent(activity) }.getOrDefault("")
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s"), "")
    }

    private val transparentPoster: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    // ── Tabs ───────────────────────────────────────────────────────────────────────────────────

    fun newTab(url: String? = null, select: Boolean = true, openerId: Long = -1L): Tab {
        val tab = Tab(nextId++)
        tab.openerId = openerId
        tab.desktop = settings.desktopSite
        val at = tabs.indexOfFirst { it.id == openerId }
        if (at >= 0) tabs.add(at + 1, tab) else tabs.add(tab)
        if (select) select(tab.id)
        if (!url.isNullOrBlank()) load(tab, url)
        scheduleSave()
        return tab
    }

    fun select(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        active?.let { if (it.id != id) captureThumbnail(it) }
        activeId = id
        tab.lastActive = System.currentTimeMillis()
        clearFind()
        attachActive()
        scheduleSave()
    }

    fun close(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs[index]
        val wasActive = id == activeId
        destroyWebView(tab)
        tab.thumbnail = null
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            newTab()
        } else if (wasActive) {
            val next = tabs.firstOrNull { it.id == tab.openerId } ?: tabs[index.coerceAtMost(tabs.size - 1)]
            activeId = -1L
            select(next.id)
        }
        scheduleSave()
    }

    fun closeAll() {
        tabs.forEach { destroyWebView(it) }
        tabs.clear()
        activeId = -1L
        newTab()
    }

    // ── Navigation ─────────────────────────────────────────────────────────────────────────────

    /** Loads an address or runs a search in [tab]. */
    fun load(tab: Tab, input: String) {
        val url = UrlTools.resolve(input, settings.privateSearch, dark)
        if (url.isEmpty()) return
        tab.pendingUrl = null
        tab.showHome = false
        tab.error = null
        tab.url = url
        tab.title = ""
        tab.loading = true
        tab.progress = 5
        val web = wake(tab)
        web.loadUrl(url, extraHeaders())
        if (tab.id == activeId) attachActive()
        updateNav(tab)
        scheduleSave()
    }

    fun loadInActive(input: String) {
        val tab = active ?: newTab()
        load(tab, input)
    }

    /** Returns false when there is nowhere left to go back to. */
    fun goBack(): Boolean {
        val tab = active ?: return false
        if (tab.showHome) return false
        val web = tab.webView
        if (web != null && web.canGoBack()) {
            tab.error = null
            web.goBack()
            return true
        }
        if (tab.openerId != -1L && tabs.any { it.id == tab.openerId }) {
            close(tab.id)
            return true
        }
        if (tab.external) return false
        showHome(tab)
        return true
    }

    fun goForward() {
        val tab = active ?: return
        if (tab.showHome) {
            if (tab.url.isNotEmpty()) {
                tab.showHome = false
                attachActive()
                updateNav(tab)
            }
            return
        }
        tab.webView?.takeIf { it.canGoForward() }?.let {
            tab.error = null
            it.goForward()
        }
    }

    fun reload() {
        val tab = active ?: return
        if (tab.showHome) return
        tab.error = null
        val web = wake(tab)
        if (web.url.isNullOrEmpty() && tab.url.isNotEmpty()) web.loadUrl(tab.url, extraHeaders()) else web.reload()
    }

    fun stop() {
        active?.webView?.stopLoading()
        active?.loading = false
    }

    fun showHome(tab: Tab) {
        tab.showHome = true
        tab.error = null
        tab.loading = false
        tab.webView?.stopLoading()
        if (tab.id == activeId) attachActive()
        updateNav(tab)
        scheduleSave()
    }

    fun toggleDesktop(tab: Tab) {
        tab.desktop = !tab.desktop
        tab.webView?.let {
            it.settings.userAgentString = userAgent(tab.desktop)
            it.reload()
        }
    }

    private fun updateNav(tab: Tab) {
        val web = tab.webView
        if (tab.showHome) {
            tab.canGoBack = false
            tab.canGoForward = tab.url.isNotEmpty()
        } else {
            tab.canGoBack = true
            tab.canGoForward = web?.canGoForward() == true
        }
    }

    // ── Showing the active page ────────────────────────────────────────────────────────────────

    fun setContainer(view: FrameLayout?) {
        container = view
        if (view != null) attachActive()
    }

    private fun attachActive() {
        val c = container ?: return
        val tab = active
        val web = if (tab != null && !tab.showHome) wake(tab) else null
        for (i in c.childCount - 1 downTo 0) {
            val child = c.getChildAt(i)
            if (child !== web) {
                c.removeViewAt(i)
                (child as? WebView)?.onPause()
            }
        }
        if (web != null && web.parent !== c) {
            (web.parent as? ViewGroup)?.removeView(web)
            c.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
            if (resumed) web.onResume()
        }
    }

    // ── Sleeping tabs ──────────────────────────────────────────────────────────────────────────

    val awakeCount: Int get() = tabs.count { it.webView != null } + assistantViews.size

    /** Unloads a background page, keeping what's needed to bring it back where it was. */
    fun sleep(tab: Tab) {
        val web = tab.webView ?: return
        if (tab.id == activeId && !tab.showHome) return
        val state = Bundle()
        val list = runCatching { web.saveState(state) }.getOrNull()
        tab.savedState = if (list != null && list.size > 0) state else null
        tab.pendingUrl = tab.url
        destroyWebView(tab)
        tab.sleeping = true
    }

    /** Sleeps the least recently used background tab. Returns false if none is left to sleep. */
    fun sleepOldest(): Boolean {
        val candidate = tabs
            .filter { it.webView != null && !(it.id == activeId && !it.showHome) }
            .minByOrNull { it.lastActive } ?: return false
        sleep(candidate)
        return true
    }

    fun sleepAllBackground() {
        tabs.toList().forEach { sleep(it) }
    }

    private fun wake(tab: Tab): WebView {
        tab.webView?.let { return it }
        val web = createWebView(tab)
        tab.webView = web
        tab.sleeping = false
        val state = tab.savedState
        tab.savedState = null
        val pending = tab.pendingUrl
        tab.pendingUrl = null
        val restored = state != null && runCatching { web.restoreState(state) }.getOrNull() != null
        if (!restored && !pending.isNullOrBlank()) {
            tab.loading = true
            web.loadUrl(pending, extraHeaders())
        }
        return web
    }

    private fun destroyWebView(tab: Tab) {
        val web = tab.webView ?: return
        tab.webView = null
        tab.scriptHandlers.clear()
        tab.loading = false
        (web.parent as? ViewGroup)?.removeView(web)
        web.stopLoading()
        web.webChromeClient = null
        web.setDownloadListener(null)
        web.setOnLongClickListener(null)
        web.destroy()
    }

    // ── Assistant panel pages ──────────────────────────────────────────────────────────────────

    private val assistantViews = HashMap<String, WebView>()

    /** Changes when the assistant pages are thrown away, so the panel knows to fetch a new one. */
    var assistantRevision by mutableIntStateOf(0)
        private set

    /**
     * The page shown in the assistant panel when the user signs in on a provider's own site
     * instead of using an API key. Kept alive between openings so the conversation stays put.
     */
    fun assistantWebView(id: String, url: String): WebView =
        assistantViews.getOrPut(id) {
            val holder = Tab(-100L - assistantViews.size)
            holder.internal = true
            holder.showHome = false
            createWebView(holder).also {
                holder.webView = it
                it.loadUrl(url)
            }
        }

    private fun releaseAssistantViews() {
        if (assistantViews.isEmpty()) return
        assistantViews.values.forEach {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        assistantViews.clear()
        assistantRevision++
    }

    // ── WebView set-up ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: Tab): WebView {
        val web = WebView(MutableContextWrapper(if (dark) darkContext else lightContext))
        web.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        web.setBackgroundColor(if (dark) 0xFF0D0D10.toInt() else Color.WHITE)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        val s = web.settings
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.mediaPlaybackRequiresUserGesture = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.safeBrowsingEnabled = true
        s.setGeolocationEnabled(true)
        runCatching {
            // Without this the WebView tags every request with the app's package name.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
            }
        }
        // Lets Android reclaim page memory first when Velo isn't on screen.
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
        applySettings(web, tab)
        installScripts(web, tab)

        web.webViewClient = Client(tab)
        web.webChromeClient = Chrome(tab)
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            host.requestDownload(url, userAgent, contentDisposition, mimeType)
        }
        web.setFindListener { ordinal, count, _ ->
            if (tab.id == activeId) {
                findCurrent = if (count == 0) 0 else ordinal + 1
                findTotal = count
            }
        }
        web.setOnScrollChangeListener { _, _, y, _, oldY ->
            if (tab.id == activeId) host.onPageScrolled(y - oldY)
        }
        val hrefHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                val link = msg.data.getString("url")
                val image = msg.data.getString("src")
                if (link != null || image != null) host.showLinkMenu(link, image)
            }
        }
        web.setOnLongClickListener {
            val hit = web.hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    host.showLinkMenu(hit.extra, null)
                    true
                }
                WebView.HitTestResult.IMAGE_TYPE -> {
                    host.showLinkMenu(null, hit.extra)
                    true
                }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    web.requestFocusNodeHref(hrefHandler.obtainMessage())
                    true
                }
                else -> false
            }
        }
        return web
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(web: WebView, tab: Tab) {
        val s = web.settings
        s.javaScriptEnabled = settings.javaScript
        s.textZoom = settings.textZoom
        s.userAgentString = userAgent(tab.desktop)
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, dark && settings.darkWebPages)
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            web, !(settings.blockThirdPartyCookies || settings.privateSearch)
        )
    }

    /** Scripts that must run before the page's own code. */
    private fun installScripts(web: WebView, tab: Tab) {
        tab.scriptHandlers.forEach { runCatching { it.remove() } }
        tab.scriptHandlers.clear()
        val js = earlyScript()
        if (js.isEmpty()) return
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                tab.scriptHandlers += WebViewCompat.addDocumentStartJavaScript(web, js, setOf("*"))
            }
        }
    }

    private fun earlyScript(): String = buildString {
        if (settings.privateSearch) append(GPC_JS)
        if (app.vpn.isRouting && settings.vpnWebRtcGuard) append(VpnManager.WEBRTC_GUARD_JS)
    }

    private fun supportsEarlyScripts(): Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }.getOrDefault(false)

    /** Re-applies preferences to every loaded page after the user changes a setting. */
    fun applySettingsToAll() {
        tabs.forEach { tab ->
            tab.webView?.let {
                applySettings(it, tab)
                installScripts(it, tab)
            }
        }
    }

    /** Switches loaded pages between light and dark without reloading them. */
    fun setDark(value: Boolean) {
        if (dark == value) return
        dark = value
        tabs.forEach { tab ->
            val web = tab.webView ?: return@forEach
            (web.context as? MutableContextWrapper)?.baseContext = if (dark) darkContext else lightContext
            web.setBackgroundColor(if (dark) 0xFF0D0D10.toInt() else Color.WHITE)
            applySettings(web, tab)
        }
        assistantViews.values.forEach { web ->
            (web.context as? MutableContextWrapper)?.baseContext = if (dark) darkContext else lightContext
            web.setBackgroundColor(if (dark) 0xFF0D0D10.toInt() else Color.WHITE)
        }
        // The WebView re-reads its theme when it is attached to a window.
        val c = container
        val web = active?.webView
        if (c != null && web != null && web.parent === c) {
            c.removeView(web)
            c.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        }
    }

    private fun userAgent(desktop: Boolean): String =
        if (!desktop) {
            baseUserAgent
        } else {
            baseUserAgent
                .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
                .replace(" Mobile ", " ")
        }

    private fun extraHeaders(): Map<String, String> =
        if (settings.privateSearch) mapOf("Sec-GPC" to "1", "DNT" to "1") else emptyMap()

    // ── Page helpers ───────────────────────────────────────────────────────────────────────────

    fun find(query: String) {
        val web = active?.webView ?: return
        if (query.isEmpty()) clearFind() else web.findAllAsync(query)
    }

    fun findNext(forward: Boolean) {
        active?.webView?.findNext(forward)
    }

    fun clearFind() {
        active?.webView?.clearMatches()
        findCurrent = 0
        findTotal = 0
    }

    /**
     * Reads the visible text of [tab]'s page. [onResult] gets the text and whether it had to be
     * cut at [maxChars].
     */
    fun readPageText(tab: Tab, maxChars: Int, onResult: (text: String, trimmed: Boolean) -> Unit) {
        val web = tab.webView
        if (web == null || tab.showHome) {
            onResult("", false)
            return
        }
        val js = "(function(){var t=document.body?document.body.innerText:'';" +
            "return JSON.stringify({t:t.slice(0,$maxChars),c:t.length>$maxChars})})()"
        web.evaluateJavascript(js) { raw ->
            val parsed = runCatching {
                val inner = JSONTokener(raw).nextValue() as String
                val o = JSONObject(inner)
                o.getString("t") to o.getBoolean("c")
            }.getOrDefault("" to false)
            onResult(parsed.first, parsed.second)
        }
    }

    fun captureThumbnail(tab: Tab) {
        val web = tab.webView ?: return
        if (tab.showHome || web.width <= 0 || web.height <= 0 || web.parent == null) return
        runCatching {
            val scale = THUMBNAIL_WIDTH / web.width.toFloat()
            val w = THUMBNAIL_WIDTH
            val h = (web.height * scale).toInt().coerceIn(1, (w * 1.5f).toInt())
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.drawColor(if (dark) 0xFF0D0D10.toInt() else Color.WHITE)
            canvas.scale(scale, scale)
            canvas.translate(-web.scrollX.toFloat(), -web.scrollY.toFloat())
            web.draw(canvas)
            tab.thumbnail = bitmap
        }
    }

    fun clearData(cookies: Boolean, cache: Boolean, siteData: Boolean) {
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        if (siteData) WebStorage.getInstance().deleteAllData()
        if (cache) {
            val live = tabs.firstNotNullOfOrNull { it.webView }
            if (live != null) {
                live.clearCache(true)
            } else {
                runCatching {
                    val temp = WebView(activity)
                    temp.clearCache(true)
                    temp.destroy()
                }
            }
        }
    }

    // ── Session ────────────────────────────────────────────────────────────────────────────────

    private val saveRunnable = Runnable { saveNow() }

    fun scheduleSave() {
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, 500)
    }

    /** Writes the open tabs to disk right away. Cheap enough to call when the app is closing. */
    fun saveNow() {
        main.removeCallbacks(saveRunnable)
        val saved = tabs.map { SavedTab(if (it.showHome) "" else it.url, if (it.showHome) "" else it.title) }
        val index = tabs.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        app.session.save(SavedSession(saved, index, System.currentTimeMillis()))
    }

    /**
     * Brings back saved tabs. They start asleep and load only when opened, so restoring many tabs
     * costs almost nothing. With [replace] the current tabs are dropped first.
     */
    fun restore(session: SavedSession, replace: Boolean) {
        val real = session.realTabs
        if (real.isEmpty()) return
        if (replace) {
            tabs.forEach { destroyWebView(it) }
            tabs.clear()
            activeId = -1L
        } else if (tabs.size == 1 && tabs[0].showHome && tabs[0].url.isEmpty()) {
            tabs.clear()
            activeId = -1L
        }
        val wanted = session.tabs.getOrNull(session.active)
        var toSelect: Tab? = null
        val now = System.currentTimeMillis()
        real.forEachIndexed { i, saved ->
            val tab = Tab(nextId++)
            tab.url = saved.url
            tab.title = saved.title
            tab.showHome = false
            tab.sleeping = true
            tab.pendingUrl = saved.url
            tab.desktop = settings.desktopSite
            tab.lastActive = now - (real.size - i)
            tab.favicon = app.favicons.get(UrlTools.host(saved.url))
            tabs.add(tab)
            if (saved === wanted) toSelect = tab
        }
        if (activeId == -1L || replace) select((toSelect ?: tabs.last()).id)
        scheduleSave()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────────────────────

    fun onResume() {
        resumed = true
        active?.takeIf { !it.showHome }?.webView?.onResume()
    }

    fun onPause() {
        resumed = false
        active?.webView?.onPause()
        CookieManager.getInstance().flush()
    }

    fun destroy() {
        main.removeCallbacksAndMessages(null)
        tabs.forEach { destroyWebView(it) }
        releaseAssistantViews()
        container = null
    }

    // ── Blocked-request counter ────────────────────────────────────────────────────────────────

    private val syncScheduled = AtomicBoolean(false)

    private val syncBlocked = Runnable {
        syncScheduled.set(false)
        var added = 0L
        tabs.forEach { tab ->
            val n = tab.blockedCounter.get()
            if (n > tab.blocked) added += n - tab.blocked
            tab.blocked = n
        }
        if (added > 0) settings.adsBlockedTotal += added
    }

    private fun noteBlocked() {
        if (syncScheduled.compareAndSet(false, true)) main.postDelayed(syncBlocked, 350)
    }

    // ── Engine callbacks ───────────────────────────────────────────────────────────────────────

    private inner class Client(private val tab: Tab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme == "http" || scheme == "https") {
                if (settings.privateSearch && request.isForMainFrame && !request.isRedirect &&
                    request.method.equals("GET", ignoreCase = true)
                ) {
                    val original = uri.toString()
                    val clean = UrlTools.stripTracking(original)
                    if (clean != original) {
                        view.loadUrl(clean, extraHeaders())
                        return true
                    }
                }
                return false
            }
            if (scheme == "about" || scheme == "blob" || scheme == "data" || scheme == "javascript") return false
            if (scheme == "file" || scheme == "content") return true
            if (request.isForMainFrame) host.openExternal(uri)
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame) return null
            if (app.adBlocker.shouldBlock(request.url, tab.pageHost)) {
                tab.blockedCounter.incrementAndGet()
                noteBlocked()
                return app.adBlocker.emptyResponse()
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url.startsWith("chrome-error://")) return
            main.removeCallbacks(syncBlocked)
            syncBlocked.run()
            tab.blockedCounter.set(0)
            tab.blocked = 0
            tab.error = null
            tab.loading = true
            tab.pageHost = UrlTools.host(url)
            if (url != "about:blank") tab.url = url
            if (!supportsEarlyScripts()) {
                val js = earlyScript()
                if (js.isNotEmpty()) view.evaluateJavascript(js, null)
            }
            updateNav(tab)
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            injectCosmetic(view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            tab.loading = false
            tab.progress = 100
            view.title?.takeIf { it.isNotBlank() && !it.startsWith("http") && tab.error == null }?.let { tab.title = it }
            injectCosmetic(view)
            updateNav(tab)
            scheduleSave()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (url.startsWith("chrome-error://") || url == "about:blank") return
            tab.url = url
            tab.pageHost = UrlTools.host(url)
            updateNav(tab)
            if (!isReload && !tab.internal && settings.saveHistory &&
                !(settings.privateSearch && UrlTools.searchQuery(url) != null)
            ) {
                val title = view.title.orEmpty()
                app.io.execute { app.history.record(url, title) }
            }
            scheduleSave()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            // A cancelled load (the user tapped stop or went elsewhere) isn't worth an error page.
            if (error.errorCode == ERROR_UNKNOWN && error.description?.contains("ERR_ABORTED") == true) return
            tab.loading = false
            tab.error = PageError(
                friendlyError(error.errorCode),
                error.description?.toString().orEmpty(),
                request.url.toString(),
            )
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            val failing = error.url.orEmpty()
            if (UrlTools.host(failing) == tab.pageHost || tab.pageHost.isEmpty()) {
                tab.loading = false
                tab.error = PageError(
                    "This connection isn't private",
                    "The site's security certificate couldn't be trusted, so Velo didn't load it. " +
                        "Someone may be trying to intercept the connection.",
                    failing,
                )
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
            val credentials = app.vpn.credentialsFor(host)
            if (credentials != null && handler.useHttpAuthUsernamePassword()) {
                handler.proceed(credentials.first, credentials.second)
            } else {
                handler.cancel()
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Every WebView shares one renderer, so all loaded pages are gone. Turn them into
            // sleeping tabs and reload the visible one.
            tabs.forEach { t ->
                if (t.webView != null) {
                    t.savedState = null
                    t.pendingUrl = t.url
                    destroyWebView(t)
                    t.sleeping = true
                }
            }
            releaseAssistantViews()
            main.post { attachActive() }
            return true
        }

        private fun injectCosmetic(view: WebView) {
            if (settings.adBlockCosmetic && app.adBlocker.activeFor(tab.pageHost)) {
                view.evaluateJavascript(AdBlocker.COSMETIC_JS, null)
            }
        }
    }

    private inner class Chrome(private val tab: Tab) : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (title.isNullOrBlank() || tab.error != null) return
            tab.title = title
            val url = tab.url
            if (settings.saveHistory && !tab.internal) app.io.execute { app.history.updateTitle(url, title) }
            scheduleSave()
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            if (icon == null) return
            tab.favicon = icon
            val pageHost = tab.pageHost
            app.io.execute { app.favicons.put(pageHost, icon) }
        }

        override fun getDefaultVideoPoster(): Bitmap = transparentPoster

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            host.enterFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            host.exitFullscreen()
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<android.net.Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            host.chooseFiles(filePathCallback, fileChooserParams)
            return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture && settings.blockPopups) {
                host.notify("Pop-up blocked")
                return false
            }
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val child = Tab(nextId++)
            child.openerId = tab.id
            child.desktop = tab.desktop
            child.showHome = false
            val at = tabs.indexOfFirst { it.id == tab.id }
            if (at >= 0) tabs.add(at + 1, child) else tabs.add(child)
            val web = createWebView(child)
            child.webView = web
            transport.webView = web
            resultMsg.sendToTarget()
            select(child.id)
            return true
        }

        override fun onCloseWindow(window: WebView) {
            close(tab.id)
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            host.askSitePermission(
                origin, "your location",
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            ) { granted -> callback.invoke(origin, granted, false) }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val wanted = ArrayList<String>()
            val android = ArrayList<String>()
            val labels = ArrayList<String>()
            for (res in request.resources) {
                when (res) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                        wanted += res
                        android += Manifest.permission.CAMERA
                        labels += "your camera"
                    }
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                        wanted += res
                        android += Manifest.permission.RECORD_AUDIO
                        labels += "your microphone"
                    }
                }
            }
            if (wanted.isEmpty()) {
                request.deny()
                return
            }
            host.askSitePermission(request.origin.toString(), labels.joinToString(" and "), android) { granted ->
                if (granted) request.grant(wanted.toTypedArray()) else request.deny()
            }
        }
    }

    private fun friendlyError(code: Int): String = when (code) {
        WebViewClient.ERROR_HOST_LOOKUP -> "Can't find this site"
        WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO -> "Can't connect"
        WebViewClient.ERROR_TIMEOUT -> "The site took too long to respond"
        WebViewClient.ERROR_PROXY_AUTHENTICATION -> "The VPN server refused the sign-in"
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "A secure connection couldn't be made"
        WebViewClient.ERROR_UNSAFE_RESOURCE -> "Blocked for your safety"
        WebViewClient.ERROR_BAD_URL, WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "That address isn't valid"
        else -> "This page didn't load"
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val THUMBNAIL_WIDTH = 360

        private const val GPC_JS =
            "try{Object.defineProperty(Navigator.prototype,'globalPrivacyControl'," +
                "{get:function(){return true},configurable:true})}catch(e){}"

        /** Context for anything outside this class that needs the activity behind a WebView. */
        fun activityOf(context: Context): Activity? {
            var c: Context? = context
            while (c != null) {
                if (c is Activity) return c
                c = (c as? android.content.ContextWrapper)?.baseContext
            }
            return null
        }
    }
}
