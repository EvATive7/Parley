package io.github.evative7.parley.asr

import io.github.evative7.parley.audio.flattenSamples
import io.github.evative7.parley.audio.pcm16Wav
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.network.ConnectionWarmer
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.network.httpFailure
import io.github.evative7.parley.network.networkFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenAiTranscriptionRecognizer @Inject constructor(
    private val client: OkHttpClient,
    private val settings: ServiceSettingsSource,
) : SpeechRecognizer {
    override val isStreaming = false
    private val warmer = ConnectionWarmer(client, "OpenAI ASR")

    /** The audio is uploaded after release; have the TLS connection ready by then. */
    override fun prepare() {
        if (settings.current?.takeIf { it.isReady }?.asrService in SUPPORTED_SERVICES) warmer.warm(TRANSCRIPTIONS_URL)
    }

    override fun open(lang: String?): RecognitionSession {
        val config = settings.current?.takeIf {
            it.isReady && it.asrService in SUPPORTED_SERVICES
        } ?: throw ServiceException(ServiceFailure.Configuration)
        val model = when (config.asrService) {
            AsrService.Gpt4oTranscribeNonStream -> "gpt-4o-transcribe"
            AsrService.Gpt4oMiniTranscribeNonStream -> "gpt-4o-mini-transcribe"
            else -> error("Unsupported OpenAI transcription service")
        }
        return OpenAiTranscriptionSession(client, TRANSCRIPTIONS_URL, model, config.openAiAsrKey, lang)
    }

    private companion object {
        const val TRANSCRIPTIONS_URL = "https://api.openai.com/v1/audio/transcriptions"
        val SUPPORTED_SERVICES = setOf(
            AsrService.Gpt4oTranscribeNonStream,
            AsrService.Gpt4oMiniTranscribeNonStream,
        )
    }
}

internal class OpenAiTranscriptionSession(
    private val client: OkHttpClient,
    private val url: String,
    private val model: String,
    private val key: String,
    private val languageHint: String?,
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
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .apply {
                languageHint?.takeIf { it.isNotBlank() }?.let { addFormDataPart("language", Lang(it).base) }
            }
            .build()
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        val response = execute(request)
        response.use {
            if (!it.isSuccessful) throw httpFailure(it.code)
            val raw = it.body?.string().orEmpty()
            val text = try {
                Json.parseToJsonElement(raw).jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
            } catch (_: Exception) {
                throw ServiceException(ServiceFailure.Response)
            }
            if (text.isBlank()) throw ServiceException(ServiceFailure.Response)
            return Transcript(text.trim(), null)
        }
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
}
