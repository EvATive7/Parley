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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class MiniMaxSynthesizer(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
    private val service: TtsService,
) : SpeechSynthesizer {
    override fun open(lang: Lang, voice: String): SynthesisSession {
        val saved = settings.current ?: throw ServiceException(ServiceFailure.Configuration)
        if (saved.miniMaxKey.isBlank()) throw ServiceException(ServiceFailure.Configuration)
        return MiniMaxSession(
            client = client,
            url = "wss://${saved.miniMaxRegion.host}/ws/v1/t2a_v2",
            key = saved.miniMaxKey,
            model = service.id.removePrefix("minimax-"),
            lang = lang,
            voice = voice,
        )
    }
}

internal class MiniMaxSession(
    private val client: OkHttpClient,
    private val url: String,
    private val key: String,
    private val model: String,
    private val lang: Lang,
    private val voice: String,
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
            socket = client.newWebSocket(request, this@MiniMaxSession)
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
        if (started) send(MiniMaxProtocol.continueTask(text)) else pending.addLast(text)
    }

    override fun finish() = synchronized(lock) {
        finishRequested = true
        if (started) sendFinish()
    }

    override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
        if (closed) {
            webSocket.cancel()
            return@synchronized
        }
        socket = webSocket
    }

    override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
        if (closed) return@synchronized
        when (val event = MiniMaxProtocol.parse(text)) {
            MiniMaxEvent.Connected -> send(MiniMaxProtocol.startTask(model, voice, lang))
            MiniMaxEvent.Started -> {
                started = true
                while (pending.isNotEmpty()) send(MiniMaxProtocol.continueTask(pending.removeFirst()))
                if (finishRequested) sendFinish()
            }
            is MiniMaxEvent.Audio -> output?.trySend(event.bytes)
            MiniMaxEvent.Finished -> {
                closed = true
                output?.close()
                webSocket.close(1000, "Finished")
            }
            is MiniMaxEvent.Failed -> fail(event.error)
            MiniMaxEvent.Other -> Unit
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = synchronized(lock) {
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
        send(MiniMaxProtocol.finishTask())
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

internal sealed interface MiniMaxEvent {
    data object Connected : MiniMaxEvent
    data object Started : MiniMaxEvent
    data class Audio(val bytes: ByteArray, val final: Boolean) : MiniMaxEvent
    data object Finished : MiniMaxEvent
    data class Failed(val error: ServiceException) : MiniMaxEvent
    data object Other : MiniMaxEvent
}

internal object MiniMaxProtocol {
    fun startTask(model: String, voice: String, lang: Lang): String = buildJsonObject {
        put("event", "task_start")
        put("model", model)
        put("language_boost", lang.languageBoost())
        put("voice_setting", buildJsonObject {
            put("voice_id", voice)
            put("speed", 1)
            put("vol", 1)
            put("pitch", 0)
        })
        put("audio_setting", buildJsonObject {
            put("sample_rate", 24_000)
            put("format", "pcm")
            put("channel", 1)
        })
    }.toString()

    fun continueTask(text: String): String = buildJsonObject {
        put("event", "task_continue")
        put("text", text)
    }.toString()

    fun finishTask(): String = buildJsonObject { put("event", "task_finish") }.toString()

    fun parse(text: String): MiniMaxEvent = try {
        val root = Json.parseToJsonElement(text).jsonObject
        val response = root["base_resp"]?.jsonObject
        val status = response?.get("status_code")?.jsonPrimitive?.intOrNull ?: 0
        if (status != 0) {
            MiniMaxEvent.Failed(failure(status, response?.get("status_msg")?.jsonPrimitive?.contentOrNull))
        } else {
            when (root["event"]?.jsonPrimitive?.contentOrNull) {
                "connected_success" -> MiniMaxEvent.Connected
                "task_started" -> MiniMaxEvent.Started
                "task_continued" -> {
                    val hex = root["data"]?.jsonObject?.get("audio")?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (hex.isEmpty()) MiniMaxEvent.Other
                    else MiniMaxEvent.Audio(hex.hexBytes(), root["is_final"]?.jsonPrimitive?.contentOrNull == "true")
                }
                "task_finished" -> MiniMaxEvent.Finished
                "task_failed", "connected_failed" -> MiniMaxEvent.Failed(
                    failure(status, response?.get("status_msg")?.jsonPrimitive?.contentOrNull),
                )
                else -> MiniMaxEvent.Other
            }
        }
    } catch (_: Exception) {
        MiniMaxEvent.Failed(ServiceException(ServiceFailure.Response))
    }

    private fun failure(code: Int, message: String?): ServiceException {
        val lower = message.orEmpty().lowercase()
        return ServiceException(when {
            code == 1004 || "auth" in lower || "key" in lower -> ServiceFailure.Authentication
            code == 1008 || "permission" in lower || "forbidden" in lower -> ServiceFailure.Permission
            code == 1002 || code == 1009 || "limit" in lower || "quota" in lower -> ServiceFailure.RateLimit
            else -> ServiceFailure.Response
        })
    }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun Lang.languageBoost(): String = when (base) {
        "zh" -> "Chinese"
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "pt" -> "Portuguese"
        "ar" -> "Arabic"
        "it" -> "Italian"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "id" -> "Indonesian"
        "vi" -> "Vietnamese"
        "ru" -> "Russian"
        "th" -> "Thai"
        else -> "English"
    }
}
