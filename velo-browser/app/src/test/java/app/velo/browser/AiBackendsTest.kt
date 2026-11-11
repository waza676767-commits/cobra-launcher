package app.velo.browser

import app.velo.browser.ai.AiException
import app.velo.browser.ai.AiModel
import app.velo.browser.ai.ChatTurn
import app.velo.browser.ai.ClaudeBackend
import app.velo.browser.ai.GeminiBackend
import app.velo.browser.ai.OpenAiBackend
import app.velo.browser.ai.StreamSink
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each provider is exercised against a local server that replays its real wire format. */
@RunWith(RobolectricTestRunner::class)
class AiBackendsTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    private class Collector : StreamSink {
        val text = StringBuilder()
        val notices = ArrayList<String>()
        override fun onText(delta: String) {
            text.append(delta)
        }

        override fun onNotice(text: String) {
            notices += text
        }
    }

    private val turns = listOf(ChatTurn(true, "Hi"), ChatTurn(false, "Hello!"), ChatTurn(true, "Tell me more"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sse(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    // ── Claude ─────────────────────────────────────────────────────────────────────────────────

    private fun claudeStream(vararg deltas: String, stop: String = """{"stop_reason":"end_turn","stop_sequence":null}"""): String {
        val b = StringBuilder()
        b.append("event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":1}}}\n\n")
        b.append("event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n")
        for (d in deltas) {
            b.append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":${JSONObject.quote(d)}}}\n\n")
        }
        b.append("event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}\n\n")
        b.append("event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":$stop,\"usage\":{\"output_tokens\":5}}\n\n")
        b.append("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        return b.toString()
    }

    private val opus = AiModel("claude-opus-5-5", "Claude Opus 5.5", supportsEffort = true, maxOutput = 128_000)

    @Test
    fun claudeStreamsTextAndSendsTheExpectedRequest() {
        server.enqueue(sse(claudeStream("Hello", " there", "!")))
        val sink = Collector()
        ClaudeBackend({ null }, server.url("/").toString()).stream("sk-ant-test", opus, "Be brief.", turns, sink)

        assertEquals("Hello there!", sink.text.toString())
        assertTrue(sink.notices.isEmpty())
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/v1/messages"))
        val credential = request.getHeader("x-api-key") ?: request.getHeader("Authorization").orEmpty()
        assertTrue("the key is sent as the credential", credential.endsWith("sk-ant-test"))
        assertTrue(request.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("claude-opus-5-5", body.getString("model"))
        assertEquals(64_000, body.getInt("max_tokens"))
        assertTrue(body.getBoolean("stream"))
        assertEquals("default", body.getString("fallbacks"))
        assertEquals("medium", body.getJSONObject("output_config").getString("effort"))
        assertFalse("thinking is left to the model's default", body.has("thinking"))
        assertFalse(body.has("temperature"))
        val messages = body.getJSONArray("messages")
        assertEquals(3, messages.length())
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
    }

    @Test
    fun claudeRetriesWithoutFallbacksWhenTheAccountRejectsThem() {
        server.enqueue(
            MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setBody("""{"type":"error","error":{"type":"invalid_request_error","message":"Unexpected value(s) for the anthropic-beta header."}}""")
        )
        server.enqueue(sse(claudeStream("Still works")))
        val sink = Collector()
        ClaudeBackend({ null }, server.url("/").toString()).stream("k", opus, "s", turns, sink)

        assertEquals("Still works", sink.text.toString())
        server.takeRequest()
        val second = server.takeRequest()
        assertFalse(JSONObject(second.body.readUtf8()).has("fallbacks"))
        assertNull(second.getHeader("anthropic-beta"))
    }

    @Test
    fun claudeOlderModelGetsNoEffortOrFallbacksAndASmallerCap() {
        server.enqueue(sse(claudeStream("ok")))
        val haiku = AiModel("claude-haiku-4-5", "Claude Haiku 4.5", supportsEffort = false, maxOutput = 32_000)
        ClaudeBackend({ null }, server.url("/").toString()).stream("k", haiku, "s", turns, Collector())
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(body.has("output_config"))
        assertFalse(body.has("fallbacks"))
        assertEquals(32_000, body.getInt("max_tokens"))
    }

    @Test
    fun claudeRefusalBecomesANoticeNotAnError() {
        val stop = """{"stop_reason":"refusal","stop_sequence":null,"stop_details":{"type":"refusal","category":"cyber","explanation":"I can't help with that request."}}"""
        server.enqueue(sse(claudeStream(stop = stop)))
        val sink = Collector()
        ClaudeBackend({ null }, server.url("/").toString()).stream("k", opus, "s", turns, sink)
        assertEquals(listOf("I can't help with that request."), sink.notices)
    }

    @Test
    fun claudeBadKeyIsReportedAsSuch() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""")
        )
        try {
            ClaudeBackend({ null }, server.url("/").toString()).stream("bad", opus, "s", turns, Collector())
            fail("expected an error")
        } catch (e: AiException) {
            assertTrue(e.badKey)
        }
    }

    @Test
    fun claudeModelListComesFromTheAccount() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"data":[
                  {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-09-01T00:00:00Z","max_input_tokens":1000000,"max_tokens":128000,
                   "capabilities":{"batch":{"supported":true},"citations":{"supported":true},"code_execution":{"supported":true},
                     "context_management":{"supported":true,"clear_thinking_20251015":{"supported":true},"clear_tool_uses_20250919":{"supported":true},"compact_20260112":{"supported":true}},
                     "effort":{"supported":true,"low":{"supported":true},"medium":{"supported":true},"high":{"supported":true},"max":{"supported":true},"xhigh":{"supported":true}},
                     "image_input":{"supported":true},"pdf_input":{"supported":true},"structured_outputs":{"supported":true},
                     "thinking":{"supported":true,"types":{"adaptive":{"supported":true},"enabled":{"supported":false}}}}},
                  {"type":"model","id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5","created_at":"2025-10-01T00:00:00Z"}
                ],"has_more":false,"first_id":"claude-opus-5-5","last_id":"claude-haiku-4-5"}"""
            )
        )
        val backend = ClaudeBackend({ null }, server.url("/").toString())
        val models = backend.listModels("k")
        assertEquals(listOf("claude-opus-5-5", "claude-haiku-4-5"), models.map { it.id })
        assertTrue(models[0].supportsEffort)
        assertEquals(128_000L, models[0].maxOutput)
        assertFalse(models[1].supportsEffort)
        assertEquals("claude-opus-5-5", backend.defaultModel(models.reversed())!!.id)
    }

    // ── ChatGPT ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun openAiStreamsTextAndListsOnlyChatModels() {
        server.enqueue(
            sse(
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hel\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
                    "data: [DONE]\n\n"
            )
        )
        val backend = OpenAiBackend(http, server.url("").toString().trimEnd('/'))
        val sink = Collector()
        backend.stream("sk-test", AiModel("gpt-5", "gpt-5"), "Be brief.", turns, sink)
        assertEquals("Hello", sink.text.toString())
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertEquals(4, body.getJSONArray("messages").length())

        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"gpt-5","created":300},{"id":"text-embedding-3-large","created":400},
                   {"id":"gpt-5-mini","created":310},{"id":"gpt-4o-realtime-preview","created":350},
                   {"id":"o3","created":200},{"id":"dall-e-3","created":100},{"id":"gpt-image-1","created":360}]}"""
            )
        )
        val models = backend.listModels("sk-test")
        assertEquals(listOf("gpt-5-mini", "gpt-5", "o3"), models.map { it.id })
        assertEquals("gpt-5", backend.defaultModel(models)!!.id)
    }

    @Test
    fun openAiErrorsCarryTheProvidersMessage() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Incorrect API key provided"}}"""))
        try {
            OpenAiBackend(http, server.url("").toString().trimEnd('/')).listModels("nope")
            fail("expected an error")
        } catch (e: AiException) {
            assertTrue(e.badKey)
        }
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"You exceeded your current quota"}}"""))
        try {
            OpenAiBackend(http, server.url("").toString().trimEnd('/')).stream("k", AiModel("gpt-5", "gpt-5"), "s", turns, Collector())
            fail("expected an error")
        } catch (e: AiException) {
            assertEquals("You exceeded your current quota", e.message)
        }
    }

    // ── Gemini ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun geminiStreamsTextSkipsThoughtsAndListsChatModels() {
        server.enqueue(
            sse(
                "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"thinking...\",\"thought\":true}]}}]}\n\n" +
                    "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Bon\"}]}}]}\n\n" +
                    "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"jour\"}]},\"finishReason\":\"STOP\"}]}\n\n"
            )
        )
        val backend = GeminiBackend(http, server.url("").toString().trimEnd('/'))
        val sink = Collector()
        backend.stream("AIza-test", AiModel("gemini-2.5-flash", "Gemini 2.5 Flash"), "Be brief.", turns, sink)
        assertEquals("Bonjour", sink.text.toString())
        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse", request.path)
        assertEquals("AIza-test", request.getHeader("x-goog-api-key"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("model", body.getJSONArray("contents").getJSONObject(1).getString("role"))
        assertEquals("Be brief.", body.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text"))

        server.enqueue(
            MockResponse().setBody(
                """{"models":[
                  {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","supportedGenerationMethods":["generateContent","countTokens"]},
                  {"name":"models/gemini-2.5-pro","displayName":"Gemini 2.5 Pro","supportedGenerationMethods":["generateContent"]},
                  {"name":"models/gemini-embedding-001","displayName":"Embedding","supportedGenerationMethods":["embedContent"]},
                  {"name":"models/gemini-2.5-flash-image","displayName":"Image","supportedGenerationMethods":["generateContent"]}
                ]}"""
            )
        )
        val models = backend.listModels("AIza-test")
        assertEquals(setOf("gemini-2.5-flash", "gemini-2.5-pro"), models.map { it.id }.toSet())
        assertEquals("gemini-2.5-flash", backend.defaultModel(models)!!.id)
    }
}
