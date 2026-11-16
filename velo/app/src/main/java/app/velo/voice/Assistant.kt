package app.velo.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.velo.audio.FindSound
import app.velo.audio.Media
import app.velo.audio.Side
import app.velo.bt.Buds
import app.velo.data.Lang
import app.velo.data.Store
import app.velo.data.T
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

data class ChatMsg(val fromUser: Boolean, val text: String)

/**
 * Velo's brain. Device commands (music, volume, apps, battery, time, find) run instantly on the
 * phone in English, Romanian or Spanish. Everything else goes to Claude, answering in the app's
 * language in a short, spoken style.
 */
object Assistant {
    private const val MODEL = "claude-opus-5-5"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var client: AnthropicClient? = null
    private var clientKey = ""

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()
    private val _chat = MutableStateFlow<List<ChatMsg>>(emptyList())
    val chat: StateFlow<List<ChatMsg>> = _chat.asStateFlow()
    val partial = MutableStateFlow("")

    fun setPhase(p: Phase) { _phase.value = p }

    private fun add(m: ChatMsg) { _chat.value = (_chat.value + m).takeLast(60) }

    /** Handle one request (spoken or typed). [done] runs after Velo finishes speaking. */
    fun handle(ctx: Context, text: String, speak: Boolean = true, done: () -> Unit = {}) {
        val q = text.trim()
        if (q.isEmpty()) {
            reply(T("s_didnt_hear"), speak, done); return
        }
        add(ChatMsg(true, q))
        val local = runCatching { local(ctx, q) }.getOrNull()
        if (local != null) { reply(local, speak, done); return }

        val key = Store.now.apiKey.trim()
        if (key.isEmpty()) { reply(T("s_no_key"), speak, done); return }
        _phase.value = Phase.THINKING
        io.execute {
            val answer = try { askClaude(key) } catch (e: UnauthorizedException) {
                T("s_bad_key")
            } catch (e: RateLimitException) {
                T("s_error")
            } catch (e: AnthropicServiceException) {
                T("s_error")
            } catch (e: Exception) {
                T("s_error")
            }
            main.post { reply(answer, speak, done) }
        }
    }

    private fun reply(text: String, speak: Boolean, done: () -> Unit) {
        add(ChatMsg(false, text))
        if (!speak) { _phase.value = Phase.IDLE; done(); return }
        _phase.value = Phase.SPEAKING
        Speaker.speak(text) { main.post { _phase.value = Phase.IDLE; done() } }
    }

    private fun clientFor(key: String): AnthropicClient {
        if (client == null || clientKey != key) {
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client!!
    }

    private fun askClaude(key: String): String {
        val s = Store.now
        val langName = when (s.lang) { Lang.EN -> "English"; Lang.RO -> "Romanian"; Lang.ES -> "Spanish" }
        val buds = Buds.current.value
        val now = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.forLanguageTag(s.lang.tag)).format(Date())
        val system = """
            You are Velo, the AI assistant of the Velo app. You live in the user's wireless earbuds.
            Your name is Velo and this is who you are in every conversation. Introduce yourself as Velo,
            speak as Velo, and never call yourself by any other assistant's name.
            If someone asks who made you or what model or company is behind you, say you are Velo, the
            assistant built into the Velo app, and that you can't share details about the technology
            behind it. Then steer back to helping them.
            Everything you write is read aloud by text-to-speech, so:
            - Answer in $langName, whatever language the question was in.
            - Keep it short and natural: one to three spoken sentences unless the user asks for more.
            - No markdown, lists, emojis, URLs or symbols. Write numbers the way they're said.
            - Be warm and direct, like a helpful friend. If you are unsure, say so briefly.
            Use web search only for things that need current information (news, weather, scores, prices).
            Context: it is $now. Earbuds: ${buds?.name ?: "none connected"}${buds?.battery?.let { ", battery $it%" } ?: ""}.
        """.trimIndent()

        val b = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4096L)
            .system(system)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addTool(WebSearchTool20260209.builder().maxUses(2L).build())
            // Server-side fallback: if a request is declined, the API retries on a fallback model.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

        // Last few turns as context (must start with a user turn and alternate).
        val history = _chat.value.takeLast(12).dropWhile { !it.fromUser }
        var lastUser: Boolean? = null
        for (m in history) {
            if (m.fromUser == lastUser) continue
            if (m.fromUser) b.addUserMessage(m.text) else b.addAssistantMessage(m.text)
            lastUser = m.fromUser
        }
        val resp = clientFor(key).messages().create(b.build())
        if (resp.stopReason().map { it.toString() }.orElse("") == "refusal") return T("s_error")
        val text = resp.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString(" ").trim()
        return text.ifEmpty { T("s_error") }
    }

    // ---------------- On-device commands ----------------

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9% ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun has(q: String, vararg words: String) = words.any { w -> Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(q) }

    /** Returns a spoken reply if it was a device command, or null to ask Claude. */
    private fun local(ctx: Context, raw: String): String? {
        val q = norm(raw)

        // open <app>
        Regex("^(?:please |te rog |por favor )?(?:open|launch|start|deschide|porneste aplicatia|abre|abrir|inicia)\\s+(.+)$").find(q)?.let { m ->
            val name = m.groupValues[1].replace(Regex("^(the|app|aplicatia|la app|la aplicacion)\\s+"), "").trim()
            val app = Media.findApp(ctx, name)
            return if (app != null && Media.launch(ctx, app.pkg)) T("s_opening").format(app.label) else T("s_no_app").format(name)
        }

        if (has(q, "find my earbuds", "find my headphones", "find my buds", "gaseste castile", "gaseste-mi castile",
                "busca mis auriculares", "encuentra mis auriculares")) {
            val side = when {
                has(q, "left", "stanga", "izquierdo") -> Side.LEFT
                has(q, "right", "dreapta", "derecho") -> Side.RIGHT
                else -> Side.BOTH
            }
            main.postDelayed({ FindSound.start(ctx, side) }, 2500)
            return T("s_find")
        }

        if (q.split(" ").size <= 8 && has(q, "battery", "baterie", "bateria", "charge", "procent")) {
            Buds.refresh()
            val b = Buds.current.value?.battery
            return if (b != null) T("s_battery").format(b) else T("s_battery_unknown")
        }

        if (q.split(" ").size <= 6 && (has(q, "what time", "the time", "time is it", "cat e ora", "ce ora", "que hora", "hora es"))) {
            val t = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.forLanguageTag(Store.now.lang.tag)).format(Date())
            return T("s_time").format(t)
        }

        // volume to N%
        Regex("(?:volume|volumul|volumen)\\D*(\\d{1,3})").find(q)?.let {
            Media.setVolumePercent(ctx, it.groupValues[1].toInt()); return T("s_paused")
        }
        if (has(q, "louder", "volume up", "turn it up", "turn up", "mai tare", "da mai tare", "sube", "mas alto", "sube el volumen")) {
            Media.volume(ctx, true); return T("s_volume_up")
        }
        if (has(q, "quieter", "volume down", "turn it down", "turn down", "mai incet", "da mai incet", "baja", "mas bajo", "baja el volumen")) {
            Media.volume(ctx, false); return T("s_volume_down")
        }
        val short = q.split(" ").size <= 5
        if (short && has(q, "next", "skip", "urmatoarea", "urmatoarea melodie", "siguiente", "salta")) {
            Media.next(ctx); return T("s_next")
        }
        if (short && has(q, "previous", "go back", "anterioara", "inapoi", "anterior")) {
            Media.previous(ctx); return T("s_prev")
        }
        if (short && has(q, "pause", "stop the music", "stop music", "pauza", "opreste muzica", "opreste", "pausa", "para la musica", "detener")) {
            Media.pause(ctx); return T("s_paused")
        }
        if (short && has(q, "play", "resume", "play music", "porneste muzica", "pune muzica", "continua", "reproduce", "pon musica")) {
            Media.play(ctx); return T("s_paused")
        }
        return null
    }
}
