package app.velo

import app.velo.data.Store
import app.velo.voice.Assistant
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real SDK request code against a local fake API server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantTest {
    private lateinit var server: MockWebServer
    private val bodies = ArrayList<String>()
    private var responses = ArrayDeque<Pair<Int, String>>()

    private val ok = """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
        "content":[{"type":"text","text":"Hi, I'm Velo!"}],"stop_reason":"end_turn","stop_sequence":null,
        "usage":{"input_tokens":10,"output_tokens":5}}"""

    @Before fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                bodies += request.body.readUtf8()
                val (code, body) = responses.removeFirstOrNull() ?: (200 to ok)
                return MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        Assistant.baseUrl = server.url("/").toString().trimEnd('/')
        Store.update { it.copy(apiKey = "sk-test") }
        Assistant.addUserForTest("hello")
    }

    @After fun stop() { server.shutdown() }

    @Test fun answersAndSendsPersona() {
        val (spoken, detail) = Assistant.askSafely("sk-test")
        assertEquals("detail=$detail", "Hi, I'm Velo!", spoken)
        assertEquals(null, detail)
        assertTrue(bodies[0].contains("You are Velo"))
        assertTrue(bodies[0].contains("claude-opus-5-5"))
    }

    @Test fun retriesWithoutExtrasWhenRejected() {
        responses += 400 to """{"type":"error","error":{"type":"invalid_request_error","message":"web search is not enabled"}}"""
        val (spoken, _) = Assistant.askSafely("sk-test2")
        assertEquals("Hi, I'm Velo!", spoken)
        assertTrue(bodies[0].contains("web_search"))
        assertFalse(bodies[1].contains("web_search"))
    }

    @Test fun explainsNoCredit() {
        val err = """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""
        responses += 400 to err; responses += 400 to err
        val (spoken, detail) = Assistant.askSafely("sk-test3")
        assertTrue(spoken, spoken.contains("credit"))
        assertTrue(detail!!.contains("credit balance"))
    }
}
