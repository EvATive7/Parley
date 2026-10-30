package io.github.evative7.parley.asr

import io.github.evative7.parley.model.FakeSamples
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * M1 stand-in for a streaming ASR service. It ignores the audio content and reveals a canned
 * sentence in step with how long the user has been speaking.
 */
class FakeRecognizer @Inject constructor() : SpeechRecognizer {
    override val isStreaming = true

    private var next = 0

    override fun open(lang: String?): RecognitionSession {
        val sample = FakeSamples.all[next++ % FakeSamples.all.size]
        return Session(sample)
    }

    private class Session(private val sample: FakeSamples.Sample) : RecognitionSession {
        private val chunks = FakeSamples.chunks(sample.text)
        private var samples = 0L
        private var shown = 0
        override val partials = MutableStateFlow("")

        override fun feed(pcm: ShortArray) {
            samples += pcm.size
            // One chunk per 250 ms of audio, so the text keeps pace with speech.
            val due = (samples / SAMPLES_PER_CHUNK).toInt().coerceAtMost(chunks.size - 1)
            if (due > shown) {
                shown = due
                partials.value = chunks.take(shown).joinToString("")
            }
        }

        override suspend fun finish(): Transcript {
            delay(350)
            partials.value = sample.text
            return Transcript(sample.text, sample.lang)
        }

        override fun cancel() = Unit
    }

    private companion object {
        const val SAMPLES_PER_CHUNK = 16_000 / 4
    }
}
