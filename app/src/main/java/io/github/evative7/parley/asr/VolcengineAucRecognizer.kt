package io.github.evative7.parley.asr

import io.github.evative7.parley.audio.flattenSamples
import io.github.evative7.parley.audio.pcm16Wav
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.network.ConnectionWarmer
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.network.httpFailure
import io.github.evative7.parley.network.networkFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VolcengineAucRecognizer @Inject constructor(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
) : ServiceRecognizer {
    private val warmer = ConnectionWarmer(client, "Volcengine")

    /** The audio is uploaded after release; have the TLS connection ready by then. */
    override fun prepare(service: AsrService) {
        // Submit, query and flash endpoints share one host, so one connection serves all three.
        if (settings.current?.isReady == true && service in SUPPORTED_SERVICES) warmer.warm(VolcAucEndpoints.Default.submit)
    }

    override fun open(service: AsrService, lang: String?): RecognitionSession {
        val config = settings.current?.takeIf {
            it.isReady && service in SUPPORTED_SERVICES
        } ?: throw ServiceException(ServiceFailure.Configuration)
        val mode = when (service) {
            AsrService.DoubaoSeedAsr20NonStream -> VolcAucMode.Standard
            AsrService.DoubaoSeedAsr20NonStreamTurbo -> VolcAucMode.Turbo
            else -> error("Unsupported Volcengine AUC service")
        }
        return VolcengineAucSession(
            client, VolcAucEndpoints.Default, mode, config.volcAppId, config.volcAccessToken,
        )
    }

    private companion object {
        val SUPPORTED_SERVICES = setOf(
            AsrService.DoubaoSeedAsr20NonStream,
            AsrService.DoubaoSeedAsr20NonStreamTurbo,
        )
    }
}

internal enum class VolcAucMode(val resourceId: String) {
    Standard("volc.seedasr.auc"),
    Turbo("volc.bigasr.auc_turbo"),
}

internal data class VolcAucEndpoints(val submit: String, val query: String, val turbo: String) {
    companion object {
        val Default = VolcAucEndpoints(
            "https://openspeech.bytedance.com/api/v3/auc/bigmodel/submit",
            "https://openspeech.bytedance.com/api/v3/auc/bigmodel/query",
            "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash",
        )
    }
}

internal class VolcengineAucSession(
    private val client: OkHttpClient,
    private val endpoints: VolcAucEndpoints,
    private val mode: VolcAucMode,
    private val appId: String,
    private val accessToken: String,
    private val pollIntervalMs: Long = 1_000,
    private val maxPolls: Int = 45,
) : RecognitionSession {
    private val lock = Any()
    private val frames = mutableListOf<ShortArray>()
    private var call: Call? = null
    private var closed = false
    override val partials = MutableStateFlow("")

    override fun feed(pcm: ShortArray) = synchronized(lock) {
        check(!closed) { "Session is no longer recording" }
        frames += pcm.copyOf()
    }

    override suspend fun finish(): Transcript {
        val samples = synchronized(lock) {
            check(!closed)
            closed = true
            frames.toList().flattenSamples()
        }
        val wav = pcm16Wav(samples)
        val body = buildJsonObject {
            put("user", buildJsonObject { put("uid", UUID.randomUUID().toString()) })
            put("audio", buildJsonObject {
                put("data", wav.toByteString().base64())
                put("format", "wav")
            })
            put("request", buildJsonObject {
                put("model_name", "bigmodel")
                put("enable_itn", true)
                put("enable_punc", true)
                put("enable_ddc", true)
            })
        }
        val response = when (mode) {
            VolcAucMode.Turbo -> post(endpoints.turbo, body, UUID.randomUUID().toString()).requireComplete()
            VolcAucMode.Standard -> {
                val requestId = UUID.randomUUID().toString()
                post(endpoints.submit, body, requestId).requireComplete()
                var final: VolcResponse? = null
                for (attempt in 0 until maxPolls) {
                    val queried = post(endpoints.query, buildJsonObject {}, requestId)
                    when (queried.statusCode) {
                        "20000000" -> { final = queried; break }
                        "20000001", "20000002" -> if (attempt < maxPolls - 1) delay(pollIntervalMs)
                        else -> throw ServiceException(ServiceFailure.Response)
                    }
                }
                final ?: throw ServiceException(ServiceFailure.Timeout)
            }
        }
        val text = extractText(response.body).trim()
        if (text.isBlank()) throw ServiceException(ServiceFailure.Response)
        return Transcript(text, null)
    }

    private suspend fun post(url: String, body: JsonObject, requestId: String): VolcResponse {
        val request = Request.Builder().url(url)
            .header("X-Api-App-Key", appId)
            .header("X-Api-Access-Key", accessToken)
            .header("X-Api-Resource-Id", mode.resourceId)
            .header("X-Api-Request-Id", requestId)
            .header("X-Api-Sequence", "-1")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = execute(request)
        response.use {
            if (!it.isSuccessful) throw httpFailure(it.code)
            val raw = it.body?.string().orEmpty()
            val json = if (raw.isBlank()) buildJsonObject {} else try {
                Json.parseToJsonElement(raw).jsonObject
            } catch (_: Exception) {
                throw ServiceException(ServiceFailure.Response)
            }
            return VolcResponse(
                it.header("X-Api-Status-Code").orEmpty(),
                json,
            )
        }
    }

    private fun VolcResponse.requireComplete(): VolcResponse {
        if (statusCode != "20000000") throw ServiceException(ServiceFailure.Response)
        return this
    }

    private fun extractText(root: JsonObject): String {
        val result = root["result"]?.jsonObject ?: return ""
        val utterances = result["utterances"]?.jsonArray
        if (utterances != null) {
            val parts = utterances.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            if (parts.isNotEmpty()) return parts.joinToString("")
        }
        return result["text"]?.jsonPrimitive?.content.orEmpty()
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val next = client.newCall(request)
        synchronized(lock) { call = next }
        continuation.invokeOnCancellation { next.cancel() }
        next.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(networkFailure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    override fun cancel() {
        synchronized(lock) {
            closed = true
            frames.clear()
            call?.cancel()
        }
    }

    private data class VolcResponse(val statusCode: String, val body: JsonObject)
}
