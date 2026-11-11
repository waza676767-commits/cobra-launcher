package app.velo.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velo.browser.BuildConfig
import app.velo.browser.Browser
import app.velo.browser.R
import app.velo.browser.Screen
import app.velo.browser.ai.AiProvider
import app.velo.browser.browser.VpnManager
import app.velo.browser.browser.VpnServer
import app.velo.browser.data.Settings
import app.velo.browser.data.UrlTools
import java.text.NumberFormat
import java.util.UUID
import kotlin.math.roundToInt

private val RamChoices = listOf(0, 300, 400, 500, 750, 1000, 1500, 2000)

@Composable
fun SettingsScreen(b: Browser) {
    val c = Velo.colors
    // The open page lives on the browser so a link straight to a page always lands on it.
    val page = b.settingsPage
    var colorPicker by remember { mutableStateOf(false) }
    var clearData by remember { mutableStateOf(false) }
    var serverEditor by remember { mutableStateOf<VpnServer?>(null) }
    var serverEditorOpen by remember { mutableStateOf(false) }

    BackHandler(page.isNotEmpty()) { b.settingsPage = "" }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            ScreenHeader(
                title = when (page) {
                    "appearance" -> "Appearance"
                    "start" -> "Start page"
                    "privacy" -> "Privacy"
                    "vpn" -> "VPN"
                    "performance" -> "Performance"
                    "general" -> "General"
                    "assistant" -> "Assistant"
                    "about" -> "About"
                    else -> "Settings"
                },
                onBack = { if (page.isEmpty()) b.screen = Screen.BROWSER else b.settingsPage = "" },
            )
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState.isNotEmpty()
                    (slideInHorizontally(tween(240)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(120)))
                },
                label = "settingsPage",
            ) { current ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                    ) {
                        when (current) {
                            "appearance" -> AppearancePage(b) { colorPicker = true }
                            "start" -> StartPagePage(b)
                            "privacy" -> PrivacyPage(b) { clearData = true }
                            "vpn" -> VpnPage(b) { server ->
                                serverEditor = server
                                serverEditorOpen = true
                            }
                            "performance" -> PerformancePage(b)
                            "general" -> GeneralPage(b)
                            "assistant" -> AssistantPage(b)
                            "about" -> AboutPage()
                            else -> Overview(b) { b.settingsPage = it }
                        }
                    }
                }
            }
        }

        ColorPickerDialog(b, colorPicker) { colorPicker = false }
        ClearDataDialog(b, clearData) { clearData = false }
        ServerEditorDialog(b, serverEditorOpen, serverEditor) { serverEditorOpen = false }
    }
}

// ── Building blocks ────────────────────────────────────────────────────────────────────────────

@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(if (title == null) 8.dp else 20.dp))
    if (title != null) {
        Txt(
            title, Modifier.padding(start = 8.dp, bottom = 9.dp),
            size = 13.sp, weight = FontWeight.SemiBold, color = Velo.colors.textDim,
        )
    }
    Card(content = content)
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SettingRow(title, subtitle = subtitle, icon = icon, onClick = onClick) {
        VIcon(VIcons.ChevronRight, tint = Velo.colors.textFaint, size = 17.dp)
    }
}

@Composable
private fun Note(text: String) {
    Txt(
        text, Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp),
        size = 12.5.sp, color = Velo.colors.textFaint, lineHeight = 18.sp,
    )
}

// ── Overview ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Overview(b: Browser, open: (String) -> Unit) {
    val s = b.settings
    val vpn = b.app.vpn
    Section(null) {
        NavRow(VIcons.Droplet, "Appearance", "Theme, button colour, style, toolbar") { open("appearance") }
        Hairline()
        NavRow(VIcons.Image, "Start page", "Wallpaper and widgets") { open("start") }
    }
    Section(null) {
        NavRow(
            VIcons.EyeOff, "Privacy",
            listOf(
                if (s.privateSearch) "Private search on" else "Private search off",
                if (s.adBlock) "ad blocker on" else "ad blocker off",
            ).joinToString(" · "),
        ) { open("privacy") }
        Hairline()
        NavRow(
            VIcons.Shield, "VPN",
            when (vpn.state) {
                VpnManager.State.ON -> "Protected through ${vpn.active?.name.orEmpty()}"
                VpnManager.State.CONNECTING -> "Connecting…"
                VpnManager.State.FAILED -> "Can't reach the server"
                else -> if (vpn.servers.isEmpty()) "Not set up" else "Off"
            },
        ) { open("vpn") }
    }
    Section(null) {
        NavRow(
            VIcons.Cpu, "Performance",
            "Memory limit ${ramLabel(s.ramLimitMb, b.memoryLimitMb)}" +
                if (b.memoryUsedMb > 0) " · using about ${b.memoryUsedMb} MB" else "",
        ) { open("performance") }
        Hairline()
        NavRow(
            VIcons.Sparkles, "Assistant",
            AiProvider.entries.filter { b.ai.mode(it).isNotEmpty() }.joinToString(", ") { it.label }
                .ifEmpty { "Claude, ChatGPT and Gemini with your own account" },
        ) { open("assistant") }
        Hairline()
        NavRow(VIcons.Globe, "General", "Search, startup, history, default browser") { open("general") }
    }
    Section(null) {
        NavRow(VIcons.Info, "About", "Velo ${BuildConfig.VERSION_NAME}") { open("about") }
    }
}

private fun ramLabel(setting: Int, effective: Int): String =
    if (setting == 0) "Auto ($effective MB)" else if (setting >= 1000) "${setting / 1000f} GB".replace(".0 ", " ") else "$setting MB"

// ── Appearance ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppearancePage(b: Browser, openColorPicker: () -> Unit) {
    val s = b.settings
    val c = Velo.colors
    Section("Theme") {
        Segmented(
            listOf("System", "Light", "Dark"), s.themeMode, { s.themeMode = it },
            Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
        )
        ToggleRow(
            "Pure black", s.pureBlack, { s.pureBlack = it },
            subtitle = "True black in dark mode. Looks great on OLED screens.",
            icon = VIcons.Moon, enabled = b.dark,
        )
    }

    Section("Button colour") {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AccentPresets.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (_, argb) -> Swatch(argb, selected = s.accent == argb) { s.accent = argb } }
                }
            }
        }
        Hairline(Modifier.padding(start = 0.dp))
        val custom = AccentPresets.none { it.second == s.accent }
        SettingRow(
            "Custom colour",
            subtitle = if (custom) "Using your own colour" else "Pick any colour you like",
            icon = VIcons.Droplet, onClick = openColorPicker,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (custom) Color(s.accent) else Brush.sweepGradient(RainbowColors).let { Color.Transparent })
                    .drawBehind { if (!custom) drawCircle(Brush.sweepGradient(RainbowColors)) }
                    .border(1.dp, c.outline, CircleShape)
            )
        }
    }

    Section("Style") {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Round" to Settings.STYLE_ROUND, "Soft" to Settings.STYLE_SOFT, "Sharp" to Settings.STYLE_SHARP)
                .forEach { (label, style) ->
                    val selected = s.style == style
                    val shapes = remember(style) { veloShapes(style) }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(Velo.shapes.tile)
                            .background(c.bg)
                            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline, Velo.shapes.tile)
                            .bounceClick(pressedScale = 0.95f) { s.style = style }
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(width = 64.dp, height = 26.dp).clip(shapes.button).background(c.accent))
                        Spacer(Modifier.height(7.dp))
                        Box(Modifier.size(width = 64.dp, height = 26.dp).clip(shapes.tile).background(c.surfaceHi))
                        Spacer(Modifier.height(12.dp))
                        Txt(label, size = 13.5.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
        }
    }

    Section("Toolbar") {
        Segmented(
            listOf("Bottom", "Top"), s.barPosition, { s.barPosition = it },
            Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
        )
        ToggleRow("Floating bar", s.floatingBar, { s.floatingBar = it }, subtitle = "A rounded bar that sits clear of the screen edge.", icon = VIcons.Layout)
        Hairline()
        ToggleRow(
            "Hide while scrolling", s.hideBarOnScroll,
            {
                s.hideBarOnScroll = it
                if (!it) b.barHidden = false
            },
            subtitle = "The bar slides away as you read and returns when you scroll back.",
            icon = VIcons.ChevronDown,
        )
    }
}

private val RainbowColors = listOf(
    Color(0xFFFF4D4D), Color(0xFFFFB020), Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFF3D7BFF),
    Color(0xFF7C5CFF), Color(0xFFFF4F8B), Color(0xFFFF4D4D),
)

@Composable
private fun Swatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val c = Velo.colors
    val ink = argb == Settings.ACCENT_INK
    Box(
        Modifier
            .size(50.dp)
            .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .drawBehind {
                if (ink) {
                    // Half dark, half light: this choice follows the theme.
                    drawRect(Color(0xFF0B0B0F))
                    drawArc(Color(0xFFF4F4F6), 135f, 180f, true)
                } else {
                    drawRect(Color(argb))
                }
            }
            .border(1.dp, c.outline, CircleShape)
            .bounceClick(pressedScale = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && !ink) {
            VIcon(VIcons.Check, tint = if (Color(argb).let { (0.299f * it.red + 0.587f * it.green + 0.114f * it.blue) > 0.6f }) Color.Black else Color.White, size = 18.dp)
        }
    }
}

// ── Start page ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun StartPagePage(b: Browser) {
    val s = b.settings
    val c = Velo.colors
    Section("Wallpaper") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "none") {
                WallpaperTile("None", selected = s.wallpaperType == Settings.WALLPAPER_NONE, onClick = { s.wallpaperType = Settings.WALLPAPER_NONE }) {
                    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                        VIcon(VIcons.Ban, tint = c.textFaint, size = 20.dp)
                    }
                }
            }
            item(key = "photo") {
                WallpaperTile("Your photo", selected = s.wallpaperType == Settings.WALLPAPER_CUSTOM, onClick = { b.pickWallpaper() }) {
                    Box(Modifier.fillMaxSize().background(c.surfaceHi), contentAlignment = Alignment.Center) {
                        VIcon(VIcons.Image, tint = c.textDim, size = 22.dp)
                    }
                }
            }
            itemsIndexed(WallpaperPresets, key = { _, p -> p.name }) { index, preset ->
                WallpaperTile(
                    preset.name,
                    selected = s.wallpaperType == Settings.WALLPAPER_PRESET && s.wallpaperPreset == index,
                    onClick = {
                        s.wallpaperPreset = index
                        s.wallpaperType = Settings.WALLPAPER_PRESET
                    },
                ) { Canvas(Modifier.fillMaxSize()) { drawWallpaper(preset) } }
            }
        }
        if (s.wallpaperType != Settings.WALLPAPER_NONE) {
            Hairline(Modifier.padding(start = 0.dp))
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row {
                    Txt("Soften", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium)
                    Txt("${(s.wallpaperDim * 100).roundToInt()}%", size = 14.sp, color = c.textDim)
                }
                VeloSlider(s.wallpaperDim / 0.7f, { s.wallpaperDim = it * 0.7f })
                Txt("Fades the wallpaper so text over it is easier to read.", size = 12.5.sp, color = c.textFaint)
            }
        }
    }

    Section("On the start page") {
        ToggleRow("Pinned pages", s.showPins, { s.showPins = it }, subtitle = "Your favourite sites, one tap away.", icon = VIcons.Pin)
        Hairline()
        ToggleRow("Clock", s.showClock, { s.showClock = it }, icon = VIcons.Clock)
        Hairline()
        ToggleRow("Privacy stats", s.showStats, { s.showStats = it }, subtitle = "How many ads and trackers Velo has blocked.", icon = VIcons.ShieldCheck)
        Hairline()
        ToggleRow("Jump back in", s.showRecent, { s.showRecent = it }, subtitle = "The last few pages you visited.", icon = VIcons.History)
    }
    Note("Velo also has widgets for your phone's home screen: a search bar and your pinned pages. Long-press an empty spot on the home screen, choose Widgets, then Velo.")
}

@Composable
private fun WallpaperTile(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = Velo.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.bounceClick(pressedScale = 0.94f, onClick = onClick)) {
        Box(
            Modifier
                .size(width = 76.dp, height = 122.dp)
                .border(2.dp, if (selected) c.accent else Color.Transparent, RoundedCornerShape(18.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, c.outline, RoundedCornerShape(14.dp))
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Txt(label, size = 12.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) c.text else c.textDim, maxLines = 1)
    }
}

// ── Privacy ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyPage(b: Browser, openClearData: () -> Unit) {
    val s = b.settings
    val c = Velo.colors
    val blocker = b.app.adBlocker
    Section(null) {
        ToggleRow(
            "Private search", s.privateSearch,
            {
                s.privateSearch = it
                b.tabs.applySettingsToAll()
            },
            subtitle = "Searches stay out of your history and aren't sent for suggestions. Tracking tags are " +
                "removed from links, third-party cookies are blocked, and sites are told not to sell or share your data.",
            icon = VIcons.EyeOff,
        )
        Hairline()
        ToggleRow(
            "Search suggestions", s.searchSuggestions && !s.privateSearch, { s.searchSuggestions = it },
            subtitle = "Sends what you type to DuckDuckGo to suggest searches.",
            icon = VIcons.Search, enabled = !s.privateSearch,
        )
    }

    Section("Ad blocker") {
        ToggleRow(
            "Block ads and trackers", s.adBlock, { s.adBlock = it },
            subtitle = "${NumberFormat.getIntegerInstance().format(s.adsBlockedTotal)} blocked so far. Pages load faster and use less data.",
            icon = VIcons.Ban,
        )
        Hairline()
        ToggleRow("Hide empty ad spaces", s.adBlockCosmetic, { s.adBlockCosmetic = it }, icon = VIcons.Wand, enabled = s.adBlock)
        Hairline()
        ToggleRow("Block pop-ups", s.blockPopups, { s.blockPopups = it }, subtitle = "Stops pages opening windows you didn't ask for.", icon = VIcons.Tabs)
        Hairline()
        SettingRow(
            "Block list",
            subtitle = "${NumberFormat.getIntegerInstance().format(blocker.ruleCount)} sites" +
                if (s.adListUpdatedAt > 0) " · updated ${ago(s.adListUpdatedAt)}" else " · built-in list",
            icon = VIcons.Download,
        ) {
            SoftButton(if (b.adListUpdating) "Updating…" else "Update", { b.refreshAdList() }, height = 36.dp)
        }
        if (s.adAllowlist.isNotEmpty()) {
            Hairline()
            SettingRow(
                "Paused on ${s.adAllowlist.size} ${if (s.adAllowlist.size == 1) "site" else "sites"}",
                subtitle = s.adAllowlist.sorted().take(3).joinToString(", "),
                icon = VIcons.Globe,
            ) { SoftButton("Reset", { s.adAllowlist = emptySet() }, height = 36.dp) }
        }
    }

    Section("Cookies and data") {
        ToggleRow(
            "Block third-party cookies", s.blockThirdPartyCookies || s.privateSearch,
            {
                s.blockThirdPartyCookies = it
                b.tabs.applySettingsToAll()
            },
            subtitle = "Makes it harder for sites to follow you around the web.",
            icon = VIcons.Cookie, enabled = !s.privateSearch,
        )
        Hairline()
        SettingRow("Clear browsing data", subtitle = "History, cookies, cached files and site storage.", icon = VIcons.Trash, onClick = openClearData, titleColor = c.danger)
    }
}

private fun ago(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 2 -> "just now"
        minutes < 60 -> "$minutes minutes ago"
        minutes < 60 * 24 -> "${minutes / 60} ${if (minutes / 60 == 1L) "hour" else "hours"} ago"
        else -> "${minutes / (60 * 24)} ${if (minutes / (60 * 24) == 1L) "day" else "days"} ago"
    }
}

@Composable
private fun ClearDataDialog(b: Browser, visible: Boolean, onClose: () -> Unit) {
    VeloDialog(visible, onClose) {
        var history by remember { mutableStateOf(true) }
        var cookies by remember { mutableStateOf(true) }
        var cache by remember { mutableStateOf(true) }
        var storage by remember { mutableStateOf(false) }
        Txt("Clear browsing data", size = 20.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Card {
            ToggleRow("History", history, { history = it })
            Hairline(Modifier.padding(start = 0.dp))
            ToggleRow("Cookies", cookies, { cookies = it }, subtitle = "Signs you out of most sites.")
            Hairline(Modifier.padding(start = 0.dp))
            ToggleRow("Cached files", cache, { cache = it })
            Hairline(Modifier.padding(start = 0.dp))
            ToggleRow("Site storage", storage, { storage = it }, subtitle = "Data sites saved on this phone.")
        }
        DialogButtons(
            confirm = "Clear", destructive = true, confirmEnabled = history || cookies || cache || storage,
            onConfirm = {
                if (history) b.app.io.execute { b.app.history.clear() }
                b.tabs.clearData(cookies = cookies, cache = cache, siteData = storage)
                if (cache) b.app.favicons.clear()
                onClose()
                b.notify("Browsing data cleared")
            },
            onCancel = onClose,
        )
    }
}

// ── VPN ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun VpnPage(b: Browser, editServer: (VpnServer?) -> Unit) {
    val s = b.settings
    val c = Velo.colors
    val vpn = b.app.vpn
    val state = vpn.state
    val active = vpn.active

    Spacer(Modifier.height(8.dp))
    Card {
        Column(Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val pulse = rememberInfiniteTransition(label = "vpnPulse")
            val wave by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "wave")
            val busy = state == VpnManager.State.CONNECTING || state == VpnManager.State.ON
            val fill by animateColorAsState(
                when (state) {
                    VpnManager.State.ON -> c.accent
                    VpnManager.State.CONNECTING -> c.accent.copy(alpha = 0.55f)
                    VpnManager.State.FAILED -> c.danger
                    else -> c.surfaceHi
                },
                tween(280), label = "vpnFill",
            )
            val ring = c.accent
            Box(
                Modifier
                    .size(132.dp)
                    .drawBehind {
                        if (busy) {
                            drawCircle(ring.copy(alpha = 0.28f * (1f - wave)), radius = size.minDimension / 2 * (0.72f + 0.28f * wave))
                        }
                    }
                    .padding(18.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .bounceClick(pressedScale = 0.92f) {
                        if (vpn.servers.isEmpty()) editServer(null) else vpn.setEnabled(!s.vpnEnabled)
                    },
                contentAlignment = Alignment.Center,
            ) {
                VIcon(
                    VIcons.Power,
                    tint = when (state) {
                        VpnManager.State.ON, VpnManager.State.CONNECTING -> c.onAccent
                        VpnManager.State.FAILED -> Color.White
                        else -> c.textDim
                    },
                    size = 38.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Txt(
                when (state) {
                    VpnManager.State.ON -> "Protected"
                    VpnManager.State.CONNECTING -> "Connecting…"
                    VpnManager.State.FAILED -> "Can't connect"
                    VpnManager.State.UNSUPPORTED -> "Not available"
                    else -> "VPN is off"
                },
                size = 22.sp, weight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Txt(
                when {
                    vpn.detail.isNotEmpty() -> vpn.detail
                    state == VpnManager.State.ON && active != null -> "Sites see ${active.name}'s address, not yours."
                    vpn.servers.isEmpty() -> "Add a server below to get started."
                    active != null -> "Tap to route Velo through ${active.name}."
                    else -> ""
                },
                size = 14.sp, color = if (state == VpnManager.State.FAILED) c.danger else c.textDim,
                align = TextAlign.Center, lineHeight = 20.sp,
            )
        }
    }
    Note(
        "Velo VPN sends everything this browser loads through a server you choose, so sites see that server's " +
            "address instead of yours. It covers Velo only, not your other apps. Velo doesn't come with servers: " +
            "add one from your VPN or proxy provider, or use the free Tor network through the Orbot app."
    )

    Section("Servers") {
        vpn.servers.forEachIndexed { i, server ->
            if (i > 0) Hairline()
            val selected = server.id == active?.id
            Row(
                Modifier.fillMaxWidth().bounceClick(pressedScale = 0.985f) { vpn.select(server.id) }.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).border(2.dp, if (selected) c.accent else c.textFaint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.accent)) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Txt(server.name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                    Txt("${typeLabel(server.type)} · ${server.host}:${server.port}", size = 12.5.sp, color = c.textDim, maxLines = 1)
                }
                IconBtn(VIcons.Edit, onClick = { editServer(server) }, tint = c.textDim, iconSize = 17.dp)
            }
        }
        if (vpn.servers.isNotEmpty()) Hairline()
        SettingRow("Add a server", subtitle = "HTTP, HTTPS or SOCKS5 details from your provider.", icon = VIcons.Plus, onClick = { editServer(null) })
        if (vpn.servers.none { it.host == "127.0.0.1" && it.port == 9050 }) {
            Hairline()
            SettingRow(
                "Use Tor through Orbot",
                subtitle = "Free. Install the Orbot app and start it first.",
                icon = VIcons.Globe,
                onClick = {
                    vpn.saveServer(VpnServer(UUID.randomUUID().toString(), "Tor (Orbot)", "socks", "127.0.0.1", 9050), null)
                    b.notify("Tor added. Start Orbot, then turn the VPN on")
                },
            )
        }
    }

    Section("Protection") {
        ToggleRow(
            "Block WebRTC leaks", s.vpnWebRtcGuard,
            {
                s.vpnWebRtcGuard = it
                b.tabs.applySettingsToAll()
            },
            subtitle = "Stops sites finding your real address through video-call features. Calls in the browser won't work while the VPN is on.",
            icon = VIcons.ShieldCheck,
        )
        Hairline()
        SettingRow(
            "Check what sites see",
            subtitle = "Opens an address lookup in a new tab.",
            icon = VIcons.External,
            onClick = {
                b.screen = Screen.BROWSER
                b.openInNewTab(UrlTools.searchUrl("what is my ip address", s.privateSearch, b.dark))
            },
        )
    }
}

private fun typeLabel(type: String) = when (type) {
    "socks" -> "SOCKS5"
    "https" -> "HTTPS"
    else -> "HTTP"
}

@Composable
private fun ServerEditorDialog(b: Browser, visible: Boolean, server: VpnServer?, onClose: () -> Unit) {
    val c = Velo.colors
    VeloDialog(visible, onClose) {
        val types = listOf("http", "https", "socks")
        var name by remember(server, visible) { mutableStateOf(server?.name.orEmpty()) }
        var type by remember(server, visible) { mutableStateOf(server?.type ?: "socks") }
        var host by remember(server, visible) { mutableStateOf(server?.host.orEmpty()) }
        var port by remember(server, visible) { mutableStateOf(server?.port?.toString().orEmpty()) }
        var user by remember(server, visible) { mutableStateOf(server?.user.orEmpty()) }
        var password by remember(server, visible) { mutableStateOf("") }
        val portNumber = port.toIntOrNull()
        val valid = host.isNotBlank() && !host.contains(' ') && portNumber != null && portNumber in 1..65535

        Txt(if (server == null) "Add a server" else "Edit server", size = 20.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Segmented(listOf("HTTP", "HTTPS", "SOCKS5"), types.indexOf(type).coerceAtLeast(0), { type = types[it] })
        Spacer(Modifier.height(10.dp))
        VeloField(name, { name = it }, "Name (optional)", Modifier.fillMaxWidth(), imeAction = ImeAction.Next)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VeloField(host, { host = it.trim() }, "Server address", Modifier.weight(1f), keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
            VeloField(port, { port = it.filter(Char::isDigit).take(5) }, "Port", Modifier.width(96.dp), keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        }
        if (type != "socks") {
            Spacer(Modifier.height(8.dp))
            VeloField(user, { user = it }, "Username (if needed)", Modifier.fillMaxWidth(), imeAction = ImeAction.Next)
            Spacer(Modifier.height(8.dp))
            VeloField(password, { password = it }, if (server != null && server.user.isNotEmpty()) "Password (unchanged)" else "Password (if needed)", Modifier.fillMaxWidth(), password = true)
        } else {
            Spacer(Modifier.height(8.dp))
            Txt("SOCKS5 servers that need a username and password aren't supported by Android's web engine.", size = 12.5.sp, color = c.textFaint, lineHeight = 18.sp)
        }
        if (server != null) {
            Spacer(Modifier.height(12.dp))
            Txt(
                "Delete this server",
                Modifier.bounceClick {
                    b.app.vpn.removeServer(server.id)
                    onClose()
                }.padding(vertical = 6.dp),
                size = 14.sp, weight = FontWeight.Medium, color = c.danger,
            )
        }
        DialogButtons(
            confirm = "Save", confirmEnabled = valid,
            onConfirm = {
                val saved = VpnServer(
                    id = server?.id ?: UUID.randomUUID().toString(),
                    name = name.trim().ifEmpty { host },
                    type = type,
                    host = host,
                    port = portNumber ?: 0,
                    user = if (type == "socks") "" else user.trim(),
                )
                // An empty password field while editing means "keep the one already saved".
                val newPassword = when {
                    type == "socks" -> ""
                    password.isNotEmpty() -> password
                    server == null || saved.user != server.user -> ""
                    else -> null
                }
                b.app.vpn.saveServer(saved, newPassword)
                onClose()
            },
            onCancel = onClose,
        )
    }
}

// ── Performance ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PerformancePage(b: Browser) {
    val s = b.settings
    val c = Velo.colors
    Section("Memory") {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VIcon(VIcons.Cpu, tint = c.textDim, size = 20.dp)
                Spacer(Modifier.width(16.dp))
                Txt("RAM limit", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium)
                Txt(ramLabel(s.ramLimitMb, b.memoryLimitMb), size = 14.5.sp, weight = FontWeight.SemiBold, color = c.text)
            }
            Spacer(Modifier.height(6.dp))
            val index = RamChoices.indexOf(s.ramLimitMb).coerceAtLeast(0)
            VeloSlider(
                index / (RamChoices.size - 1f),
                { s.ramLimitMb = RamChoices[(it * (RamChoices.size - 1)).roundToInt()] },
                steps = RamChoices.size - 1,
                onFinished = { b.checkMemory() },
            )
            Spacer(Modifier.height(4.dp))
            val total = b.tabs.tabs.size
            Txt(
                if (b.memoryUsedMb > 0) "Using about ${b.memoryUsedMb} MB · ${b.tabs.awakeCount.coerceAtMost(total)} of $total ${if (total == 1) "tab" else "tabs"} awake"
                else "Measuring…",
                size = 13.sp, color = c.textDim,
            )
        }
        Hairline()
        SettingRow(
            "Sleep background tabs now",
            subtitle = "Frees their memory. They reload when you open them.",
            icon = VIcons.Moon,
            onClick = {
                b.tabs.sleepAllBackground()
                b.checkMemory()
                b.notify("Background tabs are asleep")
            },
        )
    }
    Note(
        "When Velo goes over the limit it puts the tabs you haven't used for longest to sleep. Android doesn't let " +
            "apps measure web pages exactly, so the figure is an estimate. Auto picks a limit from this phone's " +
            "${(b.deviceMemoryMb / 1024f).roundToInt()} GB of memory."
    )

    Section("Pages") {
        ToggleRow(
            "Dark web pages", s.darkWebPages,
            {
                s.darkWebPages = it
                b.tabs.applySettingsToAll()
            },
            subtitle = "In dark mode, also darken sites that don't have their own dark design.",
            icon = VIcons.Moon,
        )
        Hairline()
        ToggleRow("Desktop site by default", s.desktopSite, { s.desktopSite = it }, subtitle = "Applies to new tabs.", icon = VIcons.Monitor)
        Hairline()
        ToggleRow(
            "JavaScript", s.javaScript,
            {
                s.javaScript = it
                b.tabs.applySettingsToAll()
            },
            subtitle = "Most sites need this to work.",
            icon = VIcons.Zap,
        )
        Hairline()
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VIcon(VIcons.Type, tint = c.textDim, size = 20.dp)
                Spacer(Modifier.width(16.dp))
                Txt("Text size", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium)
                Txt("${s.textZoom}%", size = 14.5.sp, weight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            VeloSlider(
                (s.textZoom - 80) / 70f,
                { s.textZoom = 80 + (it * 7).roundToInt() * 10 },
                steps = 7,
                onFinished = { b.tabs.applySettingsToAll() },
            )
        }
    }
}

// ── General ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun GeneralPage(b: Browser) {
    val s = b.settings
    val c = Velo.colors
    Section("Search") {
        SettingRow("Search engine", subtitle = "DuckDuckGo. It doesn't track your searches.", icon = VIcons.Search)
    }
    Section("When Velo opens") {
        Segmented(
            listOf("Ask me", "Restore tabs", "Start fresh"), s.restoreMode, { s.restoreMode = it },
            Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp),
        )
        Txt(
            when (s.restoreMode) {
                Settings.RESTORE_ALWAYS -> "The tabs you had open come back automatically."
                Settings.RESTORE_NEVER -> "Velo always opens on a clean start page."
                else -> "Velo saves your tabs as you browse. If it gets closed, it asks whether to bring them back."
            },
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp), size = 13.sp, color = c.textDim, lineHeight = 19.sp,
        )
    }
    Section("History") {
        ToggleRow("Save browsing history", s.saveHistory, { s.saveHistory = it }, icon = VIcons.History)
        Hairline()
        NavRow(VIcons.Clock, "Manage history", "Search, delete visits or clear by time") { b.screen = Screen.HISTORY }
    }
    Section("Default browser") {
        NavRow(VIcons.Globe, "Make Velo your default browser", "Opens Android's default apps settings") { b.openDefaultAppsSettings() }
    }
}

// ── Assistant ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AssistantPage(b: Browser) {
    val s = b.settings
    val c = Velo.colors
    val ai = b.ai
    Section("Accounts") {
        AiProvider.entries.forEachIndexed { i, provider ->
            if (i > 0) Hairline()
            val mode = ai.mode(provider)
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(provider.brand)), contentAlignment = Alignment.Center) {
                    Txt(provider.label.take(1), size = 16.sp, weight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(provider.label, size = 15.sp, weight = FontWeight.Medium)
                    Txt(
                        when (mode) {
                            "api" -> "Connected with an API key"
                            "web" -> "Using ${UrlTools.displayHost(provider.webUrl)}"
                            else -> "Not connected"
                        },
                        size = 12.5.sp, color = c.textDim,
                    )
                }
                if (ai.isConnected(provider)) {
                    SoftButton("Remove key", { ai.disconnect(provider) }, height = 36.dp, color = c.danger)
                } else {
                    SoftButton(
                        "Set up",
                        {
                            ai.ensureLoaded()
                            ai.selectProvider(provider)
                            b.screen = Screen.BROWSER
                            b.aiOpen = true
                        },
                        height = 36.dp,
                    )
                }
            }
        }
    }
    Section("Pages") {
        ToggleRow(
            "Let the assistant read the page", s.aiIncludePage, { s.aiIncludePage = it },
            subtitle = "Sends the text of the page you're on with your first question about it. Very long pages are cut to the first 40,000 characters.",
            icon = VIcons.File,
        )
    }
    Note(
        "Each assistant is used with your own account and may charge for use. API keys are stored encrypted on this " +
            "phone and are only sent to the company that issued them. Your messages go straight to that company, " +
            "through Velo VPN if it is on."
    )
}

// ── About ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutPage() {
    val c = Velo.colors
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.velo_wordmark), contentDescription = "Velo",
            modifier = Modifier.size(width = 150.dp, height = 52.dp), colorFilter = ColorFilter.tint(c.text),
        )
        Spacer(Modifier.height(12.dp))
        Txt("Version ${BuildConfig.VERSION_NAME}", size = 14.sp, color = c.textDim)
    }
    Section(null) {
        SettingRow("Search", subtitle = "DuckDuckGo", icon = VIcons.Search)
        Hairline()
        SettingRow("Web engine", subtitle = "Android System WebView. Keep it updated in the Play Store for the latest security fixes.", icon = VIcons.Globe)
        Hairline()
        SettingRow("Ad blocking", subtitle = "Built-in list, plus Peter Lowe's ad and tracking server list when you update.", icon = VIcons.Ban)
    }
}

// ── Colour picker ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColorPickerDialog(b: Browser, visible: Boolean, onClose: () -> Unit) {
    val s = b.settings
    VeloDialog(visible, onClose) {
        val start = remember(visible) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(if (s.accent == Settings.ACCENT_INK) 0xFF3D7BFF.toInt() else s.accent, hsv)
            hsv
        }
        var hue by remember(visible) { mutableFloatStateOf(start[0]) }
        var saturation by remember(visible) { mutableFloatStateOf(start[1].coerceAtLeast(0.2f)) }
        var value by remember(visible) { mutableFloatStateOf(start[2].coerceAtLeast(0.3f)) }
        val color = Color.hsv(hue, saturation, value)
        val onColor = if (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue > 0.6f) Color(0xFF0B0B0F) else Color.White

        Txt("Custom colour", size = 20.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(54.dp).clip(Velo.shapes.button).background(color), contentAlignment = Alignment.Center) {
            Txt("Preview", size = 15.sp, weight = FontWeight.SemiBold, color = onColor)
        }
        Spacer(Modifier.height(18.dp))
        GradientSlider(hue / 360f, { hue = it * 360f }, List(13) { Color.hsv(it * 30f % 360f, 1f, 1f) })
        Spacer(Modifier.height(12.dp))
        GradientSlider(saturation, { saturation = it.coerceAtLeast(0.05f) }, listOf(Color.hsv(hue, 0.05f, value), Color.hsv(hue, 1f, value)))
        Spacer(Modifier.height(12.dp))
        GradientSlider(value, { value = it.coerceAtLeast(0.15f) }, listOf(Color.hsv(hue, saturation, 0.15f), Color.hsv(hue, saturation, 1f)))
        DialogButtons(
            confirm = "Use colour",
            onConfirm = {
                s.accent = color.toArgb()
                onClose()
            },
            onCancel = onClose,
        )
    }
}

@Composable
private fun GradientSlider(value: Float, onChange: (Float) -> Unit, colors: List<Color>) {
    val c = Velo.colors
    val current by rememberUpdatedState(onChange)
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(Unit) {
                fun update(x: Float) {
                    val inset = 14.dp.toPx()
                    current(((x - inset) / (size.width - inset * 2)).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position.x)
                    drag(down.id) { change ->
                        update(change.position.x)
                        change.consume()
                    }
                }
            }
            .drawBehind {
                val inset = 14.dp.toPx()
                val h = 16.dp.toPx()
                val y = size.height / 2
                drawRoundRect(
                    Brush.horizontalGradient(colors, inset, size.width - inset),
                    Offset(inset - h / 2, y - h / 2), Size(size.width - inset * 2 + h, h), CornerRadius(h / 2),
                )
                val x = inset + (size.width - inset * 2) * value.coerceIn(0f, 1f)
                drawCircle(Color.Black.copy(alpha = 0.2f), 14.dp.toPx(), Offset(x, y + 1.dp.toPx()))
                drawCircle(Color.White, 13.dp.toPx(), Offset(x, y))
                drawCircle(c.outline, 13.dp.toPx(), Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            .graphicsLayer { }
    )
}
