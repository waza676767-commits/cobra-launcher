package app.velo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velo.bt.Buds
import app.velo.data.Store
import app.velo.data.T
import app.velo.ui.AssistantScreen
import app.velo.ui.C
import app.velo.ui.DevicesScreen
import app.velo.ui.FindScreen
import app.velo.ui.GesturesScreen
import app.velo.ui.HomeScreen
import app.velo.ui.LocalAccent
import app.velo.ui.NoiseScreen
import app.velo.ui.SettingsScreen
import app.velo.ui.VeloTheme
import app.velo.ui.pressable
import app.velo.voice.Speaker
import app.velo.voice.VeloService

enum class Screen { HOME, DEVICES, ASSISTANT, SETTINGS, FIND, GESTURES, NOISE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Speaker.init(this)
        setContent { Root() }
    }

    override fun onResume() {
        super.onResume()
        Buds.bindProfiles()
        Buds.refresh()
        VeloService.sync(this)
    }
}

@Composable
internal fun Root(start: Screen = Screen.HOME) {
    val s by Store.settings.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(start) }
    var tab by remember { mutableStateOf(if (start.ordinal <= Screen.SETTINGS.ordinal) start else Screen.HOME) }
    val go: (Screen) -> Unit = { screen = it; if (it.ordinal <= Screen.SETTINGS.ordinal) tab = it }

    val perms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_SCAN) }
            else add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Buds.bindProfiles(); Buds.refresh(); VeloService.sync(ctx)
    }
    LaunchedEffect(Unit) {
        if (perms.any { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) launcher.launch(perms)
    }

    // Read language so the whole tree recomposes when it changes.
    @Suppress("UNUSED_VARIABLE") val lang = s.lang
    VeloTheme(Color(s.accent.argb)) {
        val accent = LocalAccent.current
        Box(
            Modifier.fillMaxSize().background(C.bg).background(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(900f, -200f), radius = 1600f
                )
            )
        ) {
            BackHandler(enabled = screen != Screen.HOME) {
                screen = if (screen.ordinal > Screen.SETTINGS.ordinal) tab else Screen.HOME.also { tab = it }
            }
            key(s.lang) { AnimatedContent(screen, transitionSpec = {
                val ease = androidx.compose.animation.core.FastOutSlowInEasing
                val sub = Screen.SETTINGS.ordinal
                when {
                    // opening a detail page: slides in from the right
                    targetState.ordinal > sub && initialState.ordinal <= sub ->
                        (slideInHorizontally(tween(320, easing = ease)) { it / 5 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(320, easing = ease)) { -it / 12 } + fadeOut(tween(180)))
                    // going back
                    initialState.ordinal > sub && targetState.ordinal <= sub ->
                        (slideInHorizontally(tween(320, easing = ease)) { -it / 12 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(320, easing = ease)) { it / 5 } + fadeOut(tween(180)))
                    // switching tabs: soft cross-fade with a hint of zoom
                    else -> (fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(300, easing = ease), initialScale = 0.985f)) togetherWith
                        fadeOut(tween(140))
                }
            }, label = "nav") { sc ->
                when (sc) {
                    Screen.HOME -> HomeScreen(go)
                    Screen.DEVICES -> DevicesScreen(go)
                    Screen.ASSISTANT -> AssistantScreen()
                    Screen.SETTINGS -> SettingsScreen()
                    Screen.FIND -> FindScreen { go(tab) }
                    Screen.GESTURES -> GesturesScreen { go(tab) }
                    Screen.NOISE -> NoiseScreen { go(tab) }
                }
            } }
            if (screen.ordinal <= Screen.SETTINGS.ordinal) {
                PillNav(tab, Modifier.align(Alignment.BottomCenter)) { go(it) }
            }
        }
    }
}

@Composable
private fun PillNav(selected: Screen, modifier: Modifier, onSelect: (Screen) -> Unit) {
    val accent = LocalAccent.current
    val items = listOf(
        Triple(Screen.HOME, Icons.Rounded.Headphones, "home"),
        Triple(Screen.DEVICES, Icons.Rounded.Devices, "devices"),
        Triple(Screen.ASSISTANT, Icons.Rounded.AutoAwesome, "assistant"),
        Triple(Screen.SETTINGS, Icons.Rounded.Settings, "settings")
    )
    Row(
        modifier.navigationBarsPadding().padding(bottom = 14.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0xE61C1F24), Color(0xF20E1013))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))), RoundedCornerShape(50))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (sc, icon, key) ->
            NavItem(icon, T(key), sc == selected, accent) { onSelect(sc) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) accent else Color.Transparent, tween(260), label = "navBg")
    val tint by animateColorAsState(if (on) Color.Black else C.sub, tween(260), label = "navTint")
    Row(
        Modifier.pressable(0.92f, onClick).clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(21.dp))
        AnimatedVisibility(on, enter = expandHorizontally(tween(260)) + fadeIn(tween(200, 60)),
            exit = shrinkHorizontally(tween(220)) + fadeOut(tween(120))) {
            Text(label, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp, end = 2.dp))
        }
    }
}
