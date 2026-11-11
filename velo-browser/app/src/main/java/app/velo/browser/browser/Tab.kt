package app.velo.browser.browser

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ScriptHandler
import app.velo.browser.data.UrlTools
import java.util.concurrent.atomic.AtomicInteger

data class PageError(val title: String, val detail: String, val url: String)

/** One browser tab. The fields the interface shows are Compose state; the rest is engine plumbing. */
class Tab(val id: Long) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var favicon by mutableStateOf<Bitmap?>(null)
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /** True while the tab shows Velo's start page instead of a web page. */
    var showHome by mutableStateOf(true)
    var error by mutableStateOf<PageError?>(null)
    var thumbnail by mutableStateOf<Bitmap?>(null)

    /** Ads and trackers blocked on the current page. */
    var blocked by mutableIntStateOf(0)
    var desktop by mutableStateOf(false)

    /** True when the page was unloaded to save memory; it comes back when the tab is opened. */
    var sleeping by mutableStateOf(false)

    var webView: WebView? = null
    var savedState: Bundle? = null
    var pendingUrl: String? = null
    var lastActive = 0L

    /** Tab that opened this one through a link, or -1. Back on the first page returns there. */
    var openerId = -1L

    /** Opened from another app; Back on the first page returns to that app. */
    var external = false

    /** Backs the assistant panel rather than a browser tab; its visits stay out of history. */
    var internal = false

    @Volatile
    var pageHost = ""
    val blockedCounter = AtomicInteger(0)
    val scriptHandlers = ArrayList<ScriptHandler>()

    val secure: Boolean get() = url.startsWith("https://")

    val displayTitle: String
        get() = when {
            showHome -> "Start page"
            title.isNotBlank() -> title
            else -> UrlTools.displayHost(url)
        }
}

/** What the engine needs from the screen it lives in. */
interface BrowserHost {
    val dark: Boolean
    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams)
    fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun exitFullscreen()
    fun showLinkMenu(link: String?, image: String?)
    fun askSitePermission(
        origin: String,
        what: String,
        androidPermissions: List<String>,
        onResult: (Boolean) -> Unit,
    )
    fun openExternal(uri: Uri)
    fun requestDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?)
    fun notify(message: String)
    fun onPageScrolled(dy: Int)
}
