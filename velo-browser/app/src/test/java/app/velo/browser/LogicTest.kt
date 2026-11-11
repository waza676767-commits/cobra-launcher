package app.velo.browser

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.velo.browser.browser.AdBlocker
import app.velo.browser.data.HistoryStore
import app.velo.browser.data.PinsStore
import app.velo.browser.data.SavedSession
import app.velo.browser.data.SavedTab
import app.velo.browser.data.SessionStore
import app.velo.browser.data.Settings
import app.velo.browser.data.UrlTools
import app.velo.browser.ui.MdBlock
import app.velo.browser.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogicTest {
    private val context: VeloApp get() = ApplicationProvider.getApplicationContext()

    // ── Addresses ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun typedTextBecomesAddressOrDuckDuckGoSearch() {
        assertEquals("https://example.com", UrlTools.resolve("example.com", false, false))
        assertEquals("https://example.com/a?b=1", UrlTools.resolve(" example.com/a?b=1 ", false, false))
        assertEquals("http://localhost:8080", UrlTools.resolve("localhost:8080", false, false))
        assertEquals("http://192.168.1.1", UrlTools.resolve("192.168.1.1", false, false))
        assertEquals("https://x.org/y", UrlTools.resolve("https://x.org/y", false, false))
        assertEquals("https://duckduckgo.com/?q=best+pizza", UrlTools.resolve("best pizza", false, false))
        // File names are searches, not addresses, even though they contain a dot.
        assertEquals("https://duckduckgo.com/?q=node.js", UrlTools.resolve("node.js", false, false))
        assertEquals("https://duckduckgo.com/?q=report.pdf", UrlTools.resolve("report.pdf", false, false))
        assertEquals("https://example.com/app.js", UrlTools.resolve("example.com/app.js", false, false))
        assertEquals("https://duckduckgo.com/?q=what+is+2%2B2&kae=d", UrlTools.resolve("what is 2+2", false, true))
        assertTrue(UrlTools.resolve("cats", true, false).endsWith("&kd=1&kg=p&k1=-1"))
        assertEquals("", UrlTools.resolve("   ", false, false))
    }

    @Test
    fun searchTermsAreReadBackFromResultsAddress() {
        assertEquals("best pizza", UrlTools.searchQuery("https://duckduckgo.com/?q=best+pizza&kae=d"))
        assertNull(UrlTools.searchQuery("https://example.com/?q=hello"))
        assertNull(UrlTools.searchQuery("https://duckduckgo.com/"))
    }

    @Test
    fun trackingTagsAreStrippedAndEverythingElseKept() {
        assertEquals(
            "https://shop.example/item?id=42&size=m#reviews",
            UrlTools.stripTracking("https://shop.example/item?utm_source=news&id=42&fbclid=abc&size=m&gclid=zz#reviews"),
        )
        assertEquals("https://a.example/p", UrlTools.stripTracking("https://a.example/p?utm_campaign=x"))
        val untouched = "https://a.example/p?q=1&page=2"
        assertEquals(untouched, UrlTools.stripTracking(untouched))
        assertEquals("https://a.example/p", UrlTools.stripTracking("https://a.example/p"))
    }

    @Test
    fun hostsAreShownWithoutWww() {
        assertEquals("example.com", UrlTools.displayHost("https://www.example.com/path?x=1"))
        assertEquals("m.youtube.com", UrlTools.host("https://m.youtube.com/watch?v=1"))
    }

    // ── Ad blocker ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun adBlockerBlocksListedHostsAndTheirSubdomainsOnly() {
        val settings = Settings(context)
        val blocker = AdBlocker(context, settings)
        blocker.load()
        assertTrue("built-in list loaded", blocker.ruleCount > 250)
        val page = "news.example"
        fun blocked(url: String) = blocker.shouldBlock(android.net.Uri.parse(url), page)
        assertTrue(blocked("https://doubleclick.net/ad.js"))
        assertTrue(blocked("https://stats.g.doubleclick.net/collect"))
        assertTrue(blocked("https://cdn.example.org/static/adsbygoogle.js"))
        assertFalse(blocked("https://notdoubleclick.net/x.js"))
        assertFalse(blocked("https://news.example/app.js"))
        assertFalse(blocked("https://cdn.jsdelivr.net/npm/vue"))

        blocker.setAllowlisted("news.example", true)
        assertFalse("paused on this site", blocked("https://doubleclick.net/ad.js"))
        blocker.setAllowlisted("news.example", false)
        settings.adBlock = false
        assertFalse("switched off", blocked("https://doubleclick.net/ad.js"))
    }

    @Test
    fun blockListParserAcceptsCommonFormats() {
        val out = HashSet<String>()
        AdBlocker.parseInto(
            sequenceOf(
                "# comment", "", "ads.example.com", "0.0.0.0 tracker.example.net", "127.0.0.1 localhost",
                "||pixel.example.org^", "not a host", "! adblock comment", "UPPER.Example.COM",
            ),
            out,
        )
        assertEquals(setOf("ads.example.com", "tracker.example.net", "pixel.example.org", "upper.example.com"), out)
    }

    // ── Session saving ─────────────────────────────────────────────────────────────────────────

    @Test
    fun sessionIsOfferedOnceAndSurvivesBrowsingBeforeTheAnswer() {
        val store = SessionStore(context)
        assertNull("nothing saved yet", store.takePrevious())

        store.save(SavedSession(listOf(SavedTab("https://a.example/", "A"), SavedTab("", ""), SavedTab("https://b.example/", "B")), 2, 1L))
        val offered = store.takePrevious()
        assertNotNull(offered)
        assertEquals(listOf("https://a.example/", "https://b.example/"), offered!!.realTabs.map { it.url })
        assertEquals(2, offered.active)

        // The user ignores the prompt, opens only the start page, and the app is killed again.
        store.save(SavedSession(listOf(SavedTab("", "")), 0, 2L))
        assertEquals("still on offer", 2, store.takePrevious()!!.realTabs.size)

        store.discardPrevious()
        assertNull(store.takePrevious())
    }

    @Test
    fun sessionWithOnlyStartPagesIsNotOffered() {
        val store = SessionStore(context)
        store.save(SavedSession(listOf(SavedTab("", ""), SavedTab("", "")), 0, 1L))
        assertNull(store.takePrevious())
    }

    // ── History ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun historyRecordsSearchesAndClearsByTime() {
        val history = HistoryStore(context)
        history.record("https://a.example/", "Alpha", now = 1_000)
        history.record("https://a.example/", "Alpha again", now = 2_000)
        history.record("https://b.example/page", "Bravo", now = 3_000)
        history.record("about:blank", "ignored", now = 4_000)
        assertEquals("a reload doesn't add a second entry", 2, history.count())
        assertEquals("Alpha again", history.query("alpha").single().title)
        assertEquals(listOf("https://b.example/page", "https://a.example/"), history.query().map { it.url })

        history.updateTitle("https://b.example/page", "Bravo 2")
        assertEquals("Bravo 2", history.query("bravo").single().title)

        history.deleteSince(2_500)
        assertEquals(listOf("https://a.example/"), history.query().map { it.url })
        history.delete(history.query().single().id)
        assertEquals(0, history.count())
    }

    // ── Pins ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun pinsCanBeAddedEditedAndRemoved() {
        val pins = PinsStore(context)
        val defaults = pins.pins.size
        assertTrue(defaults > 0)
        pins.add("My site", "https://my.example/")
        assertTrue(pins.isPinned("https://my.example"))
        pins.add("Duplicate", "https://my.example/")
        assertEquals(defaults + 1, pins.pins.size)

        val id = pins.pins.last().id
        pins.update(id, "Renamed", "https://my.example/")
        assertEquals("Renamed", PinsStore.readAll(context).last().title)
        assertFalse(pins.toggle("x", "https://my.example/"))
        assertEquals(defaults, PinsStore(context).pins.size)
    }

    // ── Markdown ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun markdownIsSplitIntoBlocksEvenWhenUnfinished() {
        val blocks = parseMarkdown("# Title\n\nSome **bold** text\nsecond line\n\n- one\n- two\n1. first\n\n```kotlin\nval x = 1\n```\n> quote\n---\n```\nunclosed")
        assertEquals(MdBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MdBlock.Paragraph("Some **bold** text\nsecond line"), blocks[1])
        assertEquals(MdBlock.ListItem(0, "•", "one"), blocks[2])
        assertEquals(MdBlock.ListItem(0, "•", "two"), blocks[3])
        assertEquals(MdBlock.ListItem(0, "1.", "first"), blocks[4])
        assertEquals(MdBlock.Code("val x = 1"), blocks[5])
        assertEquals(MdBlock.Quote("quote"), blocks[6])
        assertEquals(MdBlock.Rule, blocks[7])
        assertEquals(MdBlock.Code("unclosed"), blocks[8])
    }
}
