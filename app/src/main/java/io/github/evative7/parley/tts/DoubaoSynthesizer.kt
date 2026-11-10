package io.github.evative7.parley.tts

import io.github.evative7.parley.asr.VolcengineProtocol
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.network.httpFailure
import io.github.evative7.parley.network.networkFailure
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.UUID

class DoubaoSynthesizer(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
) : SpeechSynthesizer {
    override fun open(lang: Lang, voice: String): SynthesisSession {
        val saved = settings.current ?: throw ServiceException(ServiceFailure.Configuration)
        if (saved.volcengineAppId.isBlank() || saved.volcengineAccessToken.isBlank()) {
            throw ServiceException(ServiceFailure.Configuration)
        }
        return DoubaoSession(
            client,
            saved.volcengineAppId,
            saved.volcengineAccessToken,
            lang,
            voice,
        )
    }
}

internal class DoubaoSession(
    private val client: OkHttpClient,
    private val appId: String,
    private val accessToken: String,
    private val lang: Lang,
    private val voice: String,
    private val sessionId: String = UUID.randomUUID().toString(),
) : WebSocketListener(), SynthesisSession {
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var output: ProducerScope<ByteArray>? = null
    private var socket: WebSocket? = null
    private var sessionStarted = false
    private var finishRequested = false
    private var finishSent = false
    private var closed = false

    override val audio: Flow<ByteArray> = callbackFlow {
        synchronized(lock) {
            check(output == null) { "Synthesis audio can only be collected once" }
            output = this
            val request = Request.Builder().url(URL)
                .header("X-Api-App-Id", appId)
                .header("X-Api-Access-Key", accessToken)
                .header("X-Api-Resource-Id", RESOURCE_ID)
                .header("X-Api-Connect-Id", UUID.randomUUID().toString())
                .build()
            socket = client.newWebSocket(request, this@DoubaoSession)
        }
        awaitClose {
            synchronized(lock) {
                closed = true
                socket?.cancel()
                socket = null
                output = null
            }
        }
    }

    override fun append(text: String) = synchronized(lock) {
        check(!finishRequested) { "Synthesis session is already finished" }
        if (sessionStarted) send(DoubaoProtocol.task(sessionId, voice, lang, text)) else pending.addLast(text)
    }

    override fun finish() = synchronized(lock) {
        finishRequested = true
        if (sessionStarted) sendFinish()
    }

    override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
        if (closed) { webSocket.cancel(); return@synchronized }
        socket = webSocket
        send(DoubaoProtocol.startConnection())
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = synchronized(lock) {
        if (closed) return@synchronized
        val response = VolcengineProtocol.parseEvent(bytes.toByteArray())
            ?: return@synchronized fail(ServiceException(ServiceFailure.Response))
        when (response.event) {
            DoubaoProtocol.CONNECTION_STARTED -> send(DoubaoProtocol.startSession(sessionId, voice, lang))
            DoubaoProtocol.SESSION_STARTED -> {
                sessionStarted = true
                while (pending.isNotEmpty()) send(DoubaoProtocol.task(sessionId, voice, lang, pending.removeFirst()))
                if (finishRequested) sendFinish()
            }
            DoubaoProtocol.TTS_RESPONSE -> if (response.messageType == VolcengineProtocol.SERVER_AUDIO_ONLY_RESPONSE) {
                output?.trySend(response.payload)
            }
            DoubaoProtocol.SESSION_FINISHED -> {
                send(DoubaoProtocol.finishConnection())
                closed = true
                output?.close()
                webSocket.close(1000, "Finished")
            }
            DoubaoProtocol.CONNECTION_FAILED, DoubaoProtocol.SESSION_FAILED -> fail(
                DoubaoProtocol.failure(response.payload, response.errorCode),
            )
            else -> if (response.errorCode != null) fail(DoubaoProtocol.failure(response.payload, response.errorCode))
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
        if (!closed) fail(ServiceException(ServiceFailure.Response))
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        response?.close()
        synchronized(lock) {
            if (!closed) fail(response?.let { httpFailure(it.code) } ?: networkFailure(t))
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = synchronized(lock) {
        if (!closed) fail(ServiceException(ServiceFailure.Network))
    }

    private fun sendFinish() {
        if (finishSent) return
        finishSent = true
        send(DoubaoProtocol.finishSession(sessionId))
    }

    private fun send(frame: ByteArray) {
        if (socket?.send(frame.toByteString()) != true) fail(ServiceException(ServiceFailure.Network))
    }

    private fun fail(error: Throwable) {
        if (closed) return
        closed = true
        output?.close(error)
        socket?.cancel()
    }

    private companion object {
        const val URL = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"
        const val RESOURCE_ID = "seed-tts-2.0"
    }
}

internal object DoubaoProtocol {
    const val START_CONNECTION = 1
    const val FINISH_CONNECTION = 2
    const val CONNECTION_STARTED = 50
    const val CONNECTION_FAILED = 51
    const val START_SESSION = 100
    const val FINISH_SESSION = 102
    const val SESSION_STARTED = 150
    const val SESSION_FINISHED = 152
    const val SESSION_FAILED = 153
    const val TASK_REQUEST = 200
    const val TTS_RESPONSE = 352

    fun startConnection(): ByteArray = VolcengineProtocol.eventFrame(START_CONNECTION)
    fun finishConnection(): ByteArray = VolcengineProtocol.eventFrame(FINISH_CONNECTION)
    fun startSession(sessionId: String, voice: String, lang: Lang): ByteArray =
        VolcengineProtocol.eventFrame(START_SESSION, sessionId, request(START_SESSION, voice, lang).toByteArray())
    fun task(sessionId: String, voice: String, lang: Lang, text: String): ByteArray =
        VolcengineProtocol.eventFrame(TASK_REQUEST, sessionId, request(TASK_REQUEST, voice, lang, text).toByteArray())
    fun finishSession(sessionId: String): ByteArray = VolcengineProtocol.eventFrame(
        FINISH_SESSION,
        sessionId,
        buildJsonObject { put("event", FINISH_SESSION) }.toString().toByteArray(),
    )

    fun failure(payload: ByteArray, errorCode: Int?): Throwable {
        val message = payload.toString(Charsets.UTF_8)
        val lower = message.lowercase()
        return when {
            ("resource" in lower && ("grant" in lower || "permission" in lower)) || "not granted" in lower ->
                DoubaoTtsException("Enable 语音合成 2.0 for this app in the Volcengine console")
            errorCode == 401 || "auth" in lower || "token" in lower -> ServiceException(ServiceFailure.Authentication)
            errorCode == 403 || "permission" in lower -> ServiceException(ServiceFailure.Permission)
            "limit" in lower || "quota" in lower || "thrott" in lower -> ServiceException(ServiceFailure.RateLimit)
            else -> ServiceException(ServiceFailure.Response)
        }
    }

    private fun request(event: Int, voice: String, lang: Lang, text: String? = null): String = buildJsonObject {
        put("event", event)
        put("namespace", "BidirectionalTTS")
        put("req_params", buildJsonObject {
            put("speaker", voice)
            text?.let { put("text", it) }
            put("audio_params", buildJsonObject {
                put("format", "pcm")
                put("sample_rate", 24_000)
            })
            put("explicit_language", lang.explicitLanguage())
        })
    }.toString()

    private fun Lang.explicitLanguage(): String = when (tag) {
        "zh-CN", "zh-TW" -> "zh-cn"
        "es" -> "es-mx"
        "pt" -> "pt-br"
        else -> base
    }
}

class DoubaoTtsException(message: String) : IOException(message)
