package io.github.evative7.parley.mock

import io.github.evative7.parley.asr.RecognitionSession
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.ServiceRecognizer
import io.github.evative7.parley.asr.Transcript
import io.github.evative7.parley.data.LanguageRepository
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.state.LangPair
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recognizes nothing: the microphone runs as usual, but each press "hears" the next sample sentence,
 * alternating between the two languages of the bar so both directions get translated. With both mock services
 * selected, they hear the same sentence; the whole-file one a moment later and without its final punctuation,
 * so there is a second transcript to compare.
 */
@Singleton
class MockRecognizer @Inject constructor(
    private val settings: ServiceSettingsSource,
    private val languages: LanguageRepository,
) : ServiceRecognizer {
    private var turn = 0
    // Sessions opened this close together belong to one press.
    private var lastOpenNs = Long.MIN_VALUE
    private var pressTurn = 0

    private val speed: MockSpeed get() = settings.current?.mockAsrSpeed ?: MockSpeed.Normal
    private val fault: MockAsrFault get() = settings.current?.mockAsrFault ?: MockAsrFault.None

    @Synchronized
    override fun open(service: AsrService, lang: String?): RecognitionSession {
        val langs = languages.current.value ?: LangPair.default()
        val now = System.nanoTime()
        if (lastOpenNs == Long.MIN_VALUE || now - lastOpenNs > SAME_PRESS_NS) pressTurn = turn++
        lastOpenNs = now
        val n = pressTurn
        // Each language gets its turn, and the sentences run through the list.
        val speaker = if (n % 2 == 0) langs.first else langs.second
        val phrase = MockPhrases.get(n / 2, speaker)
        val text = if (service.streaming) phrase else phrase.trimEnd { !it.isLetterOrDigit() }
        DebugLog.log(TAG) { "open ${service.id}: will hear ${speaker.tag}, speed=$speed, fault=$fault" }
        return Session(text, speaker, service.streaming)
    }

    private inner class Session(
        private val text: String,
        private val lang: Lang,
        private val streaming: Boolean,
    ) : RecognitionSession {
        private val heard = MutableStateFlow("")
        private var samples = 0L

        override val partials: Flow<String> = if (streaming) heard.asStateFlow() else emptyFlow()

        override fun feed(pcm: ShortArray) {
            samples += pcm.size
            val seconds = samples / SAMPLE_RATE.toDouble()
            if (fault == MockAsrFault.NetworkWhileRecording && seconds >= 1.0) {
                throw ServiceException(ServiceFailure.Network)
            }
            if (streaming) heard.value = prefix(seconds)
        }

        override suspend fun finish(): Transcript {
            delay(speed.recognizeMs + if (streaming) 0 else WHOLE_FILE_EXTRA_MS)
            return when (fault) {
                MockAsrFault.None -> Transcript(text, lang)
                MockAsrFault.NoSpeech -> Transcript("", null)
                MockAsrFault.Authentication -> throw ServiceException(ServiceFailure.Authentication, 401)
                MockAsrFault.NetworkWhileRecording -> throw ServiceException(ServiceFailure.Network)
                // Never answers; the pipeline's own timeout ends it.
                MockAsrFault.Timeout -> awaitCancellation()
            }
        }

        override fun cancel() {}

        /** As much of the sentence as [seconds] of speech would have reached, cut at a word where there are words. */
        private fun prefix(seconds: Double): String {
            val chars = (seconds * CHARS_PER_SECOND).toInt().coerceAtMost(text.length)
            if (chars >= text.length) return text
            val cut = text.take(chars)
            val space = cut.lastIndexOf(' ')
            return if (' ' in text && space > 0) cut.take(space) else cut
        }
    }

    private companion object {
        const val TAG = "MockASR"
        const val SAMPLE_RATE = 16_000
        const val CHARS_PER_SECOND = 12
        const val SAME_PRESS_NS = 500_000_000L
        const val WHOLE_FILE_EXTRA_MS = 400L
    }
}
