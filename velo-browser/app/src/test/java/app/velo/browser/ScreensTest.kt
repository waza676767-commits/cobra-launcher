package app.velo.browser

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.velo.browser.ai.ChatMessage
import app.velo.browser.browser.PageError
import app.velo.browser.browser.VpnServer
import app.velo.browser.data.SavedSession
import app.velo.browser.data.SavedTab
import app.velo.browser.data.Settings
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs the real activity and walks through its screens, checking behaviour and saving a picture
 * of each to build/shots so the design can be reviewed without a device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class ScreensTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: VeloApp get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var b: Browser

    private fun launch(seed: (VeloApp) -> Unit = {}) {
        seed(app)
        rule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java).also { s -> s.onActivity { b = it.browser } }
        settle()
    }

    private fun ui(block: () -> Unit) {
        scenario!!.onActivity { block() }
        settle()
    }

    private fun settle(ms: Long = 1500) {
        // State changes reach the interface on the next looper pass, then need frames to draw.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
    }

    private fun shot(name: String) {
        settle()
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun startPageInEveryLook() {
        launch()
        assertEquals(1, b.tabs.tabs.size)
        assertTrue(b.tabs.active!!.showHome)
        assertNull("nothing to restore on a first run", b.restoreOffer)
        shot("01_start_light")

        ui { b.settings.themeMode = Settings.THEME_DARK }
        assertTrue(b.dark)
        shot("02_start_dark")

        ui {
            b.settings.wallpaperPreset = 0
            b.settings.wallpaperType = Settings.WALLPAPER_PRESET
            b.settings.accent = 0xFF7C5CFF.toInt()
            b.settings.adsBlockedTotal = 12843
        }
        shot("03_start_wallpaper")

        ui {
            b.settings.themeMode = Settings.THEME_LIGHT
            b.settings.wallpaperPreset = 7
            b.settings.style = Settings.STYLE_SOFT
            b.settings.barPosition = Settings.BAR_TOP
            b.settings.floatingBar = false
        }
        shot("04_start_light_wallpaper_top_bar")
    }

    @Test
    fun browsingMenuAndTabs() {
        launch()
        ui { b.submitSearch("example.com") }
        val tab = b.tabs.active!!
        assertFalse(tab.showHome)
        assertEquals("https://example.com", tab.url)
        ui {
            tab.title = "Example Domain"
            tab.loading = false
            tab.blocked = 7
            tab.pageHost = "example.com"
        }
        shot("10_page")

        ui { b.menuOpen = true }
        shot("11_menu")
        ui { b.menuOpen = false }

        ui {
            b.newTab("duckduckgo.com/?q=velo+browser")
            b.tabs.active!!.title = "velo browser at DuckDuckGo"
            b.newTab()
            b.newTab("wikipedia.org")
            b.tabs.active!!.title = "Wikipedia"
        }
        assertEquals(4, b.tabs.tabs.size)
        ui { b.openTabs() }
        shot("12_tabs")

        ui {
            b.screen = Screen.BROWSER
            b.tabs.active!!.error = PageError("Can't find this site", "net::ERR_NAME_NOT_RESOLVED", "https://wikipedia.org")
        }
        shot("13_error")

        ui {
            b.tabs.active!!.error = null
            b.findOpen = true
        }
        shot("14_find")
        ui { b.closeFind() }

        // Back from the first page of a tab returns to the start page, then leaves the app alone.
        ui { b.onBack() }
        assertTrue(b.tabs.active!!.showHome)

        ui { b.openSearch() }
        shot("15_search")
    }

    @Test
    fun closingTabsNeverLeavesTheBrowserEmpty() {
        launch()
        ui {
            b.newTab("a.example")
            b.newTab("b.example")
        }
        assertEquals(3, b.tabs.tabs.size)
        ui { b.tabs.close(b.tabs.activeId) }
        assertEquals(2, b.tabs.tabs.size)
        assertNotNull(b.tabs.active)
        ui { b.tabs.closeAll() }
        assertEquals(1, b.tabs.tabs.size)
        assertTrue(b.tabs.active!!.showHome)
    }

    @Test
    fun tabsAreSavedAndOfferedBackAfterAQuickClose() {
        launch()
        ui {
            b.submitSearch("news.example/story")
            b.tabs.active!!.title = "A long story"
            b.newTab("shop.example")
            b.tabs.active!!.title = "Shop"
        }
        // Swiping the app away: the activity stops and the process dies.
        ui { b.onStop() }
        scenario!!.close()
        scenario = null

        launch()
        val offer = b.restoreOffer
        assertNotNull("the user is asked before anything is restored", offer)
        assertEquals(listOf("https://news.example/story", "https://shop.example"), offer!!.realTabs.map { it.url })
        assertEquals("only the start page until they answer", 1, b.tabs.tabs.size)
        shot("20_restore_prompt")

        ui { b.acceptRestore() }
        assertNull(b.restoreOffer)
        assertEquals(listOf("https://news.example/story", "https://shop.example"), b.tabs.tabs.map { it.url })
        assertEquals("Shop", b.tabs.active!!.title)
        assertTrue("restored tabs load only when opened", b.tabs.tabs.first().sleeping)
    }

    @Test
    fun decliningTheOfferStartsFreshAndForgetsIt() {
        launch { it.session.save(SavedSession(listOf(SavedTab("https://old.example/", "Old")), 0, 1L)) }
        assertNotNull(b.restoreOffer)
        ui { b.declineRestore() }
        assertEquals(1, b.tabs.tabs.size)
        assertTrue(b.tabs.active!!.showHome)
        scenario!!.close()
        scenario = null
        launch()
        assertNull("not offered again", b.restoreOffer)
    }

    @Test
    fun restoreSettingIsRespected() {
        launch {
            it.settings.restoreMode = Settings.RESTORE_ALWAYS
            it.session.save(SavedSession(listOf(SavedTab("https://old.example/", "Old")), 0, 1L))
        }
        assertNull(b.restoreOffer)
        assertEquals(listOf("https://old.example/"), b.tabs.tabs.map { it.url })
        scenario!!.close()
        scenario = null

        launch {
            it.settings.restoreMode = Settings.RESTORE_NEVER
            it.session.save(SavedSession(listOf(SavedTab("https://old.example/", "Old")), 0, 1L))
        }
        assertNull(b.restoreOffer)
        assertTrue(b.tabs.active!!.showHome)
    }

    @Test
    fun memoryLimitPutsOldBackgroundTabsToSleep() {
        launch()
        ui {
            b.submitSearch("one.example")
            b.newTab("two.example")
            b.newTab("three.example")
        }
        assertEquals(3, b.tabs.awakeCount)
        ui { b.tabs.sleepOldest() }
        assertTrue("the least recently used tab sleeps first", b.tabs.tabs[0].sleeping)
        assertEquals(2, b.tabs.awakeCount)
        ui { b.tabs.sleepAllBackground() }
        assertEquals("the page on screen is never put to sleep", 1, b.tabs.awakeCount)
        assertFalse(b.tabs.active!!.sleeping)
        // Opening a sleeping tab wakes it.
        ui { b.tabs.select(b.tabs.tabs[0].id) }
        assertFalse(b.tabs.tabs[0].sleeping)
    }

    @Test
    fun settingsPages() {
        launch()
        ui { b.openSettings() }
        shot("30_settings")
        for (page in listOf("appearance", "start", "privacy", "performance", "general", "assistant", "about")) {
            ui { b.screen = Screen.BROWSER }
            ui { b.openSettings(page) }
            shot("31_settings_$page")
        }
        ui {
            b.app.vpn.saveServer(VpnServer("1", "Tor (Orbot)", "socks", "127.0.0.1", 9050), null)
            b.screen = Screen.BROWSER
        }
        ui { b.openSettings("vpn") }
        shot("32_settings_vpn")

        ui {
            b.settings.themeMode = Settings.THEME_DARK
            b.settings.accent = 0xFF22B866.toInt()
            b.screen = Screen.BROWSER
        }
        ui { b.openSettings("appearance") }
        shot("33_settings_appearance_dark")
    }

    @Test
    fun historyScreen() {
        launch {
            val now = System.currentTimeMillis()
            it.history.record("https://en.wikipedia.org/wiki/Bicycle", "Bicycle - Wikipedia", now - 5 * 60_000)
            it.history.record("https://news.example/today", "Today's headlines", now - 50 * 60_000)
            it.history.record("https://shop.example/cart", "Your cart", now - 26 * 60 * 60_000)
        }
        ui { b.screen = Screen.HISTORY }
        shot("40_history")
    }

    @Test
    fun assistantPanel() {
        launch()
        ui { b.aiOpen = true }
        shot("50_ai_connect")

        ui {
            b.app.secure.put("ai_key_claude", "test")
            b.ai.useApi(b.ai.provider)
        }
        shot("51_ai_empty_chat")

        ui {
            b.ai.messages += ChatMessage(true, "What makes a browser fast?")
            b.ai.messages += ChatMessage(
                false,
                "A few things matter most:\n\n- **Blocking ads and trackers** so pages pull in less\n- **Sleeping background tabs** to keep memory free\n- A lean interface that stays out of the way\n\nVelo does all three. Try `Settings → Performance` to set a memory limit.",
            )
        }
        shot("52_ai_chat")

        ui {
            b.settings.themeMode = Settings.THEME_DARK
            b.ai.messages += ChatMessage(true, "Thanks!")
            b.ai.messages += ChatMessage(false, "").also { it.pending = true }
        }
        shot("53_ai_chat_dark_typing")
    }
}
