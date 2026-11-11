package app.velo.browser

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import app.velo.browser.ui.BrowserRoot

class MainActivity : ComponentActivity() {
    lateinit var browser: Browser
        private set

    val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        browser.onFilesPicked(it.resultCode, it.data)
    }
    val permissionPrompt = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        browser.onAndroidPermissionResult()
    }
    val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        browser.onWallpaperPicked(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; the interface pads itself.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        browser = Browser(this)
        browser.start(recreated = savedInstanceState != null, intent = intent)
        setContent { BrowserRoot(browser) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        browser.handleIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        browser.onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        super.onResume()
        browser.onResume()
    }

    override fun onPause() {
        browser.onPause()
        super.onPause()
    }

    override fun onStop() {
        // Saved here as well as during browsing, so tabs survive the app being swiped away.
        browser.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        browser.onDestroy()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        browser.onTrimMemory(level)
    }
}
