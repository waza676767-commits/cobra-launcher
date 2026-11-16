package app.velo.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NoiseAware
import androidx.compose.material.icons.rounded.NoiseControlOff
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velo.Screen
import app.velo.bt.Buds
import app.velo.bt.BudsStyle
import app.velo.data.Store
import app.velo.data.T
import app.velo.voice.VeloService
import app.velo.voice.WakeWord
import kotlin.concurrent.thread

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val buds by Buds.current.collectAsStateWithLifecycle()
    val connecting by Buds.connecting.collectAsStateWithLifecycle()
    val s by Store.settings.collectAsStateWithLifecycle()
    val dl by WakeWord.downloading.collectAsStateWithLifecycle()
    val dlP by WakeWord.downloadProgress.collectAsStateWithLifecycle()
    val b = buds

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val addr = b?.address
        if (uri != null && addr != null) thread { BudsPhotos.save(ctx, addr, uri) }
    }

    Page {
        // Header: name + "Manage your earbuds >" with round buttons, like the maker app
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(b?.name ?: T("no_buds"), style = Type.hero.copy(fontSize = 30.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.clickable { go(Screen.DEVICES) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(T("manage"), style = Type.body.copy(color = C.sub, fontSize = 17.sp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.sub)
                }
            }
            RoundIcon(Icons.Rounded.Add) { go(Screen.DEVICES) }
            Spacer(Modifier.width(10.dp))
            RoundIcon(Icons.Rounded.Settings) { go(Screen.SETTINGS) }
        }

        // Product photo
        Box(Modifier.fillMaxWidth().aspectRatio(1.55f).padding(vertical = 6.dp)) {
            BudsArt(b?.model?.style ?: BudsStyle.BEANS, accent, Modifier.fillMaxWidth().aspectRatio(1.3f).align(Alignment.Center)
                
                .padding(horizontal = 56.dp), address = b?.address, name = b?.name)
            if (b != null) Row(
                Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(50)).background(C.glassTop)
                    .border(1.dp, C.stroke, RoundedCornerShape(50))
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PhotoCamera, null, tint = C.sub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(T("photo"), style = Type.sub)
            }
        }

        // Status line
        Box(Modifier.fillMaxWidth().padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
            when {
                b == null -> Text(T("nearest"), style = Type.body.copy(color = accent, textDecoration = TextDecoration.Underline, fontSize = 17.sp),
                    modifier = Modifier.clickable { go(Screen.DEVICES) })
                b.connected -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(8.dp))
                    Text(T("connected") + (b.battery?.let { "  ·  " + T("battery_pct").format(it) } ?: ""),
                        style = Type.body.copy(fontSize = 17.sp))
                }
                else -> Text(if (connecting == b.address) T("connecting") else T("disc_link"),
                    style = Type.body.copy(color = accent, textDecoration = TextDecoration.Underline, fontSize = 17.sp),
                    modifier = Modifier.clickable { Buds.connect(b.address) })
            }
        }

        // Noise control: three round buttons in a row
        val on = b?.connected == true
        Glass(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 20.dp, horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth().alpha(if (on) 1f else 0.45f), horizontalArrangement = Arrangement.SpaceEvenly) {
                NoiseButton(Icons.Rounded.Hearing, T("transparency")) { openNoise(ctx, b, go) }
                NoiseButton(Icons.Rounded.NoiseAware, T("anc")) { openNoise(ctx, b, go) }
                NoiseButton(Icons.Rounded.NoiseControlOff, T("off")) { openNoise(ctx, b, go) }
            }
        }

        Spacer(Modifier.height(14.dp))
        RowGroup {
            ListRow(Icons.Rounded.TouchApp, T("gestures")) { go(Screen.GESTURES) }
            ListRow(Icons.Rounded.Mic, T("hey_velo"), trailing = {
                Switch(s.wakeEnabled, { v -> Store.update { it.copy(wakeEnabled = v) }; VeloService.sync(ctx) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent,
                        uncheckedThumbColor = C.sub, uncheckedTrackColor = Color(0xFF1C1F23), uncheckedBorderColor = C.stroke))
            }) { Store.update { it.copy(wakeEnabled = !it.wakeEnabled) }; VeloService.sync(ctx) }
            ListRow(Icons.Rounded.AutoAwesome, T("assistant")) { go(Screen.ASSISTANT) }
        }
        if (dl) {
            Spacer(Modifier.height(10.dp))
            Glass(Modifier.fillMaxWidth()) { Text(T("wake_model"), style = Type.sub); Spacer(Modifier.height(10.dp)); ProgressBar(dlP) }
        }
        Spacer(Modifier.height(14.dp))
        RowGroup {
            ListRow(Icons.Rounded.VolumeUp, T("find")) { go(Screen.FIND) }
            ListRow(Icons.Rounded.Radar, T("nearest")) { go(Screen.DEVICES) }
            ListRow(Icons.Rounded.NoiseAware, T("clarity")) { go(Screen.NOISE) }
        }
        Spacer(Modifier.height(14.dp))
        RowGroup {
            ListRow(Icons.Rounded.Devices, T("my_buds")) { go(Screen.DEVICES) }
            ListRow(Icons.Rounded.Settings, T("settings")) { go(Screen.SETTINGS) }
        }
    }
}

private fun openNoise(ctx: android.content.Context, b: app.velo.bt.BudsState?, go: (Screen) -> Unit) {
    val pkg = b?.model?.makerApp
    val i = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
    if (i != null) ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else go(Screen.NOISE)
}

@Composable
private fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).pressable(0.9f, onClick).clip(CircleShape).border(1.5.dp, C.text.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.text, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun NoiseButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).pressable(0.93f, onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(Color(0xFF3A3D42)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF1A1C1F), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = Type.body.copy(color = C.sub, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** A rounded card holding list rows, like the maker app's settings groups. */
@Composable
fun RowGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF17191C))
        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(26.dp)).padding(vertical = 6.dp)) { content() }
}

@Composable
fun ListRow(icon: ImageVector, title: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().pressable(0.98f, onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(18.dp))
        Text(title, style = Type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
    }
}
