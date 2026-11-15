package app.velo.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.velo.data.Lang
import app.velo.data.Store
import app.velo.data.VoiceStyle
import java.util.Locale

/**
 * Velo's voice. Picks the most natural installed voice for the language and the chosen style
 * (Google's neural "network" voices when HD voice is on), then tunes pitch/rate per style.
 */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<Pair<String, (() -> Unit)?>>()
    private val callbacks = HashMap<String, () -> Unit>()
    private var counter = 0

    // Gender of Google TTS voices by their code (e.g. "en-us-x-iom-network" → male).
    private val male = setOf(
        "iol", "iom", "tpd", "gbb", "gbd", "rjs", "eed", "eef", "esd", "end", "aud", "aub", "ahp", "tpe"
    )
    private val female = setOf(
        "iob", "iog", "tpc", "tpf", "sfg", "gba", "gbc", "gbg", "eea", "eec", "eee", "esc", "esf", "vfv", "aua", "auc"
    )

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) {}
                    override fun onDone(id: String) { callbacks.remove(id)?.invoke() }
                    @Deprecated("") override fun onError(id: String) { callbacks.remove(id)?.invoke() }
                    override fun onError(id: String, code: Int) { callbacks.remove(id)?.invoke() }
                })
                pending.forEach { speak(it.first, it.second) }
                pending.clear()
            }
        }
    }

    private fun gender(v: Voice): Boolean? {
        val n = v.name.lowercase()
        if ("female" in n) return true
        if ("male" in n) return false
        val code = Regex("-x-([a-z]{3})").find(n)?.groupValues?.get(1) ?: return null
        return when (code) { in female -> true; in male -> false; else -> null }
    }

    /** Ranks voices: right language, right gender, best quality, network when HD is on. */
    fun pickVoice(lang: Lang, style: VoiceStyle, hd: Boolean): Pair<Voice?, Float> {
        val t = tts ?: return null to 1f
        val loc = Locale.forLanguageTag(lang.tag)
        val all = runCatching { t.voices.toList() }.getOrDefault(emptyList())
            .filter { it.locale.language == loc.language && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        if (all.isEmpty()) return null to 1f
        fun score(v: Voice): Int {
            var s = v.quality
            if (v.locale.country == loc.country) s += 200
            if (v.isNetworkConnectionRequired) s += if (hd) 150 else -150
            if (gender(v) == style.female) s += 1000
            return s
        }
        val ranked = all.sortedByDescending(::score)
        val sameGender = ranked.filter { gender(it) == style.female }
        val v = if (sameGender.isNotEmpty()) sameGender[style.rank.coerceAtMost(sameGender.size - 1)] else ranked.first()
        // If this language has no voice of the wanted gender (e.g. Romanian male), shape the pitch instead.
        val pitchFix = when {
            gender(v) == style.female || gender(v) == null -> 1f
            style.female -> 1.25f
            else -> 0.78f
        }
        return v to pitchFix
    }

    private fun apply() {
        val t = tts ?: return
        val s = Store.now
        val loc = Locale.forLanguageTag(s.lang.tag)
        t.language = loc
        val (v, fix) = pickVoice(s.lang, s.voice, s.hdVoice)
        v?.let { runCatching { t.voice = it } }
        t.setPitch(s.voice.pitch * fix)
        t.setSpeechRate(s.voice.rate)
    }

    fun speak(text: String, done: (() -> Unit)? = null) {
        val t = tts
        if (t == null || !ready) { pending += text to done; return }
        apply()
        val id = "v${counter++}"
        done?.let { callbacks[id] = it }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        t.speak(clean(text), TextToSpeech.QUEUE_FLUSH, params, id)
    }

    fun stop() { tts?.stop(); callbacks.clear() }

    val isSpeaking get() = tts?.isSpeaking == true

    /** Strip markdown and symbols that sound bad read aloud. */
    private fun clean(s: String) = s.replace(Regex("[*_#`>|]"), "").replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1").trim()
}
