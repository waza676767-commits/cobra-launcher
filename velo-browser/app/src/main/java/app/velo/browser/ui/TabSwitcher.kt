package app.velo.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velo.browser.Browser
import app.velo.browser.R
import app.velo.browser.Screen
import app.velo.browser.browser.Tab
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Every open tab as a card. Tap to open, swipe sideways or tap the cross to close. */
@Composable
fun TabSwitcher(b: Browser) {
    val c = Velo.colors
    val tabs = b.tabs.tabs
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Tabs", size = 28.sp, weight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.clip(Velo.shapes.chip).background(c.surface).padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Txt(tabs.size.toString(), size = 13.sp, weight = FontWeight.SemiBold, color = c.textDim) }
            Spacer(Modifier.weight(1f))
            if (tabs.size > 1) {
                SoftButton("Close all", { b.tabs.closeAll() }, height = 38.dp, background = c.surface)
                Spacer(Modifier.width(6.dp))
            }
            IconBtn(VIcons.Close, onClick = { b.screen = Screen.BROWSER })
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabCard(
                    b = b,
                    tab = tab,
                    selected = tab.id == b.tabs.activeId,
                    modifier = Modifier.animateItem(),
                    onOpen = {
                        b.tabs.select(tab.id)
                        b.screen = Screen.BROWSER
                    },
                    onClose = { b.tabs.close(tab.id) },
                )
            }
        }

        PrimaryButton(
            "New tab",
            onClick = { b.newTab() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            icon = VIcons.Plus,
        )
    }
}

@Composable
private fun TabCard(
    b: Browser,
    tab: Tab,
    selected: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Velo.colors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    Column(
        modifier
            .aspectRatio(0.8f)
            .graphicsLayer {
                translationX = offset.value
                alpha = (1f - abs(offset.value) / (size.width * 0.9f)).coerceIn(0f, 1f)
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                onDragStopped = { velocity ->
                    if (abs(offset.value) > 150f || abs(velocity) > 1600f) {
                        val direction = if (offset.value >= 0f) 1f else -1f
                        offset.animateTo(direction * 900f, tween(160))
                        onClose()
                    } else {
                        offset.animateTo(0f, VeloSpring)
                    }
                },
            )
            .clip(Velo.shapes.card)
            .background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline, Velo.shapes.card)
            .bounceClick(pressedScale = 0.96f, onClick = onOpen)
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab.showHome) {
                VIcon(VIcons.Home, tint = c.textDim, size = 16.dp)
            } else {
                SiteIcon(tab.favicon, tab.displayTitle, size = 18.dp)
            }
            Spacer(Modifier.width(8.dp))
            Txt(tab.displayTitle, Modifier.weight(1f), size = 13.sp, weight = FontWeight.Medium, maxLines = 1)
            IconBtn(VIcons.Close, onClick = onClose, tint = c.textDim, size = 38.dp, iconSize = 15.dp)
        }
        Box(
            Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, bottom = 6.dp).clip(Velo.shapes.tile).background(c.bg),
            contentAlignment = Alignment.Center,
        ) {
            val thumbnail = tab.thumbnail
            when {
                tab.showHome -> Image(
                    painter = painterResource(R.drawable.velo_mark),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    colorFilter = ColorFilter.tint(c.textFaint.copy(alpha = 0.5f)),
                )
                thumbnail != null -> Image(
                    bitmap = remember(thumbnail) { thumbnail.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VIcon(if (tab.sleeping) VIcons.Moon else VIcons.Globe, tint = c.textFaint, size = 26.dp)
                    if (tab.sleeping) {
                        Spacer(Modifier.height(8.dp))
                        Txt("Sleeping", size = 12.sp, color = c.textFaint)
                    }
                }
            }
        }
    }
}
