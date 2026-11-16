package app.velo.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velo.R
import app.velo.Screen
import app.velo.audio.FindSound
import app.velo.audio.Media
import app.velo.audio.Side
import app.velo.bt.Buds
import app.velo.bt.BudsModel
import app.velo.bt.BudsModels
import app.velo.bt.BudsStyle
import app.velo.data.Binding
import app.velo.data.GestureAction
import app.velo.data.GestureSlot
import app.velo.data.Store
import app.velo.data.T
import app.velo.voice.VeloService
import app.velo.voice.WakeWord

private val pagePad = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp)

@Composable
fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(pagePad),
        content = content
    )
}

@Composable
fun BatteryRing(level: Int?, on: Boolean, modifier: Modifier) {
    val accent = LocalAccent.current
    val col = when { level == null || !on -> C.faint; level <= 15 -> C.danger; else -> accent }
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(level) {
        anim.animateTo((level ?: 0) / 100f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.09f
            val inset = w / 2
            drawArc(Color(0x22FFFFFF), 135f, 270f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            if (level != null) drawArc(col, 135f, 270f * anim.value, false, Offset(inset, inset),
                Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(level?.let { "$it" } ?: "–", style = Type.h.copy(fontSize = 20.sp))
    }
}

@Composable
fun ProgressBar(p: Float) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(accent))
    }
}

// ------------------------------------------------------------------ Devices

@Composable
fun DevicesScreen(go: (Screen) -> Unit) {
    val paired by Buds.paired.collectAsStateWithLifecycle()
    val nearby by Buds.nearby.collectAsStateWithLifecycle()
    val scanning by Buds.scanning.collectAsStateWithLifecycle()
    val connecting by Buds.connecting.collectAsStateWithLifecycle()
    val s by Store.settings.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { Buds.stopScan() } }

    Page {
        Spacer(Modifier.height(24.dp))
        Text(T("devices"), style = Type.title)
        val savedAddrs = s.saved.map { it.address }.toSet()
        val savedList = paired.filter { it.address in savedAddrs }
        val other = paired.filter { it.address !in savedAddrs }
        if (savedList.isNotEmpty()) {
            SectionLabel(T("saved"))
            savedList.forEach { d -> DeviceCard(d.address, d.name, d.model, d.connected, d.battery, connecting == d.address,
                onForget = { Store.forget(d.address) }) { Buds.connect(d.address) } }
        }
        if (other.isNotEmpty()) {
            SectionLabel(T("paired"))
            other.forEach { d -> DeviceCard(d.address, d.name, d.model, d.connected, d.battery, connecting == d.address) { Buds.connect(d.address) } }
        }
        if (paired.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Glass(Modifier.fillMaxWidth()) {
                Text(T("no_buds"), style = Type.h)
                Text(T("no_buds_sub"), style = Type.sub)
                Spacer(Modifier.height(12.dp))
                PillButton("Bluetooth", filled = false, icon = Icons.Rounded.Bluetooth) { Buds.openBluetoothPanel() }
            }
        }

        SectionLabel(T("nearest"))
        Glass(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.NearMe)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(T("nearest"), style = Type.body.copy(fontWeight = FontWeight.SemiBold))
                    Text(if (scanning) T("scanning") else T("nearest_sub"), style = Type.sub)
                }
                PillButton(if (scanning) T("stop") else "Scan", filled = !scanning) {
                    if (scanning) Buds.stopScan() else Buds.startScan()
                }
            }
            nearby.take(12).forEach { n ->
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { Buds.connect(n.address) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(n.rssi)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.name, style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${T(Buds.distanceKey(n.rssi))} · ${n.rssi} dBm", style = Type.sub)
                    }
                    Text(if (n.bonded) T("connect") else "Pair", style = Type.body.copy(color = LocalAccent.current, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
fun SignalBars(rssi: Int) {
    val accent = LocalAccent.current
    val bars = when { rssi >= -50 -> 4; rssi >= -65 -> 3; rssi >= -80 -> 2; else -> 1 }
    Row(Modifier.size(28.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) Box(Modifier.width(4.dp).height((6 + i * 5).dp).clip(RoundedCornerShape(2.dp))
            .background(if (i <= bars) accent else Color(0x26FFFFFF)))
    }
}

@Composable
fun DeviceCard(address: String, name: String, model: BudsModel, connected: Boolean, battery: Int?, connecting: Boolean,
               onForget: (() -> Unit)? = null, onConnect: () -> Unit) {
    val accent = LocalAccent.current
    Glass(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x0FFFFFFF))) {
                BudsArt(model.style, accent, Modifier.fillMaxSize().padding(6.dp), glowing = false, address = address, name = name)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = Type.h, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (connected) T("connected") else T("disconnected")) + (battery?.let { " · $it%" } ?: ""),
                    style = Type.sub.copy(color = if (connected) accent else C.sub)
                )
                if (onForget != null && !connected) Text(T("forget"), style = Type.sub.copy(color = C.faint),
                    modifier = Modifier.clickable(onClick = onForget).padding(top = 4.dp))
            }
            if (!connected) PillButton(if (connecting) "…" else T("connect"), filled = false, onClick = onConnect)
        }
    }
}

// ------------------------------------------------------------------ Find

@Composable
fun FindScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val buds by Buds.current.collectAsStateWithLifecycle()
    val playing by FindSound.playing.collectAsStateWithLifecycle()
    var side by remember { mutableStateOf(1) }
    DisposableEffect(Unit) { onDispose { FindSound.stop(ctx) } }
    Page {
        SubHeader(T("find"), T("find_sub"), onBack)
        Segmented(listOf(T("left"), T("both"), T("right")), side) {
            side = it
            if (playing != null) FindSound.start(ctx, listOf(Side.LEFT, Side.BOTH, Side.RIGHT)[it])
        }
        Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val on = playing != null
            Box(
                Modifier.size(190.dp).clip(CircleShape)
                    .background(if (on) accent.copy(alpha = 0.18f) else C.glassTop)
                    .border(1.dp, if (on) accent else C.stroke, CircleShape)
                    .clickable {
                        if (on) FindSound.stop(ctx) else FindSound.start(ctx, listOf(Side.LEFT, Side.BOTH, Side.RIGHT)[side])
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (on) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (on) accent else C.text, modifier = Modifier.size(54.dp))
                    Text(if (on) T("stop") else T("play_sound"), style = Type.body.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        Glass(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFFC15A))
                Spacer(Modifier.width(12.dp))
                Text(if (buds?.connected == true) T("find_warn") else T("find_need"), style = Type.sub.copy(color = C.text))
            }
        }
        Spacer(Modifier.height(12.dp))
        Glass(Modifier.fillMaxWidth(), onClick = { Buds.startScan() }) {
            NavRow(Icons.Rounded.Radar, T("nearest")) { Buds.startScan() }
        }
        val nearby by Buds.nearby.collectAsStateWithLifecycle()
        nearby.take(5).forEach { n ->
            Row(Modifier.padding(top = 10.dp, start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SignalBars(n.rssi); Spacer(Modifier.width(12.dp))
                Text("${n.name} · ${T(Buds.distanceKey(n.rssi))}", style = Type.body)
            }
        }
    }
}

// ------------------------------------------------------------------ Noise control

@Composable
fun NoiseScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val buds by Buds.current.collectAsStateWithLifecycle()
    val s by Store.settings.collectAsStateWithLifecycle()
    val model = buds?.model ?: BudsModels.match("")
    var mode by remember { mutableStateOf(0) }
    Page {
        SubHeader(T("noise"), buds?.name, onBack)
        if (model.anc) {
            Segmented(listOf(T("anc"), T("transparency"), T("off")), mode) { mode = it; openMaker(ctx, model) }
            Spacer(Modifier.height(12.dp))
        }
        Glass(Modifier.fillMaxWidth()) {
            Text(T("noise_note"), style = Type.sub)
            val pkg = model.makerApp
            if (pkg != null && ctx.packageManager.getLaunchIntentForPackage(pkg) != null) {
                Spacer(Modifier.height(12.dp))
                PillButton(T("open_maker").format(model.makerName), filled = false, icon = Icons.Rounded.GraphicEq) { openMaker(ctx, model) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Glass(Modifier.fillMaxWidth()) {
            ToggleRow(Icons.Rounded.Hearing, T("clarity"), T("clarity_sub"), s.voiceClarity) { v ->
                Store.update { it.copy(voiceClarity = v) }; VeloService.sync(ctx)
            }
        }
    }
}

private fun openMaker(ctx: android.content.Context, m: BudsModel) {
    val pkg = m.makerApp ?: return
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ------------------------------------------------------------------ Gestures

@Composable
fun GesturesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val buds by Buds.current.collectAsStateWithLifecycle()
    val model = buds?.model ?: BudsModels.match("")
    var pickFor by remember { mutableStateOf<GestureSlot?>(null) }
    val access = remember(s.gestureControl) { Media.hasListenerAccess(ctx) }

    Page {
        SubHeader(T("gestures"), T("gestures_sub"), onBack)
        Glass(Modifier.fillMaxWidth()) {
            ToggleRow(Icons.Rounded.TouchApp, T("gesture_enable"), T("gesture_enable_sub"), s.gestureControl) { v ->
                Store.update { it.copy(gestureControl = v) }
                if (v && !Media.hasListenerAccess(ctx)) Media.openListenerSettings(ctx)
                VeloService.sync(ctx)
            }
            if (s.gestureControl && !access) {
                PillButton(T("grant"), filled = false) { Media.openListenerSettings(ctx) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val slots = buildList {
            if (model.touch) {
                add(GestureSlot.TAP_1)
                if (model.maxTaps >= 2) add(GestureSlot.TAP_2)
                if (model.maxTaps >= 3) add(GestureSlot.TAP_3)
            }
            add(GestureSlot.NEXT_SIGNAL); add(GestureSlot.PREV_SIGNAL)
            if (model.hold) add(GestureSlot.HOLD)
        }
        Glass(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
            slots.forEach { slot ->
                val b = s.bindings[slot] ?: Binding(GestureAction.NONE)
                Box {
                    NavRow(null, T(slot.name), if (b.action == GestureAction.OPEN_APP) b.appLabel ?: T("OPEN_APP") else T(b.action.name)) { pickFor = slot }
                    ActionMenu(pickFor == slot, { pickFor = null }) { action, app ->
                        Store.update { st -> st.copy(bindings = st.bindings + (slot to Binding(action, app?.pkg, app?.label))) }
                        pickFor = null
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(T("gesture_note"), style = Type.sub, modifier = Modifier.padding(horizontal = 6.dp))
        if (model.hold) {
            Spacer(Modifier.height(8.dp))
            Text(T("hold_note"), style = Type.sub, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun ActionMenu(open: Boolean, onDismiss: () -> Unit, onPick: (GestureAction, Media.AppInfo?) -> Unit) {
    var apps by remember { mutableStateOf(false) }
    DropdownMenu(
        expanded = open, onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xF21A1D21),
        modifier = Modifier.border(1.dp, C.stroke, RoundedCornerShape(24.dp))
    ) {
        GestureAction.entries.forEach { a ->
            DropdownMenuItem(
                text = { Text(T(a.name), style = Type.body.copy(fontWeight = FontWeight.SemiBold)) },
                onClick = { if (a == GestureAction.OPEN_APP) apps = true else onPick(a, null) }
            )
        }
    }
    if (apps) AppPicker({ apps = false; onDismiss() }) { app -> apps = false; onPick(GestureAction.OPEN_APP, app) }
}

@Composable
fun AppPicker(onDismiss: () -> Unit, onPick: (Media.AppInfo) -> Unit) {
    val ctx = LocalContext.current
    val list = remember { Media.launchableApps(ctx) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF15171A)).border(1.dp, C.stroke, RoundedCornerShape(28.dp)).padding(18.dp)) {
            Text(T("pick_app"), style = Type.h)
            Spacer(Modifier.height(10.dp))
            LazyColumn {
                items(list, key = { it.pkg }) { a ->
                    Text(a.label, style = Type.body, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(a) }.padding(vertical = 12.dp, horizontal = 6.dp))
                }
            }
        }
    }
}
