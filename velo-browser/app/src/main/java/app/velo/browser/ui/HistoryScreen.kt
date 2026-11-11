package app.velo.browser.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velo.browser.Browser
import app.velo.browser.Screen
import app.velo.browser.data.HistoryEntry
import app.velo.browser.data.UrlTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Browsing history: search it, reopen pages, delete single visits or clear a time range. */
@Composable
fun HistoryScreen(b: Browser) {
    val c = Velo.colors
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }
    val revision = b.app.history.revision
    val entries by produceState(emptyList<HistoryEntry>(), filter, revision) {
        value = withContext(Dispatchers.IO) { b.app.history.query(filter, limit = 500) }
    }
    val timeFormat = remember { DateFormat.getTimeFormat(context) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            ScreenHeader("History", onBack = { b.screen = Screen.BROWSER }) {
                if (entries.isNotEmpty() || filter.isNotEmpty()) {
                    SoftButton("Clear…", { clearDialog = true }, height = 38.dp, background = c.surface, color = c.danger)
                    Spacer(Modifier.width(8.dp))
                }
            }
            VeloField(
                filter, { filter = it }, "Search history",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                leading = VIcons.Search, background = c.surface,
                trailing = {
                    if (filter.isNotEmpty()) IconBtn(VIcons.Close, { filter = "" }, tint = c.textDim, size = 36.dp, iconSize = 15.dp)
                },
            )
            if (!b.settings.saveHistory) {
                Txt(
                    "History is turned off, so new visits aren't being saved.",
                    Modifier.padding(horizontal = 22.dp, vertical = 8.dp), size = 13.sp, color = c.textDim,
                )
            }

            if (entries.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(72.dp).clip(Velo.shapes.tile).background(c.surface), contentAlignment = Alignment.Center) {
                        VIcon(VIcons.History, tint = c.textDim, size = 30.dp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Txt(if (filter.isEmpty()) "No history yet" else "Nothing matches", size = 19.sp, weight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Txt(
                        if (filter.isEmpty()) "Pages you visit will show up here." else "Try a different word.",
                        size = 14.sp, color = c.textDim, align = TextAlign.Center,
                    )
                }
            } else {
                val grouped = remember(entries) { entries.groupBy { dayLabel(it.time) } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    grouped.forEach { (day, list) ->
                        item(key = "day-$day") {
                            Txt(
                                day,
                                Modifier.animateItem().padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
                                size = 13.sp, weight = FontWeight.SemiBold, color = c.textDim,
                            )
                        }
                        items(list, key = { it.id }) { entry ->
                            val host = UrlTools.host(entry.url)
                            val icon = remember(host) { b.app.favicons.get(host) }
                            Row(
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .bounceClick(pressedScale = 0.985f) {
                                        b.screen = Screen.BROWSER
                                        b.tabs.loadInActive(entry.url)
                                    }
                                    .padding(start = 20.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SiteIcon(icon, entry.title.ifBlank { host }, size = 34.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Txt(entry.title.ifBlank { UrlTools.displayHost(entry.url) }, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                                    Txt(
                                        "${timeFormat.format(Date(entry.time))} · ${UrlTools.displayHost(entry.url)}",
                                        size = 12.5.sp, color = c.textDim, maxLines = 1,
                                    )
                                }
                                IconBtn(
                                    VIcons.Close,
                                    onClick = { b.app.io.execute { b.app.history.delete(entry.id) } },
                                    tint = c.textFaint, size = 40.dp, iconSize = 15.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

        VeloDialog(clearDialog, { clearDialog = false }) {
            Txt("Clear history", size = 20.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Txt("Choose how far back to clear. This can't be undone.", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
            val now = System.currentTimeMillis()
            val choices = listOf(
                "Last hour" to now - 60L * 60 * 1000,
                "Today" to startOfToday(),
                "Last 7 days" to now - 7L * 24 * 60 * 60 * 1000,
                "Everything" to 0L,
            )
            Card {
                choices.forEachIndexed { i, (label, since) ->
                    if (i > 0) Hairline(Modifier.padding(start = 0.dp))
                    SettingRow(
                        label,
                        titleColor = if (since == 0L) c.danger else c.text,
                        onClick = {
                            clearDialog = false
                            b.app.io.execute { if (since == 0L) b.app.history.clear() else b.app.history.deleteSince(since) }
                            b.notify("History cleared")
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SoftButton("Cancel", { clearDialog = false }, Modifier.fillMaxWidth())
        }
    }
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(time: Long): String {
    val today = startOfToday()
    val day = 24L * 60 * 60 * 1000
    return when {
        time >= today -> "Today"
        time >= today - day -> "Yesterday"
        else -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(time))
    }
}
