package app.velo.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velo.browser.Browser
import app.velo.browser.Screen
import app.velo.browser.browser.VpnManager
import app.velo.browser.data.Settings
import app.velo.browser.data.UrlTools

// ── Main menu ──────────────────────────────────────────────────────────────────────────────────

@Composable
fun MenuSheet(b: Browser) {
    VeloSheet(b.menuOpen, { b.menuOpen = false }) {
        val c = Velo.colors
        val s = b.settings
        val tab = b.tabs.active
        val home = tab == null || tab.showHome
        val vpn = b.app.vpn
        val close = { b.menuOpen = false }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
            if (tab != null && !home) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(tab.favicon, tab.displayTitle, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(tab.displayTitle, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                        val paused = b.app.adBlocker.isAllowlisted(tab.pageHost)
                        Txt(
                            when {
                                !s.adBlock -> UrlTools.displayHost(tab.url)
                                paused -> "Ad blocker paused on this site"
                                tab.blocked == 0 -> "${UrlTools.displayHost(tab.url)} · nothing blocked"
                                else -> "${UrlTools.displayHost(tab.url)} · ${tab.blocked} blocked"
                            },
                            size = 12.5.sp, color = c.textDim, maxLines = 1,
                        )
                    }
                    if (s.adBlock && tab.pageHost.isNotEmpty()) {
                        val paused = b.app.adBlocker.isAllowlisted(tab.pageHost)
                        SoftButton(
                            if (paused) "Resume" else "Pause ads",
                            onClick = {
                                b.app.adBlocker.setAllowlisted(tab.pageHost, !paused)
                                b.tabs.reload()
                                close()
                            },
                            height = 36.dp, background = c.surface,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                Modifier.fillMaxWidth().clip(Velo.shapes.card).background(c.surface).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconBtn(VIcons.ArrowLeft, enabled = tab?.canGoBack == true, onClick = { b.onBack(); close() }, size = 48.dp)
                IconBtn(VIcons.ArrowRight, enabled = tab?.canGoForward == true, onClick = { b.tabs.goForward(); close() }, size = 48.dp)
                IconBtn(
                    if (tab?.loading == true && !home) VIcons.Close else VIcons.Refresh, enabled = !home,
                    onClick = { if (tab?.loading == true) b.tabs.stop() else b.tabs.reload(); close() }, size = 48.dp,
                )
                IconBtn(VIcons.Home, enabled = !home, onClick = { tab?.let { b.tabs.showHome(it) }; close() }, size = 48.dp)
                IconBtn(VIcons.Share, enabled = !home, onClick = { close(); b.sharePage() }, size = 48.dp)
            }
            Spacer(Modifier.height(12.dp))

            val pinned = tab != null && !home && b.app.pins.isPinned(tab.url)
            val vpnOn = s.vpnEnabled
            val tiles = listOf(
                MenuItem(VIcons.Plus, "New tab") { close(); b.newTab() },
                MenuItem(VIcons.Pin, if (pinned) "Unpin" else "Pin page", active = pinned, enabled = !home) { b.togglePin() },
                MenuItem(VIcons.History, "History") { close(); b.screen = Screen.HISTORY },
                MenuItem(VIcons.Download, "Downloads") { close(); b.openDownloads() },
                MenuItem(VIcons.Search, "Find", enabled = !home) { close(); b.findOpen = true },
                MenuItem(VIcons.Monitor, "Desktop site", active = tab?.desktop == true, enabled = !home) {
                    tab?.let { b.tabs.toggleDesktop(it) }
                },
                MenuItem(VIcons.Phone, "Add to Home", enabled = !home) { close(); b.addToHomeScreen() },
                MenuItem(VIcons.Settings, "Settings") { b.openSettings() },
                MenuItem(VIcons.Ban, "Ad blocker", active = s.adBlock) {
                    s.adBlock = !s.adBlock
                    if (!home) b.tabs.reload()
                },
                MenuItem(
                    VIcons.Shield, "VPN", active = vpnOn,
                    warning = vpnOn && vpn.state == VpnManager.State.FAILED,
                ) {
                    if (vpn.servers.isEmpty()) b.openSettings("vpn") else vpn.setEnabled(!vpnOn)
                },
                MenuItem(VIcons.EyeOff, "Private search", active = s.privateSearch) {
                    s.privateSearch = !s.privateSearch
                    b.tabs.applySettingsToAll()
                },
                MenuItem(if (b.dark) VIcons.Sun else VIcons.Moon, if (b.dark) "Light mode" else "Dark mode") {
                    s.themeMode = if (b.dark) Settings.THEME_LIGHT else Settings.THEME_DARK
                },
            )
            tiles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { MenuTile(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

private class MenuItem(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val warning: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun MenuTile(item: MenuItem, modifier: Modifier) {
    val c = Velo.colors
    val background by animateColorAsState(
        when {
            item.warning -> c.danger.copy(alpha = 0.16f)
            item.active -> c.accent
            else -> c.surface
        },
        tween(200), label = "tile",
    )
    val content by animateColorAsState(
        when {
            item.warning -> c.danger
            item.active -> c.onAccent
            else -> c.text
        },
        tween(200), label = "tileContent",
    )
    Column(
        modifier
            .height(78.dp)
            .clip(Velo.shapes.tile)
            .background(background)
            .bounceClick(enabled = item.enabled, pressedScale = 0.92f, onClick = item.onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val alpha = if (item.enabled) 1f else 0.3f
        VIcon(item.icon, tint = content.copy(alpha = content.alpha * alpha), size = 21.dp)
        Spacer(Modifier.height(8.dp))
        Txt(
            item.label, size = 11.5.sp, weight = FontWeight.Medium, color = content.copy(alpha = content.alpha * alpha),
            maxLines = 1, align = TextAlign.Center,
        )
    }
}

// ── Long-press on a link or picture ────────────────────────────────────────────────────────────

@Composable
fun LinkMenuSheet(b: Browser) {
    val target = b.linkMenu
    val shown = rememberLast(target)
    VeloSheet(target != null, { b.linkMenu = null }) {
        val c = Velo.colors
        val t = shown ?: return@VeloSheet
        val close = { b.linkMenu = null }
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
            Txt(
                t.link ?: t.image.orEmpty(),
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                size = 13.sp, color = c.textDim, maxLines = 2, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Card {
                t.link?.let { link ->
                    SettingRow("Open in new tab", icon = VIcons.Plus, onClick = { close(); b.openInNewTab(link) })
                    Hairline()
                    SettingRow("Open in background", icon = VIcons.Tabs, onClick = { close(); b.openInNewTab(link, background = true) })
                    Hairline()
                    SettingRow("Copy link", icon = VIcons.Copy, onClick = { close(); b.copy(link, "Link copied") })
                    Hairline()
                    SettingRow("Share link", icon = VIcons.Share, onClick = { close(); b.shareText(link) })
                }
                t.image?.let { image ->
                    if (t.link != null) Hairline()
                    SettingRow("Open picture in new tab", icon = VIcons.Image, onClick = { close(); b.openInNewTab(image) })
                    if (image.startsWith("http")) {
                        Hairline()
                        SettingRow("Save picture", icon = VIcons.Download, onClick = { close(); b.downloadImage(image) })
                    }
                }
            }
        }
    }
}

// ── Restore prompt ─────────────────────────────────────────────────────────────────────────────

/** Asked once when Velo opens after being closed with tabs open. */
@Composable
fun RestoreSheet(b: Browser) {
    val offer = b.restoreOffer
    val shown = rememberLast(offer)
    VeloSheet(offer != null, { b.declineRestore() }, dismissOnOutsideTap = false) {
        val c = Velo.colors
        val session = shown ?: return@VeloSheet
        val tabs = session.realTabs
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    VIcon(VIcons.History, tint = c.accent, size = 22.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Txt("Restore your tabs?", size = 20.sp, weight = FontWeight.SemiBold)
                    Txt(
                        if (tabs.size == 1) "Velo saved the page you had open last time."
                        else "Velo saved the ${tabs.size} tabs you had open last time.",
                        size = 13.5.sp, color = c.textDim,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card {
                tabs.take(4).forEachIndexed { i, saved ->
                    if (i > 0) Hairline()
                    val host = UrlTools.host(saved.url)
                    val icon = remember(host) { b.app.favicons.get(host) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(icon, saved.title.ifBlank { host }, size = 28.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(saved.title.ifBlank { UrlTools.displayHost(saved.url) }, size = 14.5.sp, weight = FontWeight.Medium, maxLines = 1)
                            Txt(UrlTools.displayHost(saved.url), size = 12.sp, color = c.textDim, maxLines = 1)
                        }
                    }
                }
                if (tabs.size > 4) {
                    Hairline()
                    Txt("and ${tabs.size - 4} more", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), size = 13.sp, color = c.textDim)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Start fresh", { b.declineRestore() }, Modifier.weight(1f))
                PrimaryButton(if (tabs.size == 1) "Restore tab" else "Restore tabs", { b.acceptRestore() }, Modifier.weight(1f))
            }
        }
    }
}

// ── Dialogs ────────────────────────────────────────────────────────────────────────────────────

@Composable
fun BrowserDialogs(b: Browser) {
    val c = Velo.colors

    // Pin editor
    val pin = b.pinEdit
    val shownPin = rememberLast(pin)
    VeloDialog(pin != null, { b.pinEdit = null }) {
        val editing = shownPin ?: return@VeloDialog
        var title by remember(editing) { mutableStateOf(editing.title) }
        var url by remember(editing) { mutableStateOf(editing.url) }
        Txt(if (editing.id == null) "Pin a page" else "Edit pin", size = 20.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        VeloField(title, { title = it }, "Name", Modifier.fillMaxWidth(), imeAction = ImeAction.Next)
        Spacer(Modifier.height(10.dp))
        VeloField(url, { url = it }, "Address, e.g. example.com", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Uri)
        if (editing.id != null) {
            Spacer(Modifier.height(14.dp))
            Txt(
                "Remove from start page",
                Modifier.bounceClick {
                    b.app.pins.removeById(editing.id)
                    b.pinEdit = null
                }.padding(vertical = 6.dp),
                size = 14.sp, weight = FontWeight.Medium, color = c.danger,
            )
        }
        DialogButtons(
            confirm = "Save",
            confirmEnabled = url.isNotBlank(),
            onConfirm = {
                val address = if (UrlTools.looksLikeUrl(url)) UrlTools.resolve(url, privateSearch = false, dark = false) else url.trim()
                if (editing.id == null) b.app.pins.add(title.trim(), address) else b.app.pins.update(editing.id, title.trim(), address)
                b.pinEdit = null
            },
            onCancel = { b.pinEdit = null },
        )
    }

    // A site asking for location, camera or microphone
    val permission = b.sitePermission
    val shownPermission = rememberLast(permission)
    VeloDialog(permission != null, { b.answerSitePermission(false) }, dismissOnOutsideTap = false) {
        val p = shownPermission ?: return@VeloDialog
        Txt("Allow ${p.site} to use ${p.what}?", size = 19.sp, weight = FontWeight.SemiBold, lineHeight = 25.sp)
        Spacer(Modifier.height(8.dp))
        Txt("Only this visit. Velo asks again next time.", size = 14.sp, color = c.textDim)
        DialogButtons("Allow", { b.answerSitePermission(true) }, cancel = "Block", onCancel = { b.answerSitePermission(false) })
    }

    // A page trying to open another app
    val external = b.externalRequest
    val shownExternal = rememberLast(external)
    VeloDialog(external != null, { b.externalRequest = null }) {
        val e = shownExternal ?: return@VeloDialog
        Txt("Leave Velo?", size = 20.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Txt(e.description, size = 14.5.sp, color = c.textDim, lineHeight = 20.sp)
        DialogButtons("Open", { b.confirmExternal() }, cancel = "Stay", onCancel = { b.externalRequest = null })
    }

    // Download confirmation
    val download = b.downloadRequest
    val shownDownload = rememberLast(download)
    VeloDialog(download != null, { b.downloadRequest = null }) {
        val d = shownDownload ?: return@VeloDialog
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(Velo.shapes.tile).background(c.surface), contentAlignment = Alignment.Center) {
                VIcon(VIcons.File, tint = c.textDim, size = 20.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Txt("Download file?", size = 19.sp, weight = FontWeight.SemiBold)
                Txt(d.fileName, size = 13.5.sp, color = c.textDim, maxLines = 2)
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt("From ${UrlTools.displayHost(d.url)}. It will be saved to your Downloads folder.", size = 13.5.sp, color = c.textDim, lineHeight = 19.sp)
        if (b.app.vpn.isRouting) {
            Spacer(Modifier.height(8.dp))
            Txt(
                "Downloads are fetched by Android's download service, which does not use Velo VPN.",
                size = 13.sp, color = c.danger, lineHeight = 18.sp,
            )
        }
        DialogButtons("Download", { b.confirmDownload() }, onCancel = { b.downloadRequest = null })
    }
}
