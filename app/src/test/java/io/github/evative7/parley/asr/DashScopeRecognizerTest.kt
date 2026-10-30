package io.github.evative7.parley.asr

import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DashScopeRecognizerTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
    private var session: RecognitionSession? = null

    @Before fun setup() { server.start() }
    @After fun teardown() {
        session?.cancel()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun config() = DashScopeConfig(
        url = server.url("/api-ws/v1/inference").toString().replace("http://", "ws://"),
        key = "test-asr",
    )
    private fun packet(event: String, taskId: String, sentence: String? = null, error: String? = null) = buildJsonObject {
        put("header", buildJsonObject {
            put("event", event)
            put("task_id", taskId)
            if (error != null) put("error_code", error)
        })
        put("payload", buildJsonObject {
            if (sentence != null) put("output", Json.parseToJsonElement(sentence))
        })
    }.toString()

    @Test fun `buffers until task-started sends LE PCM then finish and reconciles revisions`() = runBlocking {
        val audio = AtomicReference<ByteString>()
        val order = mutableListOf<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val root = Json.parseToJsonElement(text).jsonObject
                val header = root["header"]!!.jsonObject
                val id = header["task_id"]!!.jsonPrimitive.content
                when (val action = header["action"]!!.jsonPrimitive.content) {
                    "run-task" -> {
                        synchronized(order) { order += action }
                        val payload = root["payload"]!!.jsonObject
                        assertEquals("fun-asr-realtime", payload["model"]!!.jsonPrimitive.content)
                        assertEquals("16000", payload["parameters"]!!.jsonObject["sample_rate"]!!.jsonPrimitive.content)
                        webSocket.send(packet("task-started", id))
                    }
                    "finish-task" -> {
                        synchronized(order) { order += action }
                        webSocket.send(packet("result-generated", "unrelated", "{\"sentence\":{\"begin_time\":0,\"text\":\"ignore\"}}"))
                        webSocket.send(packet("result-generated", id, "{\"sentence\":{\"begin_time\":0,\"text\":\"你\"}}"))
                        webSocket.send(packet("result-generated", id, "{\"sentence\":{\"begin_time\":0,\"text\":\"你好。\",\"end_time\":1000}}"))
                        webSocket.send(packet("result-generated", id, "{\"sentence\":{\"begin_time\":0,\"text\":\"你好。\",\"end_time\":1000}}"))
                        webSocket.send(packet("result-generated", id, "{\"sentence\":{\"begin_time\":1000,\"end_time\":1600,\"text\":\"再见。\"}}"))
                        webSocket.send(packet("task-finished", id))
                    }
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                audio.set(bytes)
                synchronized(order) { order += "audio" }
            }
        }))
        val s = DashScopeSession(client, config()).also { session = it }
        s.feed(shortArrayOf(0x1234, -2))
        val result = withTimeout(5000) { s.finish() }
        assertEquals("你好。再见。", result.text)
        assertEquals(listOf("run-task", "audio", "finish-task"), synchronized(order) { order.toList() })
        assertArrayEquals(byteArrayOf(0x34, 0x12, -2, -1), audio.get().toByteArray())
        assertEquals("Bearer test-asr", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `task failure is mapped and wakes finish`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val id = Json.parseToJsonElement(text).jsonObject["header"]!!.jsonObject["task_id"]!!.jsonPrimitive.content
                webSocket.send(packet("task-failed", id, error = "InvalidApiKey"))
            }
        }))
        val s = DashScopeSession(client, config()).also { session = it }
        try {
            withTimeout(5000) { s.finish() }
            fail("Expected authentication failure")
        } catch (e: ServiceException) { assertEquals(ServiceFailure.Authentication, e.failure) }
    }

    @Test fun `cancel before task-started never flushes buffered audio`() = runBlocking {
        val opened = CountDownLatch(1)
        val serverSocket = AtomicReference<WebSocket>()
        val task = AtomicReference<String>()
        val sawAudio = CountDownLatch(1)
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { serverSocket.set(webSocket) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                task.set(Json.parseToJsonElement(text).jsonObject["header"]!!.jsonObject["task_id"]!!.jsonPrimitive.content)
                opened.countDown()
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { sawAudio.countDown() }
        }))
        val s = DashScopeSession(client, config()).also { session = it }
        s.feed(ShortArray(320))
        assertTrue(opened.await(2, TimeUnit.SECONDS))
        s.cancel()
        serverSocket.get().send(packet("task-started", task.get()))
        assertFalse(sawAudio.await(150, TimeUnit.MILLISECONDS))
    }

    @Test fun `buffer growth is bounded while waiting for task-started`() {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        val s = DashScopeSession(client, config()).also { session = it }
        try {
            repeat(501) { s.feed(ShortArray(320)) }
            fail("Expected bounded buffer failure")
        } catch (e: ServiceException) { assertEquals(ServiceFailure.Network, e.failure) }
    }


    private suspend fun recognizeSentences(vararg sentences: String): Transcript {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val header = Json.parseToJsonElement(text).jsonObject["header"]!!.jsonObject
                val id = header["task_id"]!!.jsonPrimitive.content
                when (header["action"]!!.jsonPrimitive.content) {
                    "run-task" -> webSocket.send(packet("task-started", id))
                    "finish-task" -> {
                        sentences.forEach { webSocket.send(packet("result-generated", id, it)) }
                        webSocket.send(packet("task-finished", id))
                    }
                }
            }
        }))
        val s = DashScopeSession(client, config()).also { session = it }
        return withTimeout(5000) { s.finish() }
    }

    @Test fun `moving partial begin times do not accumulate stale words`() = runBlocking {
        val result = recognizeSentences(
            """{"sentence":{"begin_time":0,"end_time":null,"text":"garbage"}}""",
            """{"sentence":{"begin_time":40,"end_time":null,"text":"Hello"}}""",
            """{"sentence":{"begin_time":80,"end_time":900,"text":"Hello world."}}""",
            """{"sentence":{"begin_time":80,"end_time":900,"text":"Hello world."}}""",
            """{"sentence":{"begin_time":1000,"end_time":null,"text":"再"}}""",
            """{"sentence":{"begin_time":1040,"end_time":1600,"text":"再见。"}}""",
        )
        assertEquals("Hello world.再见。", result.text)
    }

    @Test fun `task-finished cannot promote an unfinished draft into final text`() = runBlocking {
        try {
            recognizeSentences("""{"sentence":{"begin_time":0,"end_time":null,"text":"unfinished"}}""")
            fail("Expected incomplete response")
        } catch (e: ServiceException) { assertEquals(ServiceFailure.Response, e.failure) }
    }

    @Test fun `access denied survives the socket teardown and diagnostics redact keys`() = runBlocking {
        val failed = CountDownLatch(1)
        val peer = AtomicReference<WebSocket>()
        val reports = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { peer.set(webSocket) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val id = Json.parseToJsonElement(text).jsonObject["header"]!!.jsonObject["task_id"]!!.jsonPrimitive.content
                webSocket.send(packet("task-failed", id, error = "AccessDenied"))
            }
        }))
        val s = DashScopeSession(client, config()) {
            reports.add(it)
            if (it.contains("AccessDenied")) failed.countDown()
        }.also { session = it }
        assertTrue(failed.await(2, TimeUnit.SECONDS))
        fun assertPermission() {
            try { s.feed(shortArrayOf(1)); fail("Expected permission failure") }
            catch (e: ServiceException) { assertEquals(ServiceFailure.Permission, e.failure) }
        }
        assertPermission()
        s.onFailure(peer.get(), java.io.IOException("Socket closed test-asr"), null)
        assertPermission()
        try { s.finish(); fail("Expected permission failure") }
        catch (e: ServiceException) { assertEquals(ServiceFailure.Permission, e.failure) }
        assertTrue(reports.none { it.contains("test-asr") })
    }

    @Test fun `batches 20 ms frames and flushes the exact tail before finish`() = runBlocking {
        val frames = java.util.Collections.synchronizedList(mutableListOf<ByteString>())
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { frames.add(bytes) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val header = Json.parseToJsonElement(text).jsonObject["header"]!!.jsonObject
                val id = header["task_id"]!!.jsonPrimitive.content
                when (header["action"]!!.jsonPrimitive.content) {
                    "run-task" -> webSocket.send(packet("task-started", id))
                    "finish-task" -> webSocket.send(packet("task-finished", id))
                }
            }
        }))
        val s = DashScopeSession(client, config()) { error("An observer cannot break the session") }
            .also { session = it }
        repeat(7) { s.feed(ShortArray(320) { 0x1234 }) }
        withTimeout(5000) { s.finish() }
        assertEquals(listOf(3200, 1280), frames.map { it.size })
        assertTrue(frames.flatMap { it.toByteArray().asIterable() }.chunked(2).all {
            it == listOf(0x34.toByte(), 0x12.toByte())
        })
    }

    private fun answering(onRunTask: (WebSocket, String) -> Unit = { ws, id -> ws.send(packet("task-started", id)) }) =
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val header = Json.parseToJsonElement(text).jsonObject["header"]!!.jsonObject
                val id = header["task_id"]!!.jsonPrimitive.content
                when (header["action"]!!.jsonPrimitive.content) {
                    "run-task" -> onRunTask(webSocket, id)
                    "finish-task" -> {
                        webSocket.send(packet("result-generated", id, """{"sentence":{"begin_time":0,"end_time":500,"text":"ok"}}"""))
                        webSocket.send(packet("task-finished", id))
                    }
                }
            }
        })

    @Test fun `adopts a spare socket without a second handshake`() = runBlocking {
        server.enqueue(answering())
        val warm = WarmSocket(client, config())
        server.takeRequest(2, TimeUnit.SECONDS)
        Thread.sleep(100) // let the client see the 101 first: adopt must replay onOpen
        val s = DashScopeSession(client, config(), warm).also { session = it }
        s.feed(ShortArray(320))
        assertEquals("ok", withTimeout(5000) { s.finish() }.text)
        assertEquals(1, server.requestCount)
    }

    @Test fun `spare dropped before task-started reconnects once and keeps the audio`() = runBlocking {
        val audioOnFresh = CountDownLatch(1)
        server.enqueue(answering { ws, _ -> ws.close(1001, "idle") })
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { audioOnFresh.countDown() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val header = Json.parseToJsonElement(text).jsonObject["header"]!!.jsonObject
                val id = header["task_id"]!!.jsonPrimitive.content
                when (header["action"]!!.jsonPrimitive.content) {
                    "run-task" -> webSocket.send(packet("task-started", id))
                    "finish-task" -> {
                        webSocket.send(packet("result-generated", id, """{"sentence":{"begin_time":0,"end_time":500,"text":"ok"}}"""))
                        webSocket.send(packet("task-finished", id))
                    }
                }
            }
        }))
        val s = DashScopeSession(client, config(), WarmSocket(client, config())).also { session = it }
        s.feed(ShortArray(1600)) // one full 100 ms network message
        assertTrue(audioOnFresh.await(2, TimeUnit.SECONDS))
        assertEquals("ok", withTimeout(5000) { s.finish() }.text)
        assertEquals(2, server.requestCount)
    }

    @Test fun `an idle spare that dies reports its age and is never adopted`() {
        val died = CountDownLatch(1)
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.close(1001, "idle") }
        }))
        val warm = WarmSocket(client, config()) { _, _ -> died.countDown() }
        assertTrue(died.await(2, TimeUnit.SECONDS))
        assertFalse(warm.isUsableFor(config()))
        assertFalse(warm.adopt(object : WebSocketListener() {}))
    }

    @Test fun `non-stream mode opens the socket only after release`() = runBlocking {
        server.enqueue(answering())
        val buffered = DashScopeBufferedSession(client, config()).also { session = it }
        buffered.feed(ShortArray(320))
        assertEquals(0, server.requestCount)
        assertEquals("ok", withTimeout(5000) { buffered.finish() }.text)
        assertEquals(1, server.requestCount)
    }

    @Test fun `non-stream mode takes the spare socket on release, not on press`() = runBlocking {
        server.enqueue(answering())
        val warm = WarmSocket(client, config())
        var taken = 0
        val buffered = DashScopeBufferedSession(client, config()) { taken++; warm }.also { session = it }
        buffered.feed(ShortArray(320))
        assertEquals(0, taken)
        assertEquals("ok", withTimeout(5000) { buffered.finish() }.text)
        assertEquals(1, taken)
        assertEquals("the spare's handshake is the only one", 1, server.requestCount)
    }

    @Test fun `unconfigured recognizer does not contact the service`() {
        val recognizer = DashScopeRecognizer(client, object : ServiceSettingsSource {
            override val current = ServiceSettings()
        })
        try { recognizer.open(null); fail("Expected configuration failure") } catch (e: ServiceException) {
            assertEquals(ServiceFailure.Configuration, e.failure)
        }
        assertEquals(0, server.requestCount)
    }
}
