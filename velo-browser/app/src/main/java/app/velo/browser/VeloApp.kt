package app.velo.browser

import android.app.Application
import android.content.Context
import app.velo.browser.ai.AiChat
import app.velo.browser.browser.AdBlocker
import app.velo.browser.browser.VpnManager
import app.velo.browser.data.FaviconCache
import app.velo.browser.data.HistoryStore
import app.velo.browser.data.Net
import app.velo.browser.data.PinsStore
import app.velo.browser.data.SecureStore
import app.velo.browser.data.SessionStore
import app.velo.browser.data.Settings
import app.velo.browser.widget.PinsWidget
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Holds the pieces that outlive any one screen: preferences, stores and network. */
class VeloApp : Application() {
    lateinit var settings: Settings
        private set

    /** Small pool for disk and network work that shouldn't block the interface. */
    val io: ExecutorService = Executors.newFixedThreadPool(3)

    val history by lazy { HistoryStore(this) }
    val pins by lazy { PinsStore(this).also { it.onChanged = { PinsWidget.refresh(this) } } }
    val session by lazy { SessionStore(this) }
    val favicons by lazy { FaviconCache(this) }
    val secure by lazy { SecureStore(this) }
    val adBlocker by lazy { AdBlocker(this, settings) }
    val vpn by lazy { VpnManager(settings, secure) }
    val net by lazy { Net(vpn) }
    val ai by lazy { AiChat(this) }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
    }

    companion object {
        fun from(context: Context): VeloApp = context.applicationContext as VeloApp
    }
}
