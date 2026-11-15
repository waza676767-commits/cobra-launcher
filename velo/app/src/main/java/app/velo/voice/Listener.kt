package app.velo.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.velo.data.Store

/** Captures what you say after "Hey Velo" using the phone's speech recogniser, in the app's language. */
object Listener {
    private var sr: SpeechRecognizer? = null

    fun listen(ctx: Context, onText: (String?) -> Unit) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onText(null); return }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx.applicationContext)
        sr = r
        var delivered = false
        fun deliver(t: String?) { if (!delivered) { delivered = true; Assistant.partial.value = ""; onText(t); cancel() } }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Assistant.setPhase(Phase.LISTENING) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { deliver(null) }
            override fun onResults(results: Bundle?) {
                deliver(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    Assistant.partial.value = it
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val tag = Store.now.lang.tag
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        Assistant.setPhase(Phase.LISTENING)
        r.startListening(i)
    }

    val level = kotlinx.coroutines.flow.MutableStateFlow(0f)

    fun cancel() {
        sr?.let { runCatching { it.cancel(); it.destroy() } }
        sr = null
        level.value = 0f
    }
}
