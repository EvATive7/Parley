package io.github.evative7.parley.mock

import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.translate.Translator
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates sample sentences into their prepared versions and marks anything else as a mock translation,
 * streaming a few characters at a time like a model would.
 */
@Singleton
class MockTranslator @Inject constructor(private val settings: ServiceSettingsSource) : Translator {
    override fun translate(text: String, from: Lang?, to: Lang): Flow<String> = flow {
        val preset = settings.current?.selectedTranslation
        val speed = preset?.mockSpeed ?: MockSpeed.Normal
        val fault = preset?.mockFault ?: MockTranslationFault.None
        DebugLog.log(TAG) { "translate ${from?.tag ?: "auto"} → ${to.tag}, speed=$speed, fault=$fault" }
        delay(speed.firstTokenMs)
        when (fault) {
            MockTranslationFault.Authentication -> throw ServiceException(ServiceFailure.Authentication, 401)
            MockTranslationFault.RateLimit -> throw ServiceException(ServiceFailure.RateLimit, 429)
            // Never answers; the pipeline's own timeout ends it.
            MockTranslationFault.Timeout -> awaitCancellation()
            MockTranslationFault.None, MockTranslationFault.NetworkMidway -> Unit
        }
        val output = MockPhrases.translate(text, to) ?: "〔Mock → ${to.nativeName()}〕$text"
        // Whole code points, so emoji and other surrogate pairs are never split.
        val points = output.codePoints().toArray()
        var i = 0
        while (i < points.size) {
            if (fault == MockTranslationFault.NetworkMidway && i >= points.size / 2) {
                throw ServiceException(ServiceFailure.Network)
            }
            val end = minOf(i + CHUNK, points.size)
            emit(String(points, i, end - i))
            i = end
            if (i < points.size) delay(speed.chunkMs)
        }
    }

    private companion object {
        const val TAG = "MockLLM"
        const val CHUNK = 3
    }
}
