package app.velo.browser.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import java.net.Proxy
import java.util.concurrent.atomic.AtomicReference

/**
 * Claude, through Anthropic's official SDK.
 *
 * [baseUrl] exists so tests can point the client at a local server; in the app it is left null.
 */
class ClaudeBackend(
    private val proxy: () -> Proxy?,
    private val baseUrl: String? = null,
) : ChatBackend {

    private val current = AtomicReference<StreamResponse<BetaRawMessageStreamEvent>?>()

    @Volatile
    private var cancelled = false

    private fun client(key: String): AnthropicClient {
        val builder = AnthropicOkHttpClient.builder().apiKey(key)
        proxy()?.let { builder.proxy(it) }
        baseUrl?.let { builder.baseUrl(it) }
        return builder.build()
    }

    override fun listModels(key: String): List<AiModel> {
        val client = client(key)
        try {
            return guarded {
                client.models().list().autoPager().map { info ->
                    AiModel(
                        id = info.id(),
                        label = info.displayName(),
                        supportsEffort = info.capabilities().map { it.effort().supported() }.orElse(false),
                        maxOutput = info.maxTokens().orElse(null),
                    )
                }
            }
        } finally {
            client.close()
        }
    }

    override fun defaultModel(models: List<AiModel>): AiModel? =
        models.firstOrNull { it.id == DEFAULT_MODEL } ?: models.firstOrNull()

    override fun stream(key: String, model: AiModel, system: String, turns: List<ChatTurn>, sink: StreamSink) {
        cancelled = false
        val client = client(key)
        try {
            val withFallbacks = model.id in FALLBACK_MODELS
            var started = false
            try {
                val notice = request(client, model, system, turns, withFallbacks) { delta ->
                    started = true
                    sink.onText(delta)
                }
                notice?.let(sink::onNotice)
            } catch (e: BadRequestException) {
                // An account without access to server-side fallbacks rejects the request outright.
                // Nothing has been shown yet, so send it once more as a plain request.
                if (!withFallbacks || started || cancelled) throw e
                request(client, model, system, turns, false, sink::onText)?.let(sink::onNotice)
            }
        } catch (e: UnauthorizedException) {
            throw AiException("Anthropic didn't accept that API key. Check it and connect again.", badKey = true)
        } catch (e: PermissionDeniedException) {
            throw AiException(e.apiMessage().orElse("Your Anthropic account isn't allowed to do that."))
        } catch (e: NotFoundException) {
            throw AiException("Your account can't use ${model.label}. Pick another model.")
        } catch (e: RateLimitException) {
            throw AiException("Anthropic is rate-limiting your account. Wait a moment and try again.")
        } catch (e: AnthropicServiceException) {
            throw AiException(e.apiMessage().orElse("Claude returned an error (${e.statusCode()})."))
        } catch (e: AnthropicIoException) {
            if (cancelled) return
            throw AiException("Couldn't reach Claude. Check your connection.")
        } catch (e: RuntimeException) {
            // Closing the stream to stop a reply surfaces as an error from the reader.
            if (cancelled) return
            throw e
        } finally {
            current.set(null)
            client.close()
        }
    }

    /** Runs one streaming request. Returns a notice for the user when the reply ended unusually. */
    private fun request(
        client: AnthropicClient,
        model: AiModel,
        system: String,
        turns: List<ChatTurn>,
        withFallbacks: Boolean,
        onText: (String) -> Unit,
    ): String? {
        val params = MessageCreateParams.builder()
            .model(model.id)
            .maxTokens(minOf(MAX_TOKENS, model.maxOutput ?: MAX_TOKENS))
            .system(system)
        // Thinking is left to the model's adaptive default. Effort is set explicitly because its
        // default differs between models; medium keeps a chat panel responsive.
        if (model.supportsEffort) {
            params.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
        }
        if (withFallbacks) {
            // If the model's safety classifiers decline a request, let the API retry it on the
            // model it recommends instead of leaving the user with nothing.
            params.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
            params.fallbacks(BetaFallbacksParam.ofDefault())
        }
        for (turn in turns) {
            if (turn.fromUser) params.addUserMessage(turn.text) else params.addAssistantMessage(turn.text)
        }

        var notice: String? = null
        client.beta().messages().createStreaming(params.build()).use { stream ->
            current.set(stream)
            if (cancelled) return null
            for (event in stream) {
                event.contentBlockDelta().ifPresent { block ->
                    block.delta().text().ifPresent { onText(it.text()) }
                }
                event.messageDelta().ifPresent { end ->
                    val reason = end.delta().stopReason().orElse(null)
                    if (reason == BetaStopReason.REFUSAL) {
                        notice = end.delta().stopDetails().flatMap { it.explanation() }
                            .orElse("Claude declined to answer this one.")
                    } else if (reason == BetaStopReason.MAX_TOKENS) {
                        notice = "The reply was cut off because it reached the length limit."
                    }
                }
            }
        }
        return notice
    }

    override fun cancel() {
        cancelled = true
        runCatching { current.getAndSet(null)?.close() }
    }

    private inline fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (e: UnauthorizedException) {
            throw AiException("Anthropic didn't accept that API key. Check it and try again.", badKey = true)
        } catch (e: PermissionDeniedException) {
            throw AiException(e.apiMessage().orElse("Your Anthropic account isn't allowed to do that."))
        } catch (e: RateLimitException) {
            throw AiException("Anthropic is rate-limiting your account. Wait a moment and try again.")
        } catch (e: AnthropicServiceException) {
            throw AiException(e.apiMessage().orElse("Claude returned an error (${e.statusCode()})."))
        } catch (e: AnthropicIoException) {
            throw AiException("Couldn't reach Claude. Check your connection.")
        }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        /** Used until the account's own model list has been fetched. */
        val FALLBACK_LIST = listOf(
            AiModel("claude-opus-5-5", "Claude Opus 5.5", supportsEffort = true, maxOutput = 128_000),
            AiModel("claude-sonnet-5-5", "Claude Sonnet 5.5", supportsEffort = true, maxOutput = 128_000),
            AiModel("claude-haiku-5-5", "Claude Haiku 5.5", supportsEffort = true, maxOutput = 128_000),
        )

        /** Models that support server-side refusal fallbacks. */
        private val FALLBACK_MODELS = setOf(
            "claude-fable-5-2", "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5",
        )

        private const val MAX_TOKENS = 64_000L
    }
}
