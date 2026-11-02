package io.github.evative7.parley.asr

import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.mock.MockRecognizer
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import javax.inject.Singleton

/** Sends each press to every selected service at once; see [MultiSession] for how their answers combine. */
@Singleton
class RoutingRecognizer internal constructor(
    private val settings: ServiceSettingsSource,
    private val context: CoroutineContext,
    private val resolver: (AsrService) -> ServiceRecognizer,
) : SpeechRecognizer {
    @Inject constructor(
        settings: ServiceSettingsSource,
        dashScope: DashScopeRecognizer,
        openAiRealtime: OpenAiRealtimeRecognizer,
        openAiTranscription: OpenAiTranscriptionRecognizer,
        volcengineSauc: VolcengineSaucRecognizer,
        volcengineAuc: VolcengineAucRecognizer,
        mock: MockRecognizer,
    ) : this(settings, Dispatchers.IO, { service ->
        when (service) {
            AsrService.FunAsrRealtimeStream,
            AsrService.FunAsrRealtimeNonStream -> dashScope
            AsrService.GptRealtimeWhisper -> openAiRealtime
            AsrService.Gpt4oTranscribeNonStream,
            AsrService.Gpt4oMiniTranscribeNonStream -> openAiTranscription
            AsrService.DoubaoSeedAsr20Stream -> volcengineSauc
            AsrService.DoubaoSeedAsr20NonStream,
            AsrService.DoubaoSeedAsr20NonStreamTurbo -> volcengineAuc
            AsrService.MockStream,
            AsrService.MockNonStream -> mock
        }
    })

    // Guarded by `this`. Released once none of their services is selected any more.
    private var prepared: Set<ServiceRecognizer> = emptySet()

    private val selected: List<AsrService>?
        get() = settings.current?.takeIf { it.isReady }?.asrServices

    override val isStreaming: Boolean
        get() = (settings.current?.asrServices ?: listOf(AsrService.Default)).any { it.streaming }

    override fun prepare() {
        synchronized(this) {
            val services = selected
            if (services == null) {
                DebugLog.log(TAG) { "prepare skipped: settings not ready" }
                adopt(emptyList())
                return
            }
            adopt(services)
            for (service in services) {
                DebugLog.log(TAG) { "prepare ${service.id}" }
                resolver(service).prepare(service)
            }
        }
    }

    override fun release() = synchronized(this) { adopt(emptyList()) }

    override fun open(lang: String?): RecognitionSession {
        val services = selected ?: throw ServiceException(ServiceFailure.Configuration)
        synchronized(this) { adopt(services) }
        val sessions = mutableListOf<Pair<AsrService, RecognitionSession>>()
        var failure: Exception? = null
        for (service in services) {
            try {
                sessions += service to resolver(service).open(service, lang)
                DebugLog.log(TAG) { "open ${service.id}" }
            } catch (e: Exception) {
                DebugLog.log(TAG) { "open ${service.id} failed: ${e.javaClass.simpleName}" }
                if (failure == null) failure = e
            }
        }
        if (sessions.isEmpty()) throw failure!!
        val graceMs = settings.current?.asrGraceMs ?: ServiceSettings.DEFAULT_ASR_GRACE_MS
        return sessions.singleOrNull()?.second ?: MultiSession(sessions, graceMs, LATE_MS, context)
    }

    /** Makes the recognizers of [services] the prepared ones, releasing the rest. Call holding `this`. */
    private fun adopt(services: List<AsrService>) {
        val next = services.map(resolver).toSet()
        (prepared - next).forEach { it.release() }
        prepared = next
    }

    private companion object {
        const val TAG = "ASR"

        /** How long after the grace period ([ServiceSettings.asrGraceMs]) the other answers are still shown. */
        const val LATE_MS = 10_000L
    }
}

/**
 * Several services hearing the same speech. Audio goes to all of them; the first streaming one shows its words
 * while the user speaks. The first non-empty transcript wins; the others get [graceMs] more to arrive as
 * [Transcript.alternatives], which the translator weighs. Slower ones still arrive, for [lateMs] more, as
 * [Transcript.late]: shown, but too late for the translation. One service failing is not the press failing:
 * only all of them failing is.
 */
internal class MultiSession(
    private val sessions: List<Pair<AsrService, RecognitionSession>>,
    private val graceMs: Long,
    private val lateMs: Long,
    context: CoroutineContext,
) : RecognitionSession {
    // Guarded by `this`. Failed while recording, in the order they failed.
    private val failed = LinkedHashMap<AsrService, Exception>()

    // The services' finishes run here, so the slow ones can outlive [finish].
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val results = Channel<Pair<AsrService, Result<Transcript>>>(Channel.UNLIMITED)

    override val partials: Flow<String> =
        sessions.firstOrNull { it.first.streaming }?.second?.partials ?: emptyFlow()

    override fun feed(pcm: ShortArray) {
        for ((service, session) in sessions) {
            if (synchronized(this) { service in failed }) continue
            try {
                session.feed(pcm)
            } catch (e: Exception) {
                DebugLog.log(TAG) { "${service.id} failed while recording: ${e.javaClass.simpleName}" }
                session.cancel()
                synchronized(this) { failed[service] = e }
            }
        }
        synchronized(this) { if (failed.size == sessions.size) throw failed.values.first() }
    }

    override suspend fun finish(): Transcript {
        val live = synchronized(this) { sessions.filter { it.first !in failed } }
        var error: Throwable? = synchronized(this) { failed.values.firstOrNull() }
        for ((service, session) in live) {
            scope.launch {
                val result = try {
                    Result.success(session.finish())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
                results.send(service to result)
            }
        }
        // In order of arrival; the first is the winner.
        val heard = LinkedHashMap<AsrService, Transcript>()
        var answered = false
        var remaining = live.size
        suspend fun receive() {
            val (service, result) = results.receive()
            remaining--
            result.onSuccess {
                answered = true
                DebugLog.log(TAG) { "${service.id} final: ${it.text.length} chars" }
                if (it.text.isNotBlank()) heard[service] = it
            }.onFailure {
                DebugLog.log(TAG) { "${service.id} failed: ${it.javaClass.simpleName}" }
                if (error == null) error = it
            }
        }
        try {
            while (remaining > 0 && heard.isEmpty()) receive()
            if (heard.isNotEmpty() && remaining > 0) {
                withTimeoutOrNull(graceMs) { while (remaining > 0) receive() }
            }
        } catch (e: CancellationException) {
            cancel()
            throw e
        }

        val winner = heard.entries.firstOrNull() ?: run {
            cancel()
            // Nobody heard anything: an empty answer is no speech; no answer at all is the first failure.
            if (answered) return Transcript("", null)
            throw error ?: ServiceException(ServiceFailure.Response)
        }
        val alternatives = heard.entries
            .filter { it.key != winner.key }
            .map { AsrAlternative(it.key, it.value.text.trim()) }
        DebugLog.log(TAG) {
            "${winner.key.id} first; ${alternatives.size} other(s) in the grace period, $remaining still out"
        }
        val late = if (remaining == 0) {
            cancel()
            emptyFlow()
        } else flow {
            try {
                withTimeoutOrNull(lateMs) {
                    while (remaining > 0) {
                        val (service, result) = results.receive()
                        remaining--
                        val text = result.getOrNull()?.text?.trim()
                        DebugLog.log(TAG) { "${service.id} late: ${text?.let { "${it.length} chars" } ?: "failed"}" }
                        if (!text.isNullOrEmpty()) emit(AsrAlternative(service, text))
                    }
                }
            } finally {
                cancel()
            }
        }
        return winner.value.copy(service = winner.key, alternatives = alternatives, late = late)
    }

    override fun cancel() {
        scope.cancel()
        sessions.forEach { it.second.cancel() }
    }

    private companion object { const val TAG = "ASR" }
}
