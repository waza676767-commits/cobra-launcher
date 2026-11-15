package app.velo.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velo.data.T
import app.velo.voice.Assistant
import app.velo.voice.Listener
import app.velo.voice.Phase
import app.velo.voice.VeloService

@Composable
fun AssistantScreen() {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val chat by Assistant.chat.collectAsStateWithLifecycle()
    val phase by Assistant.phase.collectAsStateWithLifecycle()
    val partial by Assistant.partial.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(chat.size) { if (chat.isNotEmpty()) list.animateScrollToItem(chat.size - 1) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(T("assistant"), style = Type.title)
        Text(T("ask"), style = Type.sub)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (chat.isEmpty()) item { Bubble(false, T("greet")) }
            items(chat) { Bubble(it.fromUser, it.text) }
            if (partial.isNotBlank()) item { Bubble(true, partial, ghost = true) }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Orb(phase, accent) { VeloService.listenNow(ctx) }
                Spacer(Modifier.height(8.dp))
                Text(when (phase) {
                    Phase.IDLE -> T("tap_talk"); Phase.LISTENING -> T("listening")
                    Phase.THINKING -> T("thinking"); Phase.SPEAKING -> T("speaking")
                }, style = Type.sub)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 92.dp)
                .clip(RoundedCornerShape(50)).background(C.glassTop).border(1.dp, C.stroke, RoundedCornerShape(50))
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val send = {
                val t = input.trim()
                if (t.isNotEmpty()) { input = ""; Assistant.handle(ctx, t, speak = true) }
            }
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text(T("type_here"), style = Type.body.copy(color = C.faint))
                BasicTextField(input, { input = it }, textStyle = Type.body, cursorBrush = SolidColor(accent),
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(accent).clickable { send() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Bubble(user: Boolean, text: String, ghost: Boolean = false) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(22.dp, 22.dp, if (user) 6.dp else 22.dp, if (user) 22.dp else 6.dp))
                .background(if (user) accent.copy(alpha = if (ghost) 0.10f else 0.18f) else C.glassTop)
                .border(1.dp, if (user) accent.copy(alpha = 0.3f) else C.stroke,
                    RoundedCornerShape(22.dp, 22.dp, if (user) 6.dp else 22.dp, if (user) 22.dp else 6.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) { Text(text, style = Type.body.copy(color = if (ghost) C.sub else C.text)) }
    }
}

/** The Velo orb. It only animates while Velo is active, so it costs nothing when idle. */
@Composable
private fun Orb(phase: Phase, accent: Color, onClick: () -> Unit) {
    val level by Listener.level.collectAsStateWithLifecycle()
    val active = phase != Phase.IDLE
    val pulse = if (active) {
        val t = rememberInfiniteTransition(label = "orb")
        t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(if (phase == Phase.THINKING) 600 else 1100), RepeatMode.Reverse), label = "p").value
    } else 1f
    val boost by animateFloatAsState(if (phase == Phase.LISTENING) level else 0f, label = "lvl")
    Box(Modifier.size(132.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), c, r), r)
            val core = r * 0.58f * pulse * (1f + boost * 0.25f)
            drawCircle(Brush.radialGradient(listOf(Color.White, accent, accent.copy(alpha = 0.6f)), c, core), core)
        }
        Icon(Icons.Rounded.Mic, null, tint = Color.Black.copy(alpha = 0.75f), modifier = Modifier.size(32.dp))
    }
}
