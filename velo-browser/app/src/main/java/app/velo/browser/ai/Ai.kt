package app.velo.browser.ai

/** The assistants Velo can talk to. Each one is used with the user's own account. */
enum class AiProvider(
    val id: String,
    val label: String,
    val company: String,
    /** Where the user creates an API key for the built-in chat. */
    val keyUrl: String,
    /** The assistant's own website, for signing in without an API key. */
    val webUrl: String,
    val keyHint: String,
    val brand: Long,
) {
    CLAUDE(
        "claude", "Claude", "Anthropic",
        "https://platform.claude.com/settings/keys", "https://claude.ai/new", "sk-ant-…", 0xFFD97757,
    ),
    CHATGPT(
        "chatgpt", "ChatGPT", "OpenAI",
        "https://platform.openai.com/api-keys", "https://chatgpt.com/", "sk-…", 0xFF10A37F,
    ),
    GEMINI(
        "gemini", "Gemini", "Google",
        "https://aistudio.google.com/apikey", "https://gemini.google.com/app", "AIza…", 0xFF4C8DF6,
    );

    val keyName: String get() = "ai_key_$id"

    companion object {
        fun byId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: CLAUDE
    }
}

data class AiModel(
    val id: String,
    val label: String,
    /** Whether the model accepts an effort setting (Claude only). */
    val supportsEffort: Boolean = false,
    /** Largest reply the model can produce, when the provider reports it. */
    val maxOutput: Long? = null,
)

/** One turn as sent to a provider. */
data class ChatTurn(val fromUser: Boolean, val text: String)

/** A failure worth showing to the user as-is. */
class AiException(message: String, val badKey: Boolean = false) : Exception(message)

interface StreamSink {
    /** More reply text arrived. */
    fun onText(delta: String)

    /** Something the user should know that isn't part of the reply, e.g. the model declined. */
    fun onNotice(text: String)
}

/** A provider's API. Every call blocks, so run them off the main thread. */
interface ChatBackend {
    fun listModels(key: String): List<AiModel>

    /** Picks the model to use by default from [models]. */
    fun defaultModel(models: List<AiModel>): AiModel?

    fun stream(key: String, model: AiModel, system: String, turns: List<ChatTurn>, sink: StreamSink)

    /** Stops the reply in progress, if any. Safe from any thread. */
    fun cancel()
}
