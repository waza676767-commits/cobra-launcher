package app.velo.browser.ui

import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.velo.browser.Browser
import app.velo.browser.Screen
import app.velo.browser.browser.PageError
import app.velo.browser.browser.Tab
import app.velo.browser.browser.VpnManager
import app.velo.browser.data.Settings
import app.velo.browser.data.UrlTools
import kotlinx.coroutines.delay

/** The whole interface: the page, the bar, and every panel that can open over them. */
@Composable
fun BrowserRoot(b: Browser) {
    val s = b.settings
    VeloTheme(dark = b.dark, pureBlack = s.pureBlack, accent = s.accent, style = s.style) {
        val c = Velo.colors
        val windowColor = remember(b.dark, s.pureBlack) {
            veloColors(b.dark, s.pureBlack, Settings.ACCENT_INK).bg.toArgb()
        }
        LaunchedEffect(windowColor) { b.applyTheme(windowColor) }

        // Status-bar icons must contrast with whatever is behind them: the wallpaper on the
        // start page, the theme everywhere else.
        val tab = b.tabs.active
        val onWallpaper = b.screen == Screen.BROWSER && !b.searchOpen &&
            tab?.showHome != false && s.wallpaperType != Settings.WALLPAPER_NONE
        val darkIcons = if (onWallpaper) wallpaperIsLight(s) else !b.dark
        LaunchedEffect(darkIcons) { b.setSystemBarIcons(darkIcons) }

        Box(Modifier.fillMaxSize().background(c.bg)) {
            BackHandler { b.onBack() }

            BrowserLayer(b)

            AnimatedVisibility(
                b.screen == Screen.TABS,
                enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 420f), initialScale = 0.94f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.96f),
            ) { TabSwitcher(b) }

            AnimatedVisibility(
                b.screen == Screen.HISTORY,
                enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeIn(tween(160)),
                exit = slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(160)),
            ) { HistoryScreen(b) }

            AnimatedVisibility(
                b.screen == Screen.SETTINGS,
                enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeIn(tween(160)),
                exit = slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(160)),
            ) { SettingsScreen(b) }

            SearchOverlay(b)
            MenuSheet(b)
            LinkMenuSheet(b)
            AiSheet(b)
            RestoreSheet(b)
            BrowserDialogs(b)
            ToastHost(b)
        }
    }
}

/** The page (or start page) with the address bar docked to the chosen edge. */
@Composable
private fun BrowserLayer(b: Browser) {
    val s = b.settings
    val c = Velo.colors
    val tab = b.tabs.active
    val home = tab == null || tab.showHome
    val top = s.barPosition == Settings.BAR_TOP
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // A bottom bar would sit on top of the keyboard while typing into a page, so it steps aside.
    val barVisible = !b.inFullscreen && (home || (!b.barHidden && !(imeOpen && !top)))
    val barSpace = if (s.floatingBar) 74.dp else 62.dp
    val barInset by animateDpAsState(if (barVisible) barSpace else 0.dp, tween(220, easing = FastOutSlowInEasing), label = "barInset")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(top = if (top) barInset else 0.dp, bottom = if (top) 0.dp else barInset)
        ) {
            WebHost(b)
            val error = tab?.error
            if (error != null && !home) ErrorPage(error, onRetry = { b.tabs.reload() })
        }

        AnimatedVisibility(home, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            StartPage(b, barSpace = barSpace, barOnTop = top)
        }

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                barVisible && !b.findOpen,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { if (top) -it else it } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(200)) { if (top) -it else it } + fadeOut(tween(160)),
            ) { VeloBar(b, tab, home) }
            AnimatedVisibility(
                b.findOpen,
                enter = slideInVertically(tween(200)) { if (top) -it else it } + fadeIn(tween(140)),
                exit = slideOutVertically(tween(180)) { if (top) -it else it } + fadeOut(tween(140)),
            ) { FindBar(b) }
        }
    }
}

@Composable
private fun WebHost(b: Browser) {
    AndroidView(
        factory = { context -> FrameLayout(context).also { b.tabs.setContainer(it) } },
        modifier = Modifier.fillMaxSize(),
        onRelease = { b.tabs.setContainer(null) },
    )
}

// ── Address bar ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun VeloBar(b: Browser, tab: Tab?, home: Boolean) {
    val s = b.settings
    val c = Velo.colors
    val floating = s.floatingBar
    val outer = if (floating) {
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .height(58.dp)
            .shadow(if (c.dark) 0.dp else 14.dp, Velo.shapes.bar, ambientColor = c.text.copy(alpha = 0.25f), spotColor = c.text.copy(alpha = 0.25f))
            .clip(Velo.shapes.bar)
            .background(c.surface)
            .border(1.dp, c.outline.copy(alpha = 0.8f), Velo.shapes.bar)
            .padding(horizontal = 6.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(c.bg)
            .drawBehind {
                val y = if (s.barPosition == Settings.BAR_TOP) size.height else 0f
                drawLine(c.outline, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
            }
            .padding(horizontal = 10.dp)
    }
    Row(outer, verticalAlignment = Alignment.CenterVertically) {
        AddressPill(b, tab, home, floating, Modifier.weight(1f))
        Spacer(Modifier.width(2.dp))
        if (!home && tab != null) IconBtn(VIcons.Home, onClick = { b.tabs.showHome(tab) }, size = 40.dp)
        IconBtn(VIcons.Sparkles, onClick = { b.aiOpen = true }, tint = c.accent, size = 40.dp)
        TabCountButton(b.tabs.tabs.size) { b.openTabs() }
        IconBtn(VIcons.More, onClick = { b.menuOpen = true })
    }
}

@Composable
private fun AddressPill(b: Browser, tab: Tab?, home: Boolean, floating: Boolean, modifier: Modifier) {
    val c = Velo.colors
    val loading = tab != null && tab.loading && !home
    val progress by animateFloatAsState(
        if (tab != null && loading) tab.progress.coerceAtLeast(8) / 100f else 1f,
        tween(260), label = "progress",
    )
    val progressAlpha by animateFloatAsState(if (loading) 1f else 0f, tween(if (loading) 120 else 420), label = "progressAlpha")
    val vpn = b.app.vpn
    Row(
        modifier
            .height(if (floating) 46.dp else 44.dp)
            .clip(Velo.shapes.bar)
            .background(if (floating) androidx.compose.ui.graphics.Color.Transparent else c.surface)
            .drawBehind {
                if (progressAlpha > 0f) {
                    drawRect(c.accent.copy(alpha = 0.13f * progressAlpha), size = Size(size.width * progress, size.height))
                }
            }
            .bounceClick(
                pressedScale = 0.98f,
                onLongClick = { if (!home && tab != null) b.copy(tab.url, "Address copied") },
                onClick = { b.openSearch() },
            )
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val query = if (home || tab == null) null else UrlTools.searchQuery(tab.url)
        val vpnState = vpn.state
        when {
            home || tab == null || query != null -> VIcon(VIcons.Search, tint = c.textDim, size = 17.dp)
            vpnState == VpnManager.State.ON -> VIcon(VIcons.ShieldCheck, tint = c.success, size = 17.dp)
            vpnState == VpnManager.State.FAILED -> VIcon(VIcons.Shield, tint = c.danger, size = 17.dp)
            tab.secure -> VIcon(VIcons.Lock, tint = c.textDim, size = 16.dp)
            else -> VIcon(VIcons.Unlock, tint = c.danger, size = 16.dp)
        }
        Spacer(Modifier.width(10.dp))
        Txt(
            text = when {
                home || tab == null -> "Search or type an address"
                query != null -> query
                else -> UrlTools.displayHost(tab.url)
            },
            modifier = Modifier.weight(1f),
            size = 15.sp,
            weight = if (home) FontWeight.Normal else FontWeight.Medium,
            color = if (home) c.textDim else c.text,
            maxLines = 1,
        )
        if (!home && tab != null) {
            IconBtn(
                if (loading) VIcons.Close else VIcons.Refresh,
                onClick = { if (loading) b.tabs.stop() else b.tabs.reload() },
                tint = c.textDim, size = 38.dp, iconSize = 17.dp,
            )
        }
    }
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    val c = Velo.colors
    Box(
        Modifier
            .size(42.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (count == 1) "1 tab open" else "$count tabs open"
                role = Role.Button
            }
            .bounceClick(pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(23.dp).border(1.9.dp, c.text, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(180)) { if (up) it else -it } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(tween(180)) { if (up) -it else it } + fadeOut(tween(120)))
                },
                label = "tabCount",
            ) { n ->
                Txt(if (n > 99) ":D" else n.toString(), size = 11.sp, weight = FontWeight.Bold, align = TextAlign.Center)
            }
        }
    }
}

// ── Find in page ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun FindBar(b: Browser) {
    val c = Velo.colors
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .height(58.dp)
            .shadow(12.dp, Velo.shapes.bar)
            .clip(Velo.shapes.bar)
            .background(c.surface)
            .border(1.dp, c.outline, Velo.shapes.bar)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VIcon(VIcons.Search, tint = c.textDim, size = 17.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Txt("Find on this page", color = c.textFaint, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    b.tabs.find(it)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                singleLine = true,
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onAny = { b.tabs.findNext(true) }),
            )
        }
        if (query.isNotEmpty()) {
            Txt("${b.tabs.findCurrent}/${b.tabs.findTotal}", size = 13.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 6.dp))
        }
        IconBtn(VIcons.ChevronUp, onClick = { b.tabs.findNext(false) }, size = 38.dp)
        IconBtn(VIcons.ChevronDown, onClick = { b.tabs.findNext(true) }, size = 38.dp)
        IconBtn(VIcons.Close, onClick = { b.closeFind() }, size = 38.dp)
    }
}

// ── Error page ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorPage(error: PageError, onRetry: () -> Unit) {
    val c = Velo.colors
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(76.dp).clip(Velo.shapes.tile).background(c.surface), contentAlignment = Alignment.Center) {
            VIcon(VIcons.Globe, tint = c.textDim, size = 32.dp)
        }
        Spacer(Modifier.height(22.dp))
        Txt(error.title, size = 21.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(UrlTools.displayHost(error.url), size = 14.sp, color = c.textDim, align = TextAlign.Center, maxLines = 1)
        if (error.detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Txt(error.detail, size = 13.sp, color = c.textFaint, align = TextAlign.Center, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(26.dp))
        PrimaryButton("Try again", onRetry, icon = VIcons.Refresh)
    }
}

// ── Toast ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToastHost(b: Browser) {
    val c = Velo.colors
    val current = b.toast
    var shown by remember { mutableStateOf(current) }
    if (current != null) shown = current
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(2400)
            if (b.toast?.id == current.id) b.toast = null
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(bottom = 92.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            current != null,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { it } + fadeIn(tween(120)),
            exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 2 },
        ) {
            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .shadow(10.dp, Velo.shapes.button)
                    .clip(Velo.shapes.button)
                    .background(c.text)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Txt(shown?.text.orEmpty(), size = 14.sp, weight = FontWeight.Medium, color = c.bg, maxLines = 2)
            }
        }
    }
}

/** Fades and lifts its content into place a moment after it first appears. */
@Composable
fun Appear(order: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(40L * order)
        progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 22.dp.toPx()
        }
    ) { content() }
}
