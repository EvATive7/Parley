package io.github.evative7.parley.asr

import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class VolcengineRecognizerTest {
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

    @Test fun `binary protocol round trips gzip JSON response and last sequence`() {
        val json = """{"result":{"text":"ok"}}""".toByteArray()
        val raw = VolcengineProtocol.frame(
            VolcengineProtocol.SERVER_FULL_RESPONSE,
            VolcengineProtocol.NEG_WITH_SEQUENCE,
            -3,
            json,
            VolcengineProtocol.JSON_SERIALIZATION,
        )
        val parsed = VolcengineProtocol.parse(raw)!!
        assertTrue(parsed.isLast)
        assertEquals(-3, parsed.sequence)
        assertEquals("ok", parsed.json!!["result"]!!.toString().substringAfter("text\":\"").substringBefore('"'))
    }

    @Test fun `streaming sends provider headers frames partials and final text`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val raw = bytes.toByteArray()
                val type = (raw[1].toInt() ushr 4) and 0xf
                val flags = raw[1].toInt() and 0xf
                if (type == VolcengineProtocol.CLIENT_AUDIO_ONLY_REQUEST && flags == VolcengineProtocol.POS_SEQUENCE) {
                    webSocket.send(serverPacket("""{"result":{"utterances":[{"text":"你","definite":false}]}}""", false))
                }
                if (type == VolcengineProtocol.CLIENT_AUDIO_ONLY_REQUEST && flags == VolcengineProtocol.NEG_WITH_SEQUENCE) {
                    webSocket.send(serverPacket("""{"result":{"utterances":[{"text":"你好","definite":true,"start_time":0,"end_time":500}]}}""", true))
                }
            }
        }))
        val session = VolcengineSaucSession(
            client,
            server.url("/api/v3/sauc/bigmodel_async").toString().replace("http://", "ws://"),
            "app-id",
            "access-token",
            "zh-CN",
        )
        session.feed(ShortArray(320))
        assertEquals("你好", withTimeout(5_000) { session.finish() }.text)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("app-id", request.getHeader("X-Api-App-Key"))
        assertEquals("access-token", request.getHeader("X-Api-Access-Key"))
        assertEquals("volc.seedasr.sauc.duration", request.getHeader("X-Api-Resource-Id"))
        assertTrue(request.getHeader("X-Api-Connect-Id")!!.isNotBlank())
    }

    @Test fun `streaming maps rate limit handshake`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        val session = VolcengineSaucSession(
            client,
            server.url("/sauc").toString().replace("http://", "ws://"),
            "app",
            "token",
            null,
        )
        try {
            withTimeout(5_000) { session.finish() }
            fail("Expected rate limit")
        } catch (e: ServiceException) {
            assertEquals(ServiceFailure.RateLimit, e.failure)
        }
    }

    @Test fun `turbo sends WAV body resource ID and extracts utterances`() = runBlocking {
        server.enqueue(volcResponse("20000000", """{"result":{"utterances":[{"text":"你"},{"text":"好"}]}}"""))
        val endpoints = VolcAucEndpoints(
            server.url("/submit").toString(), server.url("/query").toString(), server.url("/flash").toString(),
        )
        val session = VolcengineAucSession(client, endpoints, VolcAucMode.Turbo, "app", "token", 0, 2)
        session.feed(shortArrayOf(1, 2))
        assertEquals("你好", session.finish().text)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/flash", request.path)
        assertEquals("volc.bigasr.auc_turbo", request.getHeader("X-Api-Resource-Id"))
        assertTrue(request.body.readUtf8().contains("\"model_name\":\"bigmodel\""))
    }

    @Test fun `standard submit polls until complete with one request ID`() = runBlocking {
        server.enqueue(volcResponse("20000000", "{}"))
        server.enqueue(volcResponse("20000001", "{}"))
        server.enqueue(volcResponse("20000000", """{"result":{"text":"done"}}"""))
        val endpoints = VolcAucEndpoints(
            server.url("/submit").toString(), server.url("/query").toString(), server.url("/flash").toString(),
        )
        val session = VolcengineAucSession(client, endpoints, VolcAucMode.Standard, "app", "token", 0, 3)
        assertEquals("done", session.finish().text)
        val requests = List(3) { server.takeRequest(2, TimeUnit.SECONDS)!! }
        assertEquals(listOf("/submit", "/query", "/query"), requests.map { it.path })
        assertEquals(1, requests.map { it.getHeader("X-Api-Request-Id") }.distinct().size)
    }

    @Test fun `AUC maps HTTP authentication failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val endpoints = VolcAucEndpoints("", "", server.url("/flash").toString())
        val session = VolcengineAucSession(client, endpoints, VolcAucMode.Turbo, "app", "bad", 0, 1)
        try {
            session.finish()
            fail("Expected authentication failure")
        } catch (e: ServiceException) {
            assertEquals(ServiceFailure.Authentication, e.failure)
        }
    }

    private fun serverPacket(json: String, last: Boolean): ByteString = VolcengineProtocol.frame(
        VolcengineProtocol.SERVER_FULL_RESPONSE,
        if (last) VolcengineProtocol.NEG_WITH_SEQUENCE else VolcengineProtocol.POS_SEQUENCE,
        if (last) -1 else 1,
        json.toByteArray(),
        VolcengineProtocol.JSON_SERIALIZATION,
    ).toByteString()

    private fun volcResponse(status: String, body: String) = MockResponse()
        .setHeader("X-Api-Status-Code", status)
        .setBody(body)
}
