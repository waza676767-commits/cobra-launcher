package app.velo.browser.ui

import android.graphics.BitmapFactory
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velo.browser.Browser
import app.velo.browser.PinEdit
import app.velo.browser.R
import app.velo.browser.data.HistoryEntry
import app.velo.browser.data.Pin
import app.velo.browser.data.Settings
import app.velo.browser.data.UrlTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Wallpapers ─────────────────────────────────────────────────────────────────────────────────

/** A soft blob of colour: [x] and [y] are fractions of the canvas, [r] of its longer side. */
class Glow(val color: Long, val x: Float, val y: Float, val r: Float, val alpha: Float = 0.9f)

class WallpaperPreset(val name: String, val light: Boolean, val base: Long, val glows: List<Glow>)

val WallpaperPresets = listOf(
    WallpaperPreset("Aurora", false, 0xFF0B1026, listOf(
        Glow(0xFF3D7BFF, 0.15f, 0.12f, 0.75f), Glow(0xFF7C5CFF, 0.95f, 0.38f, 0.7f), Glow(0xFF12B5A6, 0.25f, 0.95f, 0.75f))),
    WallpaperPreset("Sunset", false, 0xFF2A0E3A, listOf(
        Glow(0xFFFF8A3D, 0.85f, 0.08f, 0.75f), Glow(0xFFFF4F8B, 0.1f, 0.5f, 0.7f), Glow(0xFF7C5CFF, 0.9f, 1f, 0.7f))),
    WallpaperPreset("Ocean", false, 0xFF031B34, listOf(
        Glow(0xFF0EA5E9, 0.1f, 0.18f, 0.7f), Glow(0xFF12B5A6, 0.95f, 0.6f, 0.7f), Glow(0xFF3D7BFF, 0.35f, 1.05f, 0.7f))),
    WallpaperPreset("Forest", false, 0xFF06180F, listOf(
        Glow(0xFF22B866, 0.9f, 0.12f, 0.7f), Glow(0xFFA3E635, 0.05f, 0.6f, 0.55f, 0.55f), Glow(0xFF0F766E, 0.65f, 1.05f, 0.75f))),
    WallpaperPreset("Ember", false, 0xFF1A0A0A, listOf(
        Glow(0xFFF2463D, 0.12f, 0.1f, 0.7f), Glow(0xFFFF8A3D, 0.95f, 0.55f, 0.6f, 0.75f), Glow(0xFF7C2D12, 0.3f, 1f, 0.8f))),
    WallpaperPreset("Graphite", false, 0xFF0D0D10, listOf(
        Glow(0xFF4A4A5A, 0.85f, 0.1f, 0.7f), Glow(0xFF2B2B36, 0.1f, 0.7f, 0.8f))),
    WallpaperPreset("Mist", true, 0xFFF3F5FB, listOf(
        Glow(0xFFC7D7FF, 0.1f, 0.1f, 0.75f), Glow(0xFFE9D5FF, 0.95f, 0.45f, 0.7f), Glow(0xFFCFFAFE, 0.3f, 1f, 0.75f))),
    WallpaperPreset("Peach", true, 0xFFFFF6F0, listOf(
        Glow(0xFFFFD3B8, 0.9f, 0.08f, 0.75f), Glow(0xFFFFC2D9, 0.08f, 0.5f, 0.7f), Glow(0xFFFFE9A8, 0.85f, 1f, 0.7f))),
    WallpaperPreset("Mint", true, 0xFFF1FBF6, listOf(
        Glow(0xFFBBF7D0, 0.12f, 0.1f, 0.75f), Glow(0xFFBAE6FD, 0.95f, 0.5f, 0.7f), Glow(0xFFD9F99D, 0.3f, 1.02f, 0.7f))),
    WallpaperPreset("Paper", true, 0xFFFAFAFA, listOf(
        Glow(0xFFE2E2EA, 0.9f, 0.1f, 0.7f), Glow(0xFFECECF2, 0.1f, 0.8f, 0.8f))),
)

fun DrawScope.drawWallpaper(preset: WallpaperPreset) {
    drawRect(Color(preset.base))
    val longSide = maxOf(size.width, size.height)
    for (g in preset.glows) {
        val center = Offset(size.width * g.x, size.height * g.y)
        val radius = longSide * g.r
        val color = Color(g.color)
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = g.alpha), color.copy(alpha = 0f)), center, radius),
            radius, center,
        )
    }
}

/** True when the wallpaper is pale, so text over it should be dark. */
fun wallpaperIsLight(s: Settings): Boolean = when (s.wallpaperType) {
    Settings.WALLPAPER_PRESET -> WallpaperPresets[s.wallpaperPreset.coerceIn(0, WallpaperPresets.lastIndex)].light
    else -> false
}

@Composable
fun Wallpaper(s: Settings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    when (s.wallpaperType) {
        Settings.WALLPAPER_PRESET -> {
            val preset = WallpaperPresets[s.wallpaperPreset.coerceIn(0, WallpaperPresets.lastIndex)]
            Canvas(modifier.fillMaxSize()) { drawWallpaper(preset) }
        }
        Settings.WALLPAPER_CUSTOM -> {
            val image by produceState<ImageBitmap?>(null, s.wallpaperVersion) {
                value = withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(Browser.wallpaperFile(context).path)?.asImageBitmap() }.getOrNull()
                }
            }
            image?.let {
                Image(it, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        else -> return
    }
    if (s.wallpaperDim > 0f) {
        val light = wallpaperIsLight(s)
        Box(modifier.fillMaxSize().background((if (light) Color.White else Color.Black).copy(alpha = s.wallpaperDim)))
    }
}

// ── Start page ─────────────────────────────────────────────────────────────────────────────────

/** Colours for content sitting directly on the wallpaper (or on the plain background). */
private class OnPage(val text: Color, val dim: Color, val card: Color, val cardBorder: Color)

@Composable
fun StartPage(b: Browser, barSpace: Dp, barOnTop: Boolean) {
    val s = b.settings
    val c = Velo.colors
    val hasWallpaper = s.wallpaperType != Settings.WALLPAPER_NONE
    val on = if (!hasWallpaper) {
        OnPage(c.text, c.textDim, c.surface, Color.Transparent)
    } else if (wallpaperIsLight(s)) {
        OnPage(Color(0xFF0B0B0F), Color(0xFF45454F), Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.7f))
    } else {
        OnPage(Color.White, Color.White.copy(alpha = 0.74f), Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.16f))
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Wallpaper(s)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = if (barOnTop) barSpace else 0.dp, bottom = if (barOnTop) 0.dp else barSpace)
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height((screenHeight * 0.11f).coerceIn(36.dp, 110.dp)))

            Appear(0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.velo_wordmark),
                        contentDescription = "Velo",
                        modifier = Modifier.height(56.dp).width(160.dp),
                        colorFilter = ColorFilter.tint(on.text),
                    )
                    Spacer(Modifier.height(10.dp))
                    Txt(greeting(), size = 15.sp, color = on.dim)
                }
            }

            Spacer(Modifier.height(26.dp))
            Appear(1, Modifier.widthIn(max = 560.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(Velo.shapes.bar)
                        .background(on.card)
                        .border(1.dp, on.cardBorder, Velo.shapes.bar)
                        .bounceClick(pressedScale = 0.975f) { b.openSearch() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VIcon(VIcons.Search, tint = on.dim, size = 19.dp)
                    Spacer(Modifier.width(12.dp))
                    Txt("Search DuckDuckGo", Modifier.weight(1f), size = 16.sp, color = on.dim, maxLines = 1)
                    if (s.privateSearch) VIcon(VIcons.EyeOff, tint = on.dim, size = 17.dp)
                }
            }

            if (s.showPins) {
                Spacer(Modifier.height(28.dp))
                Appear(2, Modifier.widthIn(max = 560.dp)) { PinGrid(b, on) }
            }

            if (s.showClock || s.showStats) {
                Spacer(Modifier.height(22.dp))
                Appear(3, Modifier.widthIn(max = 560.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (s.showClock) ClockCard(on, Modifier.weight(1f))
                        if (s.showStats) StatsCard(b, on, Modifier.weight(1f))
                    }
                }
            }

            if (s.showRecent && s.saveHistory) {
                Appear(4, Modifier.widthIn(max = 560.dp)) { RecentCard(b, on) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
}

@Composable
private fun PinGrid(b: Browser, on: OnPage) {
    val pins = b.app.pins.pins
    val cells: List<Pin?> = pins.toList() + listOf(null)
    val perRow = 4
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        cells.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { pin ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (pin != null) PinTile(b, pin, on) else AddPinTile(b, on)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PinTile(b: Browser, pin: Pin, on: OnPage) {
    val host = UrlTools.host(pin.url)
    b.app.favicons.revision
    val icon = remember(host, b.app.favicons.revision) { b.app.favicons.get(host) }
    Column(
        Modifier
            .width(76.dp)
            .bounceClick(
                pressedScale = 0.9f,
                onLongClick = { b.pinEdit = PinEdit(pin.id, pin.title, pin.url) },
                onClick = { b.tabs.loadInActive(pin.url) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(60.dp).clip(Velo.shapes.tile).background(on.card).border(1.dp, on.cardBorder, Velo.shapes.tile),
            contentAlignment = Alignment.Center,
        ) { SiteIcon(icon, pin.title, size = 30.dp) }
        Spacer(Modifier.height(8.dp))
        Txt(pin.title, size = 12.sp, weight = FontWeight.Medium, color = on.text, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
private fun AddPinTile(b: Browser, on: OnPage) {
    Column(
        Modifier.width(76.dp).bounceClick(pressedScale = 0.9f) { b.pinEdit = PinEdit(null, "", "") },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(60.dp).clip(Velo.shapes.tile).border(1.5.dp, on.dim.copy(alpha = 0.35f), Velo.shapes.tile),
            contentAlignment = Alignment.Center,
        ) { VIcon(VIcons.Plus, tint = on.dim, size = 22.dp) }
        Spacer(Modifier.height(8.dp))
        Txt("Pin a page", size = 12.sp, color = on.dim, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
private fun WidgetCard(on: OnPage, modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .height(104.dp)
            .clip(Velo.shapes.card)
            .background(on.card)
            .border(1.dp, on.cardBorder, Velo.shapes.card)
            .then(if (onClick != null) Modifier.bounceClick(pressedScale = 0.96f, onClick = onClick) else Modifier)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun ClockCard(on: OnPage, modifier: Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val time = remember(now) { DateFormat.getTimeFormat(context).format(now) }
    val date = remember(now) { java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now) }
    WidgetCard(on, modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            VIcon(VIcons.Clock, tint = on.dim, size = 18.dp)
            Column {
                Txt(time, size = 26.sp, weight = FontWeight.SemiBold, color = on.text, maxLines = 1)
                Txt(date, size = 12.5.sp, color = on.dim, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatsCard(b: Browser, on: OnPage, modifier: Modifier) {
    val s = b.settings
    val count = remember(s.adsBlockedTotal) { NumberFormat.getIntegerInstance().format(s.adsBlockedTotal) }
    WidgetCard(on, modifier, onClick = { b.openSettings("privacy") }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            VIcon(if (s.adBlock) VIcons.ShieldCheck else VIcons.Shield, tint = on.dim, size = 18.dp)
            Column {
                Txt(if (s.adBlock) count else "Off", size = 26.sp, weight = FontWeight.SemiBold, color = on.text, maxLines = 1)
                Txt(if (s.adBlock) "ads and trackers blocked" else "ad blocker is off", size = 12.5.sp, color = on.dim, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RecentCard(b: Browser, on: OnPage) {
    val revision = b.app.history.revision
    val recent by produceState(emptyList<HistoryEntry>(), revision) {
        value = withContext(Dispatchers.IO) { b.app.history.recentDistinct(3) }
    }
    if (recent.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(22.dp))
        Txt("Jump back in", Modifier.padding(start = 6.dp, bottom = 10.dp), size = 13.sp, weight = FontWeight.SemiBold, color = on.dim)
        Column(
            Modifier.fillMaxWidth().clip(Velo.shapes.card).background(on.card).border(1.dp, on.cardBorder, Velo.shapes.card)
        ) {
            recent.forEach { entry ->
                val host = UrlTools.host(entry.url)
                val icon = remember(host, b.app.favicons.revision) { b.app.favicons.get(host) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bounceClick(pressedScale = 0.985f) { b.tabs.loadInActive(entry.url) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteIcon(icon, entry.title.ifBlank { host }, size = 30.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(entry.title.ifBlank { UrlTools.displayHost(entry.url) }, size = 14.5.sp, weight = FontWeight.Medium, color = on.text, maxLines = 1)
                        Txt(UrlTools.displayHost(entry.url), size = 12.sp, color = on.dim, maxLines = 1)
                    }
                    VIcon(VIcons.ChevronRight, tint = on.dim.copy(alpha = 0.6f), size = 16.dp)
                }
            }
        }
    }
}
