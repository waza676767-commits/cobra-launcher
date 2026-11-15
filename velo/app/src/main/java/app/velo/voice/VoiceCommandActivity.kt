package app.velo.voice

import android.app.Activity
import android.os.Bundle

/** Opened by "touch & hold" on the earbuds (Android's voice-command button) — starts a Velo turn. */
class VoiceCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VeloService.listenNow(this)
        finish()
    }
}
