package io.github.evative7.parley.asr

import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.network.httpFailure
import io.github.evative7.parley.network.networkFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TreeMap
import java.util.UUID
import javax.inject.Inject

class DashScopeRecognizer @Inject constructor(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
) : ServiceRecognizer {
    // Guarded by `this`.
    private var spare: WarmSocket? = null
    private var foreground = false

    override fun prepare(service: AsrService): Unit = synchronized(this) {
        foreground = true
        // Both modes use the same socket: streaming adopts it on press, buffered on release.
        val saved = settings.current?.takeIf {
            it.isReady && service.provider == AsrProvider.DashScope
        } ?: return
        val config = dashScopeConfig(saved.dashScopeRegion.host, saved.dashScopeKey)
        if (spare?.isUsableFor(config) == true) return
        spare?.close()
        DebugLog.log(TAG) { "opening spare socket" }
        spare = WarmSocket(client, config, onDied = ::spareDied)
    }

    override fun release(): Unit = synchronized(this) {
        foreground = false
        spare?.close()
        spare = null
    }

    override fun open(service: AsrService, lang: String?): RecognitionSession {
        val saved = settings.current?.takeIf { it.isReady && service.provider == AsrProvider.DashScope }
            ?: throw ServiceException(ServiceFailure.Configuration)
        val config = dashScopeConfig(saved.dashScopeRegion.host, saved.dashScopeKey)
        if (service == AsrService.FunAsrRealtimeNonStream) {
            return DashScopeBufferedSession(client, config) { takeSpare(config) }
        }
        return DashScopeSession(client, config, takeSpare(config), DebugLog.sink(TAG))
    }

    private fun takeSpare(config: DashScopeConfig): WarmSocket? {
        val warm = synchronized(this) { spare.also { spare = null } }
        if (warm != null && !warm.isUsableFor(config)) warm.close()
        val adopted = warm?.takeIf { it.isUsableFor(config) }
        DebugLog.log(TAG) { if (adopted != null) "session on spare socket" else "session on new socket (no usable spare)" }
        return adopted
    }

    /** The provider drops idle sockets; keep one ready while the screen is visible. */
    private fun spareDied(socket: WarmSocket, ageMs: Long): Unit = synchronized(this) {
        if (spare !== socket) return
        spare = null
        DebugLog.log(TAG) { "spare socket died after $ageMs ms" }
        // A socket rejected at once (bad key, no network) is not retried until the next press.
        if (foreground && ageMs >= MIN_SPARE_LIFETIME_MS) {
            settings.current?.asrServices?.firstOrNull { it.provider == AsrProvider.DashScope }?.let(::prepare)
        }
    }

    private companion object {
        const val MIN_SPARE_LIFETIME_MS = 10_000L
        const val TAG = "DashScope"
    }
}

internal const val DASHSCOPE_MODEL = "fun-asr-realtime"

internal data class DashScopeConfig(val url: String, val key: String)

private fun dashScopeConfig(host: String, key: String) =
    DashScopeConfig("wss://$host/api-ws/v1/inference", key)

/**
 * Buffers the press locally and only starts the provider task after release, on the recognizer's
 * spare socket if one is open by then ([takeSpare]), otherwise on a new one.
 */
internal class DashScopeBufferedSession(
    private val client: OkHttpClient,
    private val config: DashScopeConfig,
    private val takeSpare: () -> WarmSocket? = { null },
) : RecognitionSession {
    private val lock = Any()
    private val frames = mutableListOf<ShortArray>()
    private var delegate: DashScopeSession? = null
    private var closed = false
    override val partials = MutableStateFlow("")

    override fun feed(pcm: ShortArray) = synchronized(lock) {
        check(!closed) { "Session is no longer recording" }
        frames += pcm.copyOf()
    }

    override suspend fun finish(): Transcript {
        val (session, audio) = synchronized(lock) {
            check(!closed)
            closed = true
            DashScopeSession(client, config, takeSpare(), DebugLog.sink("DashScope")).also { delegate = it } to frames.toList()
        }
        audio.forEach(session::feed)
        return session.finish()
    }

    override fun cancel() {
        synchronized(lock) {
            closed = true
            frames.clear()
            delegate?.cancel()
        }
    }
}

/**
 * A socket opened before the press, so the TLS handshake is not between the finger and the
 * first recognized word: the provider recognizes in real time, so every second of audio queued
 * behind the handshake is a second more to wait after release. Nothing is sent on it until a
 * session adopts it.
 */
internal class WarmSocket(
    client: OkHttpClient,
    config: DashScopeConfig,
    private val onDied: (WarmSocket, ageMs: Long) -> Unit = { _, _ -> },
) : WebSocketListener() {
    private val url = config.url
    private val key = config.key
    private val createdAt = System.nanoTime()
    // Guarded by `this`.
    private var owner: WebSocketListener? = null
    private var handshake: Response? = null
    private var dead = false
    val socket: WebSocket = client.newWebSocket(dashScopeRequest(config), this)

    fun isUsableFor(config: DashScopeConfig) = synchronized(this) {
        !dead && owner == null && url == config.url && key == config.key
    }

    /** Hands every later callback to [listener]; false if the socket already died. */
    fun adopt(listener: WebSocketListener): Boolean {
        val opened = synchronized(this) {
            if (dead || owner != null) return false
            owner = listener
            handshake
        }
        opened?.let { listener.onOpen(socket, it) }
        return true
    }

    fun close() {
        synchronized(this) { dead = true }
        socket.cancel()
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        synchronized(this) { handshake = response; owner }?.onOpen(webSocket, response)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        synchronized(this) { owner }?.onMessage(webSocket, text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        synchronized(this) { owner }?.onMessage(webSocket, bytes)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        val target = die()
        if (target != null) target.onClosing(webSocket, code, reason) else webSocket.close(code, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        die()?.onClosed(webSocket, code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        val target = die()
        if (target != null) target.onFailure(webSocket, t, response) else response?.close()
    }

    private fun die(): WebSocketListener? {
        val (target, wasAlive) = synchronized(this) { (owner to !dead).also { dead = true } }
        if (target == null && wasAlive) onDied(this, (System.nanoTime() - createdAt) / 1_000_000)
        return target
    }
}

private fun dashScopeRequest(config: DashScopeConfig) = Request.Builder().url(config.url)
    .header("Authorization", "Bearer " + config.key).build()

/**
 * One recognition task per press, on [warm] if it is still open, otherwise on a new socket.
 * Audio waits for task-started, and finish-task is ordered after every queued frame.
 * All callback/feed/finish/cancel state is guarded by one lock.
 */
internal class DashScopeSession(
    private val client: OkHttpClient,
    config: DashScopeConfig,
    warm: WarmSocket? = null,
    // Opt-in device-test diagnostics only; the app never logs requests or provider bodies.
    private val diagnostics: ((String) -> Unit)? = null,
) : WebSocketListener(), RecognitionSession {
    private val openedAt = System.nanoTime()
    private val diagnosticSecrets = if (diagnostics != null && config.key.isNotEmpty()) {
        listOf(config.key)
    } else emptyList()
    private val lock = Any()
    private var taskId = newTaskId()
    private val request = dashScopeRequest(config)
    private val model = DASHSCOPE_MODEL
    private val result = CompletableDeferred<Transcript>()
    private val sentences = TreeMap<Long, String>()
    private var draftSentence = ""
    private val pending = ArrayDeque<ByteString>()
    private val wireAudio = Buffer()
    private var pendingBytes = 0
    private var started = false
    private var finishing = false
    private var closed = false
    private var failure: ServiceException? = null
    private var socket: WebSocket? = null
    // A spare socket can be dropped by the provider just as it is adopted. Until the task has
    // started nothing is lost, so that case opens one fresh socket instead of failing the press.
    private var mayReconnect = false
    private var retired: WebSocket? = null
    override val partials = MutableStateFlow("")

    init {
        // Held so no callback runs before `socket` is assigned; adopt() may call onOpen right here.
        synchronized(lock) {
            if (warm != null) {
                socket = warm.socket
                mayReconnect = true
            }
            if (warm == null || !warm.adopt(this)) {
                mayReconnect = false
                socket = client.newWebSocket(request, this)
            }
            diagnose { "socket warm=$mayReconnect" }
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
        if (webSocket === retired) return@synchronized
        if (closed) { webSocket.cancel(); return@synchronized }
        socket = webSocket
        diagnose { "websocket-open http=${response.code} extensions=${response.header("Sec-WebSocket-Extensions")}" }
        val request = buildJsonObject {
            put("header", header("run-task"))
            put("payload", buildJsonObject {
                put("task_group", "audio")
                put("task", "asr")
                put("function", "recognition")
                put("model", model)
                put("parameters", buildJsonObject {
                    put("format", "pcm")
                    put("sample_rate", 16000)
                })
                put("input", buildJsonObject {})
            })
        }
        if (!webSocket.send(request.toString())) fail(ServiceException(ServiceFailure.Network))
    }

    override fun feed(pcm: ShortArray) = synchronized(lock) {
        failure?.let { throw it }
        check(!closed && !finishing) { "Session is no longer recording" }
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bytes.putShort(it) }
        val frame = bytes.array().toByteString()
        if (started) {
            queueAudio(frame)
        } else {
            if (pendingBytes + frame.size > MAX_PENDING_BYTES) {
                val error = ServiceException(ServiceFailure.Network)
                fail(error)
                throw error
            }
            pending.addLast(frame)
            pendingBytes += frame.size
        }
    }

    override suspend fun finish(): Transcript {
        diagnose { "finish-entered" }
        synchronized(lock) {
            failure?.let { throw it }
            check(!closed && !finishing)
            finishing = true
            if (started) { flushAudio(); sendFinish() }
        }
        return try {
            result.await()
        } finally {
            synchronized(lock) {
                closed = true
                pending.clear()
                pendingBytes = 0
                wireAudio.clear()
                socket?.close(1000, "Finished")
                // cancel also releases a peer which never acknowledges the close.
                socket?.cancel()
            }
        }
    }

    override fun cancel(): Unit = synchronized(lock) {
        closed = true
        pending.clear()
        pendingBytes = 0
        wireAudio.clear()
        result.cancel()
        socket?.cancel()
    }

    override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
        if (closed || result.isCompleted || webSocket === retired) return@synchronized
        try {
            val root = Json.parseToJsonElement(text).jsonObject
            val header = root["header"]?.jsonObject ?: return@synchronized
            diagnose {
                "event=${header["event"]} task-id-match=${header["task_id"]?.jsonPrimitive?.content == taskId}" +
                    if (header["event"]?.jsonPrimitive?.content == "task-failed") {
                        " code=${header["error_code"]} message=${header["error_message"]}"
                    } else ""
            }
            if (header["task_id"]?.jsonPrimitive?.content != taskId) return@synchronized
            when (header["event"]?.jsonPrimitive?.content) {
                "task-started" -> {
                    if (started) return@synchronized
                    started = true
                    while (pending.isNotEmpty()) queueAudio(pending.removeFirst())
                    pendingBytes = 0
                    if (finishing) { flushAudio(); sendFinish() }
                }
                "result-generated" -> {
                    val sentence = root["payload"]?.jsonObject?.get("output")?.jsonObject
                        ?.get("sentence")?.jsonObject ?: return@synchronized
                    val begin = sentence["begin_time"]?.jsonPrimitive?.longOrNull ?: 0
                    val content = sentence["text"]?.jsonPrimitive?.content ?: return@synchronized
                    // begin_time can move while a sentence is provisional. Keep exactly one
                    // replaceable draft; only end_time != null commits a sentence (DashScope SDK rule).
                    val end = sentence["end_time"]?.jsonPrimitive?.longOrNull
                    diagnose { "sentence begin=$begin end=$end chars=${content.length}" }
                    if (end != null) {
                        sentences[begin] = content
                        draftSentence = ""
                    } else {
                        draftSentence = content
                    }
                    partials.value = sentences.values.joinToString("") + draftSentence
                }
                "task-finished" -> {
                    if (finishing && draftSentence.isBlank()) {
                        result.complete(Transcript(sentences.values.joinToString("").trim(), null))
                    } else fail(ServiceException(ServiceFailure.Response))
                }
                "task-failed" -> {
                    val code = header["error_code"]?.jsonPrimitive?.content.orEmpty().lowercase()
                    fail(ServiceException(when {
                        "accessdenied" in code || "permission" in code || "forbidden" in code -> ServiceFailure.Permission
                        "auth" in code || "apikey" in code || "api_key" in code -> ServiceFailure.Authentication
                        "throttl" in code || "limit" in code -> ServiceFailure.RateLimit
                        else -> ServiceFailure.Response
                    }))
                }
            }
        } catch (e: Exception) {
            fail(if (e is ServiceException) e else ServiceException(ServiceFailure.Response))
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?): Unit = synchronized(lock) {
        diagnose { "websocket-failure http=${response?.code} type=${t.javaClass.simpleName} message=${t.message}" }
        response?.close()
        if (webSocket === retired || reconnect(webSocket)) return@synchronized
        if (!closed) fail(response?.let { httpFailure(it.code) } ?: networkFailure(t))
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        diagnose { "websocket-closing code=$code reason=$reason" }
        webSocket.close(code, null)
        synchronized(lock) { reconnect(webSocket) }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = synchronized(lock) {
        if (webSocket === retired || reconnect(webSocket)) return@synchronized
        if (!closed && !result.isCompleted) fail(ServiceException(ServiceFailure.Network))
    }

    /** Replaces a spare socket that died before task-started; buffered audio goes to the new one. */
    private fun reconnect(webSocket: WebSocket): Boolean {
        if (!mayReconnect || started || closed || failure != null || webSocket !== socket) return false
        mayReconnect = false
        retired = webSocket
        webSocket.cancel()
        taskId = newTaskId()
        socket = client.newWebSocket(request, this)
        diagnose { "spare-socket-died reconnecting" }
        return true
    }

    private inline fun diagnose(message: () -> String) {
        val report = diagnostics ?: return
        var safe = "t=${(System.nanoTime() - openedAt) / 1_000_000} " + message()
        diagnosticSecrets.forEach { safe = safe.replace(it, "<key>") }
        runCatching { report(safe.take(1000)) }
    }

    private fun header(action: String) = buildJsonObject {
        put("action", action)
        put("task_id", taskId)
        put("streaming", "duplex")
    }

    private fun queueAudio(frame: ByteString) {
        wireAudio.write(frame)
        // Keep 20 ms microphone/UI frames, but use 100 ms (3.2 KB) WebSocket messages.
        // Tiny TLS writes otherwise amplify per-frame overhead, especially after a slow handshake.
        while (wireAudio.size >= WIRE_FRAME_BYTES) sendAudio(wireAudio.readByteString(WIRE_FRAME_BYTES))
    }

    private fun flushAudio() {
        if (wireAudio.size > 0) sendAudio(wireAudio.readByteString())
    }

    private fun sendAudio(frame: ByteString) {
        val ws = socket ?: throw ServiceException(ServiceFailure.Network)
        if (ws.queueSize() + frame.size > MAX_PENDING_BYTES || !ws.send(frame)) {
            val error = ServiceException(ServiceFailure.Network)
            fail(error)
            throw error
        }
    }

    private fun sendFinish() {
        val event = buildJsonObject {
            put("header", header("finish-task"))
            put("payload", buildJsonObject { put("input", buildJsonObject {}) })
        }
        val sent = socket?.send(event.toString()) == true
        diagnose { "finish-sent accepted=$sent queue=${socket?.queueSize()} event=$event" }
        if (!sent) fail(ServiceException(ServiceFailure.Network))
    }

    private fun fail(error: ServiceException) {
        // Cancelling a rejected task can itself trigger onFailure(Socket is closed).
        // Preserve the provider's original error for feed() as well as finish().
        if (closed || failure != null || result.isCompleted) return
        failure = error
        pending.clear()
        pendingBytes = 0
        wireAudio.clear()
        result.completeExceptionally(error)
        socket?.cancel()
    }

    private companion object {
        // At most ten seconds of pre-handshake / unsent 16 kHz PCM; never silently drop audio.
        const val MAX_PENDING_BYTES = 16000 * 2 * 10
        const val WIRE_FRAME_BYTES = 16000L * 2 / 10
    }
}

private fun newTaskId() = UUID.randomUUID().toString().replace("-", "")
