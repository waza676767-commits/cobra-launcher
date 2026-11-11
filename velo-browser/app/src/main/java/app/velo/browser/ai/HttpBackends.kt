package app.velo.browser.ai

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/** Shared plumbing for providers Velo talks to over plain HTTPS with server-sent events. */
abstract class HttpBackend(
    private val http: OkHttpClient,
    private val providerName: String,
) : ChatBackend {
    private val call = AtomicReference<Call?>()

    @Volatile
    private var cancelled = false

    override fun cancel() {
        cancelled = true
        call.getAndSet(null)?.cancel()
    }

    protected fun getJson(request: Request): JSONObject =
        execute(request) { JSONObject(it.body?.string().orEmpty()) }

    /** POSTs [body] and feeds each `data:` payload of the event stream to [onData]. */
    protected fun streamJson(request: Request.Builder, body: JSONObject, onData: (JSONObject) -> Unit) {
        cancelled = false
        val built = request.post(body.toString().toRequestBody(JSON_TYPE)).build()
        try {
            execute(built) { response ->
                val source = response.body?.source() ?: return@execute
                while (!cancelled) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    onData(json)
                }
            }
        } catch (e: IOException) {
            if (cancelled) return
            throw AiException("Couldn't reach $providerName. Check your connection.")
        }
    }

    private fun <T> execute(request: Request, onSuccess: (Response) -> T): T {
        val c = http.newCall(request)
        call.set(c)
        try {
            c.execute().use { response ->
                if (!response.isSuccessful) throw failure(response)
                return onSuccess(response)
            }
        } catch (e: IOException) {
            if (cancelled) throw e
            throw AiException("Couldn't reach $providerName. Check your connection.")
        } finally {
            call.compareAndSet(c, null)
        }
    }

    private fun failure(response: Response): AiException {
        val text = runCatching { response.body?.string() }.getOrNull().orEmpty()
        val message = runCatching {
            val error = JSONObject(text).optJSONObject("error")
            error?.optString("message")
        }.getOrNull().orEmpty()
        return when (response.code) {
            401, 403 -> AiException(
                "$providerName didn't accept that API key. Check it and connect again.", badKey = true
            )
            429 -> AiException(
                message.ifBlank { "$providerName says you've hit a rate limit or run out of quota." }
            )
            else -> AiException(message.ifBlank { "$providerName returned an error (${response.code})." })
        }
    }
}

/** ChatGPT, through OpenAI's chat completions API. */
class OpenAiBackend(http: OkHttpClient, private val baseUrl: String = "https://api.openai.com") :
    HttpBackend(http, "OpenAI") {

    override fun listModels(key: String): List<AiModel> {
        val json = getJson(
            Request.Builder().url("$baseUrl/v1/models").header("Authorization", "Bearer $key").build()
        )
        val data = json.optJSONArray("data") ?: JSONArray()
        val found = ArrayList<Pair<Long, AiModel>>()
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val id = o.optString("id")
            if (isChatModel(id)) found += o.optLong("created") to AiModel(id, id)
        }
        return found.sortedByDescending { it.first }.map { it.second }
    }

    override fun defaultModel(models: List<AiModel>): AiModel? {
        for (wanted in PREFERRED) models.firstOrNull { it.id == wanted }?.let { return it }
        return models.firstOrNull { !it.id.contains("mini") && !it.id.contains("nano") } ?: models.firstOrNull()
    }

    override fun stream(key: String, model: AiModel, system: String, turns: List<ChatTurn>, sink: StreamSink) {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (turn in turns) {
            messages.put(
                JSONObject().put("role", if (turn.fromUser) "user" else "assistant").put("content", turn.text)
            )
        }
        val body = JSONObject().put("model", model.id).put("stream", true).put("messages", messages)
        val request = Request.Builder().url("$baseUrl/v1/chat/completions").header("Authorization", "Bearer $key")
        streamJson(request, body) { json ->
            val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return@streamJson
            val delta = choice.optJSONObject("delta")
            val text = delta?.optString("content").orEmpty()
            if (text.isNotEmpty() && delta?.isNull("content") == false) sink.onText(text)
            delta?.optString("refusal")?.takeIf { it.isNotEmpty() && !delta.isNull("refusal") }?.let(sink::onText)
            when (choice.optString("finish_reason")) {
                "length" -> sink.onNotice("The reply was cut off because it reached the length limit.")
                "content_filter" -> sink.onNotice("OpenAI's content filter stopped this reply.")
            }
        }
    }

    private fun isChatModel(id: String): Boolean {
        val chatFamily = id.startsWith("gpt-") || id.startsWith("chatgpt-") || Regex("^o\\d").containsMatchIn(id)
        return chatFamily && EXCLUDED.none { id.contains(it) }
    }

    companion object {
        private val PREFERRED = listOf("gpt-5.2", "gpt-5.1", "gpt-5", "gpt-4.1", "gpt-4o")
        private val EXCLUDED = listOf(
            "audio", "realtime", "tts", "transcribe", "image", "embedding", "search", "instruct",
            "moderation", "codex", "deep-research", "-pro", "computer-use",
        )
        val FALLBACK_LIST = listOf(AiModel("gpt-4o", "gpt-4o"))
    }
}

/** Gemini, through Google's Generative Language API. */
class GeminiBackend(
    http: OkHttpClient,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : HttpBackend(http, "Google") {

    override fun listModels(key: String): List<AiModel> {
        val json = getJson(
            Request.Builder().url("$baseUrl/v1beta/models?pageSize=200").header("x-goog-api-key", key).build()
        )
        val data = json.optJSONArray("models") ?: JSONArray()
        val out = ArrayList<AiModel>()
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val id = o.optString("name").removePrefix("models/")
            val methods = o.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
            if (!methods.contains("generateContent")) continue
            if (!id.startsWith("gemini") || EXCLUDED.any { id.contains(it) }) continue
            out += AiModel(id, o.optString("displayName").ifBlank { id })
        }
        return out.sortedByDescending { it.id }
    }

    override fun defaultModel(models: List<AiModel>): AiModel? {
        for (wanted in PREFERRED) models.firstOrNull { it.id == wanted }?.let { return it }
        return models.firstOrNull { it.id.contains("flash") && !it.id.contains("lite") } ?: models.firstOrNull()
    }

    override fun stream(key: String, model: AiModel, system: String, turns: List<ChatTurn>, sink: StreamSink) {
        val contents = JSONArray()
        for (turn in turns) {
            contents.put(
                JSONObject()
                    .put("role", if (turn.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        val request = Request.Builder()
            .url("$baseUrl/v1beta/models/${model.id}:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", key)
        streamJson(request, body) { json ->
            json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
                sink.onNotice("Gemini blocked this request ($it).")
            }
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: return@streamJson
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.optBoolean("thought")) continue
                    val text = part.optString("text")
                    if (text.isNotEmpty()) sink.onText(text)
                }
            }
            when (candidate.optString("finishReason")) {
                "MAX_TOKENS" -> sink.onNotice("The reply was cut off because it reached the length limit.")
                "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" -> sink.onNotice("Gemini's safety filter stopped this reply.")
            }
        }
    }

    companion object {
        private val PREFERRED = listOf("gemini-flash-latest", "gemini-2.5-flash")
        private val EXCLUDED = listOf(
            "embedding", "aqa", "image", "tts", "audio", "live", "robotics", "computer-use", "vision",
        )
        val FALLBACK_LIST = listOf(AiModel("gemini-flash-latest", "Gemini Flash"))
    }
}
