package io.github.evative7.parley.asr

import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.mock.MockRecognizer
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingRecognizer internal constructor(
    private val settings: ServiceSettingsSource,
    private val resolver: (AsrService) -> SpeechRecognizer,
) : SpeechRecognizer {
    @Inject constructor(
        settings: ServiceSettingsSource,
        dashScope: DashScopeRecognizer,
        openAiRealtime: OpenAiRealtimeRecognizer,
        openAiTranscription: OpenAiTranscriptionRecognizer,
        volcengineSauc: VolcengineSaucRecognizer,
        volcengineAuc: VolcengineAucRecognizer,
        mock: MockRecognizer,
    ) : this(settings, { service ->
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

    private var preparedService: AsrService? = null
    private var preparedRecognizer: SpeechRecognizer? = null

    override val isStreaming: Boolean
        get() = settings.current?.asrService?.streaming ?: AsrService.Default.streaming

    override fun prepare() {
        synchronized(this) {
            val service = settings.current?.takeIf { it.isReady }?.asrService
            if (service == null) {
                DebugLog.log(TAG) { "prepare skipped: settings not ready" }
                preparedRecognizer?.release()
                preparedRecognizer = null
                preparedService = null
                return
            }
            val recognizer = resolver(service)
            if (preparedService != service) {
                preparedRecognizer?.release()
                preparedService = service
                preparedRecognizer = recognizer
            }
            DebugLog.log(TAG) { "prepare ${service.id}" }
            recognizer.prepare()
        }
    }

    override fun release() = synchronized(this) {
        preparedRecognizer?.release()
        preparedRecognizer = null
        preparedService = null
    }

    override fun open(lang: String?): RecognitionSession {
        val service = settings.current?.takeIf { it.isReady }?.asrService
            ?: throw ServiceException(ServiceFailure.Configuration)
        val recognizer = resolver(service)
        synchronized(this) {
            if (preparedService != null && preparedService != service) preparedRecognizer?.release()
            preparedService = service
            preparedRecognizer = recognizer
        }
        DebugLog.log(TAG) { "open ${service.id}" }
        return recognizer.open(lang)
    }

    private companion object { const val TAG = "ASR" }
}
