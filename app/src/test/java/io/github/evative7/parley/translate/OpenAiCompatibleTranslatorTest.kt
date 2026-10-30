package io.github.evative7.parley.translate

import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OpenAiCompatibleTranslatorTest {
    private val server = MockWebServer()
    private val cancelled = CountDownLatch(1)
    private val config = ServiceSettings(dashScopeKey = "test-asr", translationKey = "test-llm")
    private var current: ServiceSettings? = config
    private lateinit var client: OkHttpClient
    private lateinit var translator: OpenAiCompatibleTranslator

    @Before fun setup() {
        server.start()
        // Only tests rewrite HTTPS to a local HTTP server; production settings reject cleartext.
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
            }
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            })
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        translator = OpenAiCompatibleTranslator(client, object : ServiceSettingsSource {
            override val current get() = this@OpenAiCompatibleTranslatorTest.current
        })
    }

    @After fun teardown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun response(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
    private fun delta(text: String) = "data: {\"choices\":[{\"delta\":{\"content\":\"$text\"},\"finish_reason\":null}]}\n\n"
    private fun translate() = translator.translate("你好", Lang("zh"), Lang("en"))

    @Test fun `streams fragments and sends prompt model authentication and v1 path`() = runBlocking {
        server.enqueue(response(": heartbeat\n\n" + delta("Hello") + delta("!") + "data: [DONE]\n\n"))
        assertEquals(listOf("Hello", "!"), withTimeout(5000) { translate().toList() })
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-llm", request.getHeader("Authorization"))
        val json = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("gpt-4o-mini", json["model"]!!.jsonPrimitive.content)
        assertEquals("true", json["stream"]!!.jsonPrimitive.content)
        val messages = json["messages"]!!.jsonArray
        assertTrue(messages[0].jsonObject["content"]!!.jsonPrimitive.content.contains("English"))
        assertEquals("你好", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test fun `maps HTTP auth and rate limiting without exposing response bodies`() = runBlocking {
        for ((code, failure) in listOf(401 to ServiceFailure.Authentication, 403 to ServiceFailure.Permission, 429 to ServiceFailure.RateLimit, 500 to ServiceFailure.Response)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("secret-body"))
            try {
                translate().toList()
                fail("Expected failure")
            } catch (e: ServiceException) {
                assertEquals(failure, e.failure)
                assertFalse(e.message.orEmpty().contains("secret-body"))
            }
        }
    }

    @Test fun `rejects truncated malformed empty and provider-error responses`() = runBlocking {
        for (body in listOf(delta("partial"), "data: not-json\n\n", "data: [DONE]\n\n", "data: {\"error\":{\"message\":\"private\"}}\n\n", delta("partial") + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n")) {
            server.enqueue(response(body))
            try {
                translate().toList()
                fail("Expected invalid response failure")
            } catch (e: ServiceException) {
                assertEquals(ServiceFailure.Response, e.failure)
            }
        }
    }

    @Test fun `usage packets and role-only deltas are ignored`() = runBlocking {
        server.enqueue(response(
            "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n" +
                delta("Hello") +
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
                "data: {\"choices\":[],\"usage\":{}}\n\ndata: [DONE]\n\n",
        ))
        assertEquals(listOf("Hello"), translate().toList())
    }

    @Test fun `cancelling the consumer cancels the HTTP call`() = runBlocking {
        server.enqueue(response(delta("first") + delta("second")).throttleBody(1, 1, TimeUnit.MILLISECONDS))
        assertEquals(listOf("first"), withTimeout(5000) { translate().take(1).toList() })
        assertTrue(cancelled.await(2, TimeUnit.SECONDS))
    }

    @Test fun `read timeout is classified separately from connection loss`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            withTimeout(5000) { translate().toList() }
            fail("Expected timeout")
        } catch (e: ServiceException) { assertEquals(ServiceFailure.Timeout, e.failure) }
    }

    @Test fun `unconfigured translator never contacts the server`() = runBlocking {
        current = null
        try { translate().toList(); fail("Expected configuration failure") } catch (e: ServiceException) {
            assertEquals(ServiceFailure.Configuration, e.failure)
        }
        assertEquals(0, server.requestCount)
    }
}
