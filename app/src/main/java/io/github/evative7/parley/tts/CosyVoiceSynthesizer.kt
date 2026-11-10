package io.github.evative7.parley.tts

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID

class CosyVoiceSynthesizer(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
    private val service: TtsService,
) : SpeechSynthesizer {
    override fun open(lang: Lang, voice: String): SynthesisSession {
        val saved = settings.current ?: throw ServiceException(ServiceFailure.Configuration)
        if (saved.dashScopeKey.isBlank()) throw ServiceException(ServiceFailure.Configuration)
        return CosyVoiceSession(
            client = client,
            url = "wss://${saved.dashScopeRegion.host}/api-ws/v1/inference",
            key = saved.dashScopeKey,
            model = service.id,
            lang = lang,
            voice = voice,
        )
    }
}

internal class CosyVoiceSession(
    private val client: OkHttpClient,
    private val url: String,
    private val key: String,
    private val model: String,
    private val lang: Lang,
    private val voice: String,
    private val taskId: String = UUID.randomUUID().toString(),
) : WebSocketListener(), SynthesisSession {
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var output: ProducerScope<ByteArray>? = null
    private var socket: WebSocket? = null
    private var started = false
    private var finishRequested = false
    private var finishSent = false
    private var closed = false

    override val audio: Flow<ByteArray> = callbackFlow {
        synchronized(lock) {
            check(output == null) { "Synthesis audio can only be collected once" }
            output = this
            val request = Request.Builder().url(url).header("Authorization", "Bearer $key").build()
            socket = client.newWebSocket(request, this@CosyVoiceSession)
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
        if (started) send(CosyVoiceProtocol.continueTask(taskId, text)) else pending.addLast(text)
    }

    override fun finish() = synchronized(lock) {
        finishRequested = true
        if (started) sendFinish()
    }

    override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
        if (closed) { webSocket.cancel(); return@synchronized }
        socket = webSocket
        send(CosyVoiceProtocol.runTask(taskId, model, voice, lang))
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        synchronized(lock) { if (!closed) output?.trySend(bytes.toByteArray()) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
        if (closed) return@synchronized
        when (val event = CosyVoiceProtocol.parse(text)) {
            CosyVoiceEvent.Started -> {
                if (started) return@synchronized
                started = true
                while (pending.isNotEmpty()) send(CosyVoiceProtocol.continueTask(taskId, pending.removeFirst()))
                if (finishRequested) sendFinish()
            }
            CosyVoiceEvent.Finished -> {
                closed = true
                output?.close()
                webSocket.close(1000, "Finished")
            }
            is CosyVoiceEvent.Failed -> fail(event.error)
            CosyVoiceEvent.Other -> Unit
        }
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
        send(CosyVoiceProtocol.finishTask(taskId))
    }

    private fun send(message: String) {
        if (socket?.send(message) != true) fail(ServiceException(ServiceFailure.Network))
    }

    private fun fail(error: ServiceException) {
        if (closed) return
        closed = true
        output?.close(error)
        socket?.cancel()
    }
}

internal sealed interface CosyVoiceEvent {
    data object Started : CosyVoiceEvent
    data object Finished : CosyVoiceEvent
    data class Failed(val error: ServiceException) : CosyVoiceEvent
    data object Other : CosyVoiceEvent
}

internal object CosyVoiceProtocol {
    fun runTask(taskId: String, model: String, voice: String, lang: Lang): String = buildJsonObject {
        put("header", header("run-task", taskId))
        put("payload", buildJsonObject {
            put("task_group", "audio")
            put("task", "tts")
            put("function", "SpeechSynthesizer")
            put("model", model)
            put("parameters", buildJsonObject {
                put("text_type", "PlainText")
                put("voice", voice)
                put("format", "pcm")
                put("sample_rate", 24_000)
                put("language_hints", buildJsonArray { add(JsonPrimitive(lang.languageHint())) })
            })
            put("input", JsonObject(emptyMap()))
        })
    }.toString()

    fun continueTask(taskId: String, text: String): String = buildJsonObject {
        put("header", header("continue-task", taskId))
        put("payload", buildJsonObject {
            put("input", buildJsonObject { put("text", text) })
        })
    }.toString()

    fun finishTask(taskId: String): String = buildJsonObject {
        put("header", header("finish-task", taskId))
        put("payload", buildJsonObject { put("input", JsonObject(emptyMap())) })
    }.toString()

    fun parse(text: String): CosyVoiceEvent = try {
        val header = Json.parseToJsonElement(text).jsonObject["header"]?.jsonObject ?: return CosyVoiceEvent.Other
        when (header["event"]?.jsonPrimitive?.content) {
            "task-started" -> CosyVoiceEvent.Started
            "task-finished" -> CosyVoiceEvent.Finished
            "task-failed" -> {
                val code = header["error_code"]?.jsonPrimitive?.content.orEmpty().lowercase()
                CosyVoiceEvent.Failed(ServiceException(when {
                    "accessdenied" in code || "permission" in code || "forbidden" in code -> ServiceFailure.Permission
                    "auth" in code || "apikey" in code || "api_key" in code -> ServiceFailure.Authentication
                    "throttl" in code || "limit" in code -> ServiceFailure.RateLimit
                    else -> ServiceFailure.Response
                }))
            }
            else -> CosyVoiceEvent.Other
        }
    } catch (_: Exception) {
        CosyVoiceEvent.Failed(ServiceException(ServiceFailure.Response))
    }

    private fun header(action: String, taskId: String) = buildJsonObject {
        put("action", action)
        put("task_id", taskId)
        put("streaming", "duplex")
    }

    private fun Lang.languageHint(): String = when (base) {
        "zh" -> "zh"
        "en", "fr", "de", "ja", "ko", "ru", "pt", "th", "id", "vi", "es", "it", "ar" -> base
        else -> "en"
    }
}
