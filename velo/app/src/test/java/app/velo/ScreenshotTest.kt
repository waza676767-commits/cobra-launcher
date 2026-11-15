package app.velo

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.velo.bt.Buds
import app.velo.bt.BudsModels
import app.velo.bt.BudsState
import app.velo.bt.Nearby
import app.velo.data.Lang
import app.velo.data.Store
import app.velo.voice.Assistant
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        val name = "REDMI Buds 8"
        val cur = BudsState("AA:BB", name, true, 80, BudsModels.match(name))
        Buds.showSample(cur, listOf(cur, BudsState("CC:DD", "Galaxy Buds2 Pro", false, null, BudsModels.match("Galaxy Buds2 Pro"))),
            listOf(Nearby("EE", "REDMI Buds 8", -44, true), Nearby("FF", "WH-1000XM5", -71, false)))
        Store.update { it.copy(lang = Lang.EN) }
    }

    private fun shot(screen: Screen, file: String) {
        rule.setContent { Root(screen) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../screenshots/$file.png")
    }

    @Test fun home() = shot(Screen.HOME, "1-home")
    @Test fun devices() = shot(Screen.DEVICES, "2-devices")
    @Test fun find() = shot(Screen.FIND, "3-find")
    @Test fun gestures() = shot(Screen.GESTURES, "4-gestures")
    @Test fun settings() = shot(Screen.SETTINGS, "5-settings")
    @Test fun assistantRo() {
        Store.update { it.copy(lang = Lang.RO) }
        shot(Screen.ASSISTANT, "6-assistant-ro")
    }
}
