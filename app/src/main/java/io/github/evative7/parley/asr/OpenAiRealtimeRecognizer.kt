package io.github.evative7.parley.asr

import io.github.evative7.parley.audio.pcm16Bytes
import io.github.evative7.parley.audio.resample16kTo24k
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.network.httpFailure
import io.github.evative7.parley.network.networkFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
import okio.ByteString.Companion.toByteString
import javax.inject.Inject

class OpenAiRealtimeRecognizer @Inject constructor(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
) : SpeechRecognizer {
    override val isStreaming = true

    override fun open(lang: String?): RecognitionSession {
        val config = settings.current?.takeIf {
            it.isReady && it.asrService == AsrService.GptRealtimeWhisper
        } ?: throw ServiceException(ServiceFailure.Configuration)
        return OpenAiRealtimeSession(client, REALTIME_URL, config.openAiAsrKey, lang)
    }

    private companion object {
        const val REALTIME_URL = "wss://api.openai.com/v1/realtime?model=gpt-realtime-2"
    }
}

internal class OpenAiRealtimeSession(
    client: OkHttpClient,
    url: String,
    private val key: String,
    private val languageHint: String?,
) : WebSocketListener(), RecognitionSession {
    private val lock = Any()
    private val result = CompletableDeferred<Transcript>()
    private val pending = ArrayDeque<ByteString>()
    private var socket: WebSocket? = null
    private var opened = false
    private var finishing = false
    private var closed = false
    private var accumulated = ""
    override val partials = MutableStateFlow("")

    init {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $key").build()
        socket = client.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
        if (closed) { webSocket.cancel(); return@synchronized }
        opened = true
        val transcription = buildJsonObject {
            put("model", "gpt-realtime-whisper")
            languageHint?.takeIf { it.isNotBlank() }?.let { put("language", it.substringBefore('-').lowercase()) }
        }
        val update = buildJsonObject {
            put("type", "session.update")
            put("session", buildJsonObject {
                put("type", "transcription")
                put("audio", buildJsonObject {
                    put("input", buildJsonObject {
                        put("format", buildJsonObject {
                            put("type", "audio/pcm")
                            put("rate", 24_000)
                        })
                        put("transcription", transcription)
                        put("turn_detection", JsonNull)
                    })
                })
            })
        }
        if (!webSocket.send(update.toString())) return@synchronized fail(ServiceException(ServiceFailure.Network))
        while (pending.isNotEmpty()) sendAudio(webSocket, pending.removeFirst())
        if (finishing) sendCommit(webSocket)
    }

    override fun feed(pcm: ShortArray) = synchronized(lock) {
        check(!closed && !finishing) { "Session is no longer recording" }
        val bytes = pcm16Bytes(resample16kTo24k(pcm)).toByteString()
        val current = socket
        if (opened && current != null) sendAudio(current, bytes) else pending.addLast(bytes)
    }

    private fun sendAudio(webSocket: WebSocket, bytes: ByteString) {
        val event = buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", bytes.base64())
        }
        if (!webSocket.send(event.toString())) fail(ServiceException(ServiceFailure.Network))
    }

    override suspend fun finish(): Transcript {
        synchronized(lock) {
            check(!closed && !finishing)
            finishing = true
            if (opened) socket?.let(::sendCommit)
        }
        return try {
            result.await()
        } finally {
            synchronized(lock) {
                closed = true
                pending.clear()
                socket?.close(1000, "Finished")
                socket?.cancel()
            }
        }
    }

    private fun sendCommit(webSocket: WebSocket) {
        val event = buildJsonObject { put("type", "input_audio_buffer.commit") }
        if (!webSocket.send(event.toString())) fail(ServiceException(ServiceFailure.Network))
    }

    override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
        if (closed || result.isCompleted) return@synchronized
        try {
            val event = Json.parseToJsonElement(text).jsonObject
            when (event["type"]?.jsonPrimitive?.content) {
                "conversation.item.input_audio_transcription.delta" -> {
                    val delta = event["delta"]?.jsonPrimitive?.content.orEmpty()
                    accumulated += delta
                    partials.value = accumulated
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = event["transcript"]?.jsonPrimitive?.content.orEmpty().trim()
                    if (transcript.isBlank()) fail(ServiceException(ServiceFailure.Response))
                    else result.complete(Transcript(transcript, null))
                }
                "error" -> {
                    val error = event["error"]?.jsonObject
                    val code = error?.get("code")?.jsonPrimitive?.content.orEmpty().lowercase()
                    fail(ServiceException(when {
                        "rate" in code || "limit" in code -> ServiceFailure.RateLimit
                        "permission" in code || "forbidden" in code -> ServiceFailure.Permission
                        "auth" in code || "key" in code -> ServiceFailure.Authentication
                        else -> ServiceFailure.Response
                    }))
                }
            }
        } catch (e: Exception) {
            fail(if (e is ServiceException) e else ServiceException(ServiceFailure.Response))
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = synchronized(lock) {
        response?.close()
        if (!closed) fail(response?.let { httpFailure(it.code) } ?: networkFailure(t))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = synchronized(lock) {
        if (!closed && !result.isCompleted) fail(ServiceException(ServiceFailure.Network))
    }

    private fun fail(error: ServiceException) {
        if (!result.isCompleted) result.completeExceptionally(error)
        socket?.cancel()
    }

    override fun cancel() {
        synchronized(lock) {
            closed = true
            pending.clear()
            result.cancel()
            socket?.cancel()
        }
    }
}
