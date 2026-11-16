package app.velo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velo.data.Accent
import app.velo.data.Lang
import app.velo.data.Store
import app.velo.data.T
import app.velo.data.VoiceStyle
import app.velo.voice.Speaker
import app.velo.voice.VeloService

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    Page {
        Spacer(Modifier.height(24.dp))
        Text(T("settings"), style = Type.title)

        SectionLabel(T("language"))
        Segmented(Lang.entries.map { it.label }, s.lang.ordinal) { i -> Store.update { it.copy(lang = Lang.entries[i]) } }

        SectionLabel(T("voice"))
        Glass(Modifier.fillMaxWidth()) {
            VoiceStyle.entries.forEach { v ->
                val on = s.voice == v
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                    Store.update { it.copy(voice = v) }; Speaker.speak(T("test_voice"))
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) accent else C.glassTop), contentAlignment = Alignment.Center) {
                        Icon(if (on) Icons.Rounded.Check else Icons.Rounded.GraphicEq, null, tint = if (on) Color.Black else C.sub, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(T(v.name), style = Type.body.copy(fontWeight = FontWeight.SemiBold))
                        Text(T(v.name + "_d"), style = Type.sub)
                    }
                    Icon(Icons.Rounded.PlayArrow, T("preview"), tint = C.faint)
                }
            }
            ToggleRow(null, T("hd_voice"), T("hd_voice_sub"), s.hdVoice) { v -> Store.update { it.copy(hdVoice = v) } }
            NavRow(Icons.Rounded.GraphicEq, T("better_voices")) { Speaker.openVoiceDownloads(ctx) }
            Text(T("better_voices_sub"), style = Type.sub, modifier = Modifier.padding(start = 54.dp))
        }

        SectionLabel(T("accent"))
        Glass(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Accent.entries.forEach { a ->
                    val col = Color(a.argb)
                    Box(Modifier.size(32.dp).clip(CircleShape).background(col)
                        .border(2.dp, if (s.accent == a) Color.White else Color.Transparent, CircleShape)
                        .clickable { Store.update { it.copy(accent = a) } }, contentAlignment = Alignment.Center) {
                        if (s.accent == a) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        SectionLabel(T("hey_velo"))
        Glass(Modifier.fillMaxWidth()) {
            ToggleRow(Icons.Rounded.Mic, T("hey_velo"), T("hey_velo_sub"), s.wakeEnabled) { v ->
                Store.update { it.copy(wakeEnabled = v) }; VeloService.sync(ctx)
            }
            Spacer(Modifier.height(8.dp))
            Text(T("sensitivity"), style = Type.sub)
            Spacer(Modifier.height(8.dp))
            Segmented(listOf(T("strict"), T("balanced"), T("relaxed")), s.wakeSensitivity) { i ->
                Store.update { it.copy(wakeSensitivity = i) }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(Icons.Rounded.Headset, T("buds_mic"), T("buds_mic_sub"), s.useBudsMic) { v ->
                Store.update { it.copy(useBudsMic = v) }; VeloService.sync(ctx)
            }
            ToggleRow(Icons.Rounded.Hearing, T("clarity"), T("clarity_sub"), s.voiceClarity) { v ->
                Store.update { it.copy(voiceClarity = v) }
            }
        }

        SectionLabel(T("api_key"))
        Glass(Modifier.fillMaxWidth()) {
            var key by remember { mutableStateOf(s.apiKey) }
            var saved by remember { mutableStateOf(false) }
            var test by remember { mutableStateOf<Pair<Boolean?, String>?>(null) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.Key)
                Spacer(Modifier.width(14.dp))
                Text(T("api_key_sub"), style = Type.sub, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF))
                    .border(1.dp, C.stroke, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    if (key.isEmpty()) Text(T("api_key_hint"), style = Type.body.copy(color = C.faint))
                    BasicTextField(key, { key = it; saved = false }, textStyle = Type.body, singleLine = true,
                        cursorBrush = SolidColor(accent), visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(10.dp))
                PillButton(if (saved) T("saved_ok") else T("save")) {
                    // Keys often get pasted with spaces/newlines or quotes around them.
                    val clean = key.trim().trim('"', '\'', ' ').replace(Regex("\\s"), "")
                    key = clean
                    Store.update { it.copy(apiKey = clean) }; saved = true
                    test = null to T("testing")
                    app.velo.voice.Assistant.testKey(clean) { ok, txt -> test = ok to txt }
                }
            }
            test?.let { (ok, txt) ->
                Spacer(Modifier.height(10.dp))
                Text(txt, style = Type.sub.copy(color = when (ok) { true -> accent; false -> C.danger; null -> C.sub }))
            }
        }

        SectionLabel(T("about"))
        Text("Velo 1.0", style = Type.sub, modifier = Modifier.padding(horizontal = 6.dp))
    }
}
