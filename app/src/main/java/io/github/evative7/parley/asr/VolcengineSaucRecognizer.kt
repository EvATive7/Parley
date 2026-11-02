package io.github.evative7.parley.asr

import io.github.evative7.parley.audio.pcm16Bytes
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.network.httpFailure
import io.github.evative7.parley.network.networkFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
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
import java.util.UUID
import javax.inject.Inject

class VolcengineSaucRecognizer @Inject constructor(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
) : ServiceRecognizer {
    override fun open(service: AsrService, lang: String?): RecognitionSession {
        val config = settings.current?.takeIf {
            it.isReady && service == AsrService.DoubaoSeedAsr20Stream
        } ?: throw ServiceException(ServiceFailure.Configuration)
        return VolcengineSaucSession(client, SAUC_URL, config.volcAppId, config.volcAccessToken, lang)
    }

    private companion object {
        const val SAUC_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
    }
}

internal class VolcengineSaucSession(
    client: OkHttpClient,
    url: String,
    appId: String,
    accessToken: String,
    private val languageHint: String?,
) : WebSocketListener(), RecognitionSession {
    private val lock = Any()
    private val result = CompletableDeferred<Transcript>()
    private val pending = ArrayDeque<ByteArray>()
    private val finalParts = mutableListOf<String>()
    private val seenUtterances = mutableSetOf<String>()
    private var sequence = 1
    private var socket: WebSocket? = null
    private var opened = false
    private var finishing = false
    private var closed = false
    override val partials = MutableStateFlow("")

    init {
        val request = Request.Builder().url(url)
            .header("X-Api-App-Key", appId)
            .header("X-Api-Access-Key", accessToken)
            .header("X-Api-Resource-Id", RESOURCE_ID)
            .header("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()
        socket = client.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
        if (closed) { webSocket.cancel(); return@synchronized }
        opened = true
        val audio = buildJsonObject {
            put("format", "pcm")
            put("codec", "raw")
            put("rate", 16_000)
            put("bits", 16)
            put("channel", 1)
            languageHint?.takeIf { it.isNotBlank() }?.let { put("language", it.substringBefore('-').lowercase()) }
        }
        val payload = buildJsonObject {
            put("user", buildJsonObject { put("uid", UUID.randomUUID().toString()) })
            put("audio", audio)
            put("request", buildJsonObject {
                put("model_name", "bigmodel")
                put("enable_itn", true)
                put("enable_punc", true)
                put("enable_ddc", true)
                put("show_utterances", true)
            })
        }.toString().toByteArray()
        if (!webSocket.send(VolcengineProtocol.frame(
                VolcengineProtocol.CLIENT_FULL_REQUEST,
                VolcengineProtocol.POS_SEQUENCE,
                nextSequence(),
                payload,
                VolcengineProtocol.JSON_SERIALIZATION,
            ).toByteString())) return@synchronized fail(ServiceException(ServiceFailure.Network))
        while (pending.isNotEmpty()) sendAudio(webSocket, pending.removeFirst())
        if (finishing) sendEos(webSocket)
    }

    override fun feed(pcm: ShortArray) = synchronized(lock) {
        check(!closed && !finishing) { "Session is no longer recording" }
        val bytes = pcm16Bytes(pcm)
        val current = socket
        if (opened && current != null) sendAudio(current, bytes) else pending.addLast(bytes)
    }

    private fun sendAudio(webSocket: WebSocket, bytes: ByteArray) {
        val frame = VolcengineProtocol.frame(
            VolcengineProtocol.CLIENT_AUDIO_ONLY_REQUEST,
            VolcengineProtocol.POS_SEQUENCE,
            nextSequence(),
            bytes,
            VolcengineProtocol.NO_SERIALIZATION,
        )
        if (!webSocket.send(frame.toByteString())) fail(ServiceException(ServiceFailure.Network))
    }

    override suspend fun finish(): Transcript {
        synchronized(lock) {
            check(!closed && !finishing)
            finishing = true
            if (opened) socket?.let(::sendEos)
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

    private fun sendEos(webSocket: WebSocket) {
        val finalSequence = nextSequence()
        val frame = VolcengineProtocol.frame(
            VolcengineProtocol.CLIENT_AUDIO_ONLY_REQUEST,
            VolcengineProtocol.NEG_WITH_SEQUENCE,
            -finalSequence,
            byteArrayOf(),
            VolcengineProtocol.NO_SERIALIZATION,
        )
        if (!webSocket.send(frame.toByteString())) fail(ServiceException(ServiceFailure.Network))
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = synchronized(lock) {
        if (closed || result.isCompleted) return@synchronized
        val packet = VolcengineProtocol.parse(bytes.toByteArray())
            ?: return@synchronized fail(ServiceException(ServiceFailure.Response))
        if (packet.errorCode != null) {
            fail(ServiceException(ServiceFailure.Response))
            return@synchronized
        }
        val root = packet.json ?: return@synchronized
        val responseResult = root["result"]?.jsonObject
        var draft = ""
        val utterances = responseResult?.get("utterances")?.jsonArray
        if (utterances != null) {
            for (element in utterances) {
                val utterance = element.jsonObject
                val text = utterance["text"]?.jsonPrimitive?.content.orEmpty()
                if (text.isBlank()) continue
                val definite = utterance["definite"]?.jsonPrimitive?.content == "true"
                if (definite) {
                    val signature = listOf(
                        utterance["start_time"]?.toString(),
                        utterance["end_time"]?.toString(),
                        text,
                    ).joinToString("|")
                    if (seenUtterances.add(signature)) finalParts += text
                } else {
                    draft = text
                }
            }
        } else {
            val text = responseResult?.get("text")?.jsonPrimitive?.content.orEmpty()
            if (text.isNotBlank()) draft = text
        }
        partials.value = finalParts.joinToString("") + draft
        val jsonLast = root["is_last"]?.jsonPrimitive?.content == "true" ||
            responseResult?.get("is_last")?.jsonPrimitive?.content == "true"
        if (finishing && (packet.isLast || jsonLast)) {
            val finalText = (finalParts.joinToString("") + draft).trim()
            if (finalText.isBlank()) fail(ServiceException(ServiceFailure.Response))
            else result.complete(Transcript(finalText, null))
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = synchronized(lock) {
        response?.close()
        if (!closed) fail(response?.let { httpFailure(it.code) } ?: networkFailure(t))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = synchronized(lock) {
        if (!closed && !result.isCompleted) fail(ServiceException(ServiceFailure.Network))
    }

    private fun nextSequence() = sequence++

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

    private companion object { const val RESOURCE_ID = "volc.seedasr.sauc.duration" }
}
