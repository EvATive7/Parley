package io.github.evative7.parley.tts

import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.mock.MockSpeed
import io.github.evative7.parley.mock.MockTtsFault
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/** Offline speech-shaped sine tone for exercising TTS UI and interruption paths. */
@Singleton
class MockSynthesizer @Inject constructor(private val settings: ServiceSettingsSource) : SpeechSynthesizer {
    override fun open(lang: Lang, voice: String): SynthesisSession {
        val saved = settings.current
        return MockSynthesisSession(
            saved?.mockTtsSpeed ?: MockSpeed.Normal,
            saved?.mockTtsFault ?: MockTtsFault.None,
        )
    }
}

private class MockSynthesisSession(
    private val speed: MockSpeed,
    private val fault: MockTtsFault,
) : SynthesisSession {
    private val text = Channel<String>(Channel.UNLIMITED)

    override val audio: Flow<ByteArray> = flow {
        if (fault == MockTtsFault.Authentication) throw ServiceException(ServiceFailure.Authentication)
        if (fault == MockTtsFault.Timeout) {
            delay(15_000)
            throw ServiceException(ServiceFailure.Timeout)
        }
        var emitted = false
        for (part in text) {
            val durationMs = ((300 + part.length * 22) * speed.factor()).toInt().coerceAtLeast(150)
            val samples = durationMs * SAMPLE_RATE / 1_000
            var offset = 0
            while (offset < samples) {
                delay(speed.chunkMs.coerceAtLeast(5))
                val count = minOf(CHUNK_SAMPLES, samples - offset)
                val bytes = ByteArray(count * 2)
                for (i in 0 until count) {
                    val sample = (sin(2.0 * PI * FREQUENCY * (offset + i) / SAMPLE_RATE) * Short.MAX_VALUE * 0.12).toInt()
                    bytes[i * 2] = sample.toByte()
                    bytes[i * 2 + 1] = (sample shr 8).toByte()
                }
                emit(bytes)
                offset += count
                if (fault == MockTtsFault.NetworkMidway && emitted) throw ServiceException(ServiceFailure.Network)
                emitted = true
            }
        }
    }

    override fun append(text: String) {
        check(this.text.trySend(text).isSuccess) { "Synthesis session is already finished" }
    }

    override fun finish() {
        text.close()
    }

    private fun MockSpeed.factor(): Double = when (this) {
        MockSpeed.Fast -> 0.55
        MockSpeed.Normal -> 1.0
        MockSpeed.Slow -> 2.2
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val FREQUENCY = 330.0
        const val CHUNK_SAMPLES = 2_400
    }
}
