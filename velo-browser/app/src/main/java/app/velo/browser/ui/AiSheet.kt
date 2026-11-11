package app.velo.browser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.velo.browser.Browser
import app.velo.browser.ai.AiProvider
import app.velo.browser.ai.ChatMessage
import app.velo.browser.ai.PageContext
import app.velo.browser.data.UrlTools

/** How much of a page is handed to the assistant. Longer pages are cut here and say so. */
private const val PAGE_CHARS = 40_000

/** The assistant panel: pick Claude, ChatGPT or Gemini, connect your account, and chat. */
@Composable
fun AiSheet(b: Browser) {
    val ai = b.ai
    var modelPicker by remember { mutableStateOf(false) }
    VeloSheet(b.aiOpen, { b.aiOpen = false }, tall = true) {
        val c = Velo.colors
        LaunchedEffect(Unit) { ai.ensureLoaded() }
        val provider = ai.provider
        val mode = ai.mode(provider)

        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                VIcon(VIcons.Sparkles, tint = c.onAccent, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt("Assistant", size = 18.sp, weight = FontWeight.SemiBold)
                when (mode) {
                    "api" -> Row(
                        Modifier.bounceClick { modelPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(ai.model?.label ?: provider.label, size = 12.5.sp, color = c.textDim, maxLines = 1)
                        VIcon(VIcons.ChevronDown, tint = c.textDim, size = 14.dp, modifier = Modifier.padding(start = 3.dp))
                    }
                    "web" -> Txt("On ${UrlTools.displayHost(provider.webUrl)}", size = 12.5.sp, color = c.textDim, maxLines = 1)
                    else -> Txt("Uses your own account", size = 12.5.sp, color = c.textDim, maxLines = 1)
                }
            }
            if (mode == "api" && ai.messages.isNotEmpty()) IconBtn(VIcons.Edit, onClick = { ai.newChat() }, tint = c.textDim)
            IconBtn(VIcons.Close, onClick = { b.aiOpen = false }, tint = c.textDim)
        }
        Spacer(Modifier.height(12.dp))
        Segmented(
            options = AiProvider.entries.map { it.label },
            selected = AiProvider.entries.indexOf(provider),
            onSelect = { ai.selectProvider(AiProvider.entries[it]) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))

        AnimatedContent(
            targetState = provider to mode,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "assistant",
        ) { (p, m) ->
            when (m) {
                "api" -> ChatPanel(b)
                "web" -> WebPanel(b, p)
                else -> ConnectPanel(b, p)
            }
        }
    }
    // Declared after the sheet so it draws above it.
    ModelPicker(b, modelPicker && b.aiOpen) { modelPicker = false }
}

// ── Connecting an account ──────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectPanel(b: Browser, provider: AiProvider) {
    val ai = b.ai
    val c = Velo.colors
    var key by remember(provider) { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(provider.brand)), contentAlignment = Alignment.Center) {
            Txt(provider.label.take(1), size = 28.sp, weight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Txt("Connect ${provider.label}", size = 22.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Txt(
            "Velo chats with ${provider.label} using your own ${provider.company} account. " +
                "Paste an API key to use Velo's built-in chat.",
            size = 14.5.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(20.dp))
        VeloField(
            key, { key = it; ai.connectError = null }, "API key (${provider.keyHint})", Modifier.fillMaxWidth(),
            leading = VIcons.Key, password = true, imeAction = ImeAction.Go, onDone = { ai.connect(provider, key) },
        )
        ai.connectError?.let {
            Spacer(Modifier.height(10.dp))
            Txt(it, size = 13.5.sp, color = c.danger, align = TextAlign.Center, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            if (ai.connecting) "Checking key…" else "Connect",
            onClick = { ai.connect(provider, key) },
            modifier = Modifier.fillMaxWidth(),
            enabled = key.isNotBlank() && !ai.connecting,
        )
        Spacer(Modifier.height(10.dp))
        SoftButton(
            "Get a key from ${provider.company}",
            onClick = {
                b.aiOpen = false
                b.openInNewTab(provider.keyUrl)
            },
            modifier = Modifier.fillMaxWidth(), icon = VIcons.External, background = c.surface,
        )
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(c.outline))
            Txt("or", Modifier.padding(horizontal = 14.dp), size = 13.sp, color = c.textFaint)
            Box(Modifier.weight(1f).height(1.dp).background(c.outline))
        }
        Spacer(Modifier.height(22.dp))
        SoftButton(
            "Sign in on ${UrlTools.displayHost(provider.webUrl)} instead",
            onClick = { ai.useWeb(provider) },
            modifier = Modifier.fillMaxWidth(), icon = VIcons.Globe, background = c.surface,
        )
        Spacer(Modifier.height(8.dp))
        Txt(
            "No key needed: ${provider.label}'s own site opens in this panel.",
            size = 12.5.sp, color = c.textFaint, align = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VIcon(VIcons.Lock, tint = c.textFaint, size = 13.dp)
            Spacer(Modifier.width(6.dp))
            Txt(
                "Your key is encrypted on this phone and only sent to ${provider.company}.",
                size = 12.sp, color = c.textFaint,
            )
        }
    }
}

// ── The provider's own site ────────────────────────────────────────────────────────────────────

@Composable
private fun WebPanel(b: Browser, provider: AiProvider) {
    val c = Velo.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Showing ${provider.label}'s website", Modifier.weight(1f), size = 13.sp, color = c.textDim, maxLines = 1)
            SoftButton("Use an API key", { b.ai.useApi(provider) }, height = 34.dp, background = c.surface)
        }
        val revision = b.tabs.assistantRevision
        AndroidView(
            factory = { FrameLayout(it) },
            modifier = Modifier.fillMaxSize(),
            update = { frame ->
                frame.tag = revision
                val web = b.tabs.assistantWebView(provider.id, provider.webUrl)
                if (web.parent !== frame) {
                    (web.parent as? ViewGroup)?.removeView(web)
                    frame.removeAllViews()
                    frame.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            onRelease = { it.removeAllViews() },
        )
    }
}

// ── Built-in chat ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatPanel(b: Browser) {
    val ai = b.ai
    val c = Velo.colors
    val s = b.settings
    val listState = rememberLazyListState()
    val tab = b.tabs.active
    val pageAvailable = tab != null && !tab.showHome && tab.url.startsWith("http")
    var input by remember { mutableStateOf("") }
    // The page is sent once per address, not with every message.
    var attachedUrl by remember { mutableStateOf<String?>(null) }

    fun submit(text: String, forcePage: Boolean = false) {
        if (text.isBlank() || ai.streaming) return
        input = ""
        val current = b.tabs.active
        if (current != null && pageAvailable && (forcePage || (s.aiIncludePage && attachedUrl != current.url))) {
            b.tabs.readPageText(current, PAGE_CHARS) { pageText, trimmed ->
                if (pageText.isBlank()) {
                    ai.send(text, null)
                } else {
                    attachedUrl = current.url
                    ai.send(text, PageContext(current.displayTitle, current.url, pageText, trimmed))
                }
            }
        } else {
            ai.send(text, null)
        }
    }

    // Keep the newest text in view while it streams, unless the reader has scrolled away.
    var follow by remember { mutableStateOf(true) }
    val last = ai.messages.lastOrNull()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) follow = !listState.canScrollForward
        }
    }
    LaunchedEffect(ai.messages.size) {
        if (ai.messages.isEmpty()) attachedUrl = null
        follow = true
        if (ai.messages.isNotEmpty()) listState.scrollToItem(ai.messages.size - 1, 1_000_000)
    }
    LaunchedEffect(last?.text?.length) {
        if (follow && ai.messages.isNotEmpty()) listState.scrollToItem(ai.messages.size - 1, 1_000_000)
    }

    val openLinksInVelo = remember(b) {
        object : UriHandler {
            override fun openUri(uri: String) {
                b.aiOpen = false
                b.openInNewTab(uri)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ai.messages.isEmpty()) {
                EmptyChat(ai.provider, pageAvailable) { prompt, withPage -> submit(prompt, withPage) }
            } else {
                CompositionLocalProvider(LocalUriHandler provides openLinksInVelo) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(ai.messages, key = { it.key }) { message ->
                            MessageView(b, message, isLast = message === last)
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp)) {
            if (tab != null && pageAvailable) {
                val on = s.aiIncludePage
                Row(
                    Modifier
                        .padding(bottom = 8.dp)
                        .clip(Velo.shapes.chip)
                        .background(if (on) c.accentSoft else c.surface)
                        .bounceClick { s.aiIncludePage = !on }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VIcon(if (on) VIcons.Check else VIcons.File, tint = if (on) c.accent else c.textDim, size = 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Txt(
                        if (on) "Reading: ${tab.displayTitle}" else "Not reading this page",
                        Modifier.widthIn(max = 240.dp),
                        size = 12.5.sp, weight = FontWeight.Medium, color = if (on) c.text else c.textDim, maxLines = 1,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                VeloField(
                    input, { input = it }, "Message ${ai.provider.label}",
                    Modifier.weight(1f).heightIn(min = 50.dp),
                    singleLine = false, imeAction = ImeAction.Default, background = c.surface,
                )
                Spacer(Modifier.width(8.dp))
                val canSend = input.isNotBlank()
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (ai.streaming || canSend) c.accent else c.surfaceHi)
                        .bounceClick(enabled = ai.streaming || canSend, pressedScale = 0.86f) {
                            if (ai.streaming) ai.stop() else submit(input)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    VIcon(
                        if (ai.streaming) VIcons.Stop else VIcons.ArrowUp,
                        tint = if (ai.streaming || canSend) c.onAccent else c.textFaint,
                        size = 20.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(provider: AiProvider, pageAvailable: Boolean, onPick: (prompt: String, withPage: Boolean) -> Unit) {
    val c = Velo.colors
    val prompts = if (pageAvailable) {
        listOf("Summarize this page", "What are the key points here?", "Explain this page in simple terms")
    } else {
        listOf("Explain something to me simply", "Help me write a message", "Give me ideas for the weekend")
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Txt("Ask ${provider.label}", size = 24.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Txt(
            if (pageAvailable) "About this page, or anything else." else "Anything you like.",
            size = 14.5.sp, color = c.textDim,
        )
        Spacer(Modifier.height(22.dp))
        prompts.forEachIndexed { i, prompt ->
            Appear(i) {
                Row(
                    Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .clip(Velo.shapes.card)
                        .background(c.surface)
                        .bounceClick(pressedScale = 0.97f) { onPick(prompt, pageAvailable) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(prompt, Modifier.weight(1f), size = 15.sp, weight = FontWeight.Medium)
                    VIcon(VIcons.ArrowUp, tint = c.textFaint, size = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun MessageView(b: Browser, message: ChatMessage, isLast: Boolean) {
    val c = Velo.colors
    if (message.fromUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            message.attachment?.let {
                Row(
                    Modifier
                        .padding(bottom = 6.dp)
                        .widthIn(max = 260.dp)
                        .clip(Velo.shapes.chip)
                        .border(1.dp, c.outline, Velo.shapes.chip)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VIcon(VIcons.File, tint = c.textDim, size = 13.dp)
                    Spacer(Modifier.width(6.dp))
                    Txt(it, size = 12.sp, color = c.textDim, maxLines = 1)
                }
            }
            Box(
                Modifier
                    .widthIn(max = 310.dp)
                    .clip(Velo.shapes.bubble)
                    .background(c.accent)
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) { Txt(message.text, size = 15.5.sp, color = c.onAccent, lineHeight = 22.sp) }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            if (message.pending && message.text.isEmpty()) TypingDots()
            if (message.text.isNotEmpty()) SelectionContainer { MarkdownText(message.text) }
            message.notice?.let {
                Txt(it, Modifier.padding(top = 8.dp), size = 13.sp, color = c.textDim, lineHeight = 18.sp)
            }
            message.error?.let {
                Column(
                    Modifier
                        .padding(top = if (message.text.isEmpty()) 0.dp else 10.dp)
                        .fillMaxWidth()
                        .clip(Velo.shapes.card)
                        .background(c.danger.copy(alpha = 0.1f))
                        .padding(16.dp)
                ) {
                    Txt(it, size = 14.sp, color = c.danger, lineHeight = 20.sp)
                    if (isLast) {
                        Spacer(Modifier.height(12.dp))
                        SoftButton("Try again", { b.ai.retry() }, height = 38.dp, icon = VIcons.Refresh, background = c.bg)
                    }
                }
            }
            if (!message.pending && message.text.isNotEmpty()) {
                IconBtn(
                    VIcons.Copy, onClick = { b.copy(message.text) }, tint = c.textFaint, size = 36.dp, iconSize = 15.dp,
                    modifier = Modifier.padding(top = 2.dp).graphicsLayer { translationX = -8.dp.toPx() },
                )
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val c = Velo.colors
    val transition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 1050
                        0f at i * 140
                        1f at i * 140 + 260
                        0f at i * 140 + 520
                    },
                    RepeatMode.Restart,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        translationY = -phase * 5.dp.toPx()
                        alpha = 0.35f + phase * 0.65f
                    }
                    .clip(CircleShape)
                    .background(c.textDim)
            )
        }
    }
}

@Composable
private fun ModelPicker(b: Browser, visible: Boolean, onClose: () -> Unit) {
    val ai = b.ai
    val c = Velo.colors
    VeloDialog(visible, onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("${ai.provider.label} model", Modifier.weight(1f), size = 20.sp, weight = FontWeight.SemiBold)
            IconBtn(VIcons.Refresh, onClick = { ai.refreshModels() }, tint = if (ai.refreshingModels) c.textFaint else c.textDim, enabled = !ai.refreshingModels)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            ai.models.forEach { model ->
                val selected = model.id == ai.model?.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Velo.shapes.field)
                        .background(if (selected) c.accentSoft else Color.Transparent)
                        .bounceClick(pressedScale = 0.98f) {
                            ai.selectModel(model)
                            onClose()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Txt(model.label, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                        if (model.label != model.id) Txt(model.id, size = 12.sp, color = c.textDim, maxLines = 1)
                    }
                    if (selected) VIcon(VIcons.Check, tint = c.accent, size = 18.dp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Txt(
            "Disconnect ${ai.provider.label}",
            Modifier.bounceClick {
                ai.disconnect(ai.provider)
                onClose()
            }.padding(vertical = 8.dp, horizontal = 4.dp),
            size = 14.sp, weight = FontWeight.Medium, color = c.danger,
        )
    }
}
