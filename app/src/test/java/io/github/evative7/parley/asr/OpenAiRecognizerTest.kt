package io.github.evative7.parley.asr

import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.decodeBase64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OpenAiRecognizerTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()

    @Before fun setup() = server.start()
    @After fun teardown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        // A server-side WebSocket thread can outlive the 5 s shutdown wait on a slow CI runner even
        // though the client already cancelled its socket; that says nothing about the code under test.
        runCatching { server.shutdown() }
        client.dispatcher.executorService.shutdown()
    }

    @Test fun `file transcription sends authenticated multipart WAV and model`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"text\":\"Hello world\"}"))
        val session = OpenAiTranscriptionSession(
            client, server.url("/v1/audio/transcriptions").toString(),
            "gpt-4o-transcribe", "openai-key", "en-US",
        )
        session.feed(shortArrayOf(0x1234, -2))
        assertEquals("Hello world", session.finish().text)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("Bearer openai-key", request.getHeader("Authorization"))
        val multipart = request.body.readUtf8()
        assertTrue(multipart.contains("gpt-4o-transcribe"))
        assertTrue(multipart.contains("speech.wav"))
        assertTrue(multipart.contains("WAVE"))
        assertTrue(multipart.contains("\r\n\r\nen\r\n"))
    }

    @Test fun `file transcription maps authentication failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val session = OpenAiTranscriptionSession(
            client, server.url("/transcriptions").toString(), "gpt-4o-mini-transcribe", "bad", null,
        )
        try {
            session.finish()
            fail("Expected authentication failure")
        } catch (e: ServiceException) {
            assertEquals(ServiceFailure.Authentication, e.failure)
        }
    }

    @Test fun `realtime configures transcription resamples audio and commits on release`() = runBlocking {
        val appendBytes = AtomicInteger()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = Json.parseToJsonElement(text).jsonObject
                when (event["type"]!!.jsonPrimitive.content) {
                    "session.update" -> {
                        val session = event["session"]!!.jsonObject
                        assertEquals("transcription", session["type"]!!.jsonPrimitive.content)
                    }
                    "input_audio_buffer.append" -> {
                        appendBytes.addAndGet(event["audio"]!!.jsonPrimitive.content.decodeBase64()!!.size)
                    }
                    "input_audio_buffer.commit" -> {
                        webSocket.send("""{"type":"conversation.item.input_audio_transcription.delta","delta":"Hello"}""")
                        webSocket.send("""{"type":"conversation.item.input_audio_transcription.completed","transcript":"Hello world"}""")
                    }
                }
            }
        }))
        val session = OpenAiRealtimeSession(
            client,
            server.url("/v1/realtime?model=gpt-realtime-2").toString().replace("http://", "ws://"),
            "openai-key",
            "en",
        )
        val partial = launch { assertEquals("Hello", session.partials.first { it.isNotEmpty() }) }
        session.feed(ShortArray(320) { it.toShort() })
        assertEquals("Hello world", withTimeout(5_000) { session.finish() }.text)
        partial.join()
        assertEquals(960, appendBytes.get())
        assertEquals("Bearer openai-key", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `realtime maps rate limit handshake`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        val session = OpenAiRealtimeSession(
            client,
            server.url("/realtime").toString().replace("http://", "ws://"),
            "bad",
            null,
        )
        try {
            withTimeout(5_000) { session.finish() }
            fail("Expected rate limit")
        } catch (e: ServiceException) {
            assertEquals(ServiceFailure.RateLimit, e.failure)
        }
    }
}
