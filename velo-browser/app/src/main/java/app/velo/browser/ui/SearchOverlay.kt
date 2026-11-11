package app.velo.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velo.browser.Browser
import app.velo.browser.data.HistoryEntry
import app.velo.browser.data.Settings
import app.velo.browser.data.UrlTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Full-screen typing mode for the address bar, with history matches and search suggestions. */
@Composable
fun SearchOverlay(b: Browser) {
    AnimatedVisibility(b.searchOpen, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
        val c = Velo.colors
        val s = b.settings
        val barOnTop = s.barPosition == Settings.BAR_TOP
        val seed = remember { b.searchSeed }
        var field by remember { mutableStateOf(TextFieldValue(seed, TextRange(0, seed.length))) }
        var historyHits by remember { mutableStateOf(emptyList<HistoryEntry>()) }
        var suggestions by remember { mutableStateOf(emptyList<String>()) }
        val focus = remember { FocusRequester() }
        val text = field.text.trim()

        LaunchedEffect(Unit) { focus.requestFocus() }
        LaunchedEffect(text) {
            historyHits = withContext(Dispatchers.IO) {
                if (text.isEmpty() || text == seed) b.app.history.recentDistinct(6) else b.app.history.suggest(text, 4)
            }
            if (text.isEmpty() || text == seed || s.privateSearch || !s.searchSuggestions || UrlTools.looksLikeUrl(text)) {
                suggestions = emptyList()
            } else {
                delay(170)
                suggestions = withContext(Dispatchers.IO) { b.app.net.suggestions(text) }.take(6)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            val fieldRow: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(Velo.shapes.bar)
                            .background(c.surface)
                            .padding(start = 18.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VIcon(VIcons.Search, tint = c.textDim, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (field.text.isEmpty()) Txt("Search DuckDuckGo or type an address", color = c.textFaint, maxLines = 1)
                            BasicTextField(
                                value = field,
                                onValueChange = { field = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(c.accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go,
                                    autoCorrectEnabled = false,
                                ),
                                keyboardActions = KeyboardActions(onAny = { b.submitSearch(field.text) }),
                            )
                        }
                        if (field.text.isNotEmpty()) {
                            IconBtn(VIcons.Close, onClick = { field = TextFieldValue("") }, tint = c.textDim, size = 40.dp, iconSize = 17.dp)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.height(52.dp).bounceClick { b.closeSearch() }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt("Cancel", size = 15.sp, weight = FontWeight.Medium, color = c.textDim) }
                }
            }

            if (barOnTop) fieldRow()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = !barOnTop,
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                if (text.isNotEmpty() && text != seed) {
                    item(key = "primary") {
                        val isUrl = UrlTools.looksLikeUrl(text)
                        SuggestionRow(
                            icon = if (isUrl) VIcons.Globe else VIcons.Search,
                            title = text,
                            subtitle = if (isUrl) "Open this address" else "Search DuckDuckGo",
                            highlighted = true,
                            onClick = { b.submitSearch(text) },
                        )
                    }
                }
                if (historyHits.isNotEmpty()) {
                    if (text.isEmpty() || text == seed) {
                        item(key = "recent-title") { ListLabel("Recently visited") }
                    }
                    items(historyHits, key = { "h" + it.id }) { entry ->
                        SuggestionRow(
                            icon = VIcons.Clock,
                            title = entry.title.ifBlank { UrlTools.displayHost(entry.url) },
                            subtitle = UrlTools.displayHost(entry.url),
                            onClick = { b.submitSearch(entry.url) },
                            onFill = { field = TextFieldValue(entry.url, TextRange(entry.url.length)) },
                        )
                    }
                }
                items(suggestions, key = { "s$it" }) { suggestion ->
                    SuggestionRow(
                        icon = VIcons.Search,
                        title = suggestion,
                        onClick = { b.submitSearch(suggestion) },
                        onFill = { field = TextFieldValue(suggestion, TextRange(suggestion.length)) },
                    )
                }
            }
            if (!barOnTop) fieldRow()
        }
        BackHandler { b.closeSearch() }
    }
}

@Composable
private fun ListLabel(text: String) {
    Txt(
        text,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
        size = 12.sp, weight = FontWeight.SemiBold, color = Velo.colors.textFaint, letterSpacing = 0.6.sp,
    )
}

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    onFill: (() -> Unit)? = null,
) {
    val c = Velo.colors
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (highlighted) c.accent else c.surface),
            contentAlignment = Alignment.Center,
        ) { VIcon(icon, tint = if (highlighted) c.onAccent else c.textDim, size = 17.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15.sp, weight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            if (subtitle != null) Txt(subtitle, size = 12.5.sp, color = c.textDim, maxLines = 1)
        }
        if (onFill != null) {
            IconBtn(VIcons.ArrowUp, onClick = onFill, tint = c.textFaint, size = 38.dp, iconSize = 16.dp,
                modifier = Modifier.padding(start = 4.dp))
        }
    }
}
