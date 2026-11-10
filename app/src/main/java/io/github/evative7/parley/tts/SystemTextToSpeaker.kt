package io.github.evative7.parley.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Device-provided TTS. Availability and quality depend on the installed engine and voice data. */
@Singleton
class SystemTextToSpeaker @Inject constructor(@ApplicationContext context: Context) : TextToSpeaker {
    private val initialized = CompletableDeferred<Int>()
    private val engine = TextToSpeech(context.applicationContext) { status -> initialized.complete(status) }
    private val ids = AtomicLong()

    override fun speak(key: SpeechKey, text: String, lang: Lang): Flow<PlaybackState> = callbackFlow {
        trySend(PlaybackState.Preparing)
        val utteranceId = "parley-${ids.incrementAndGet()}"
        launch(Dispatchers.Main.immediate) {
            if (initialized.await() != TextToSpeech.SUCCESS) {
                trySend(PlaybackState.Failed("System text-to-speech could not start"))
                close()
                return@launch
            }
            val available = engine.isLanguageAvailable(lang.locale)
            if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
                val message = "System text-to-speech has no voice for ${lang.tag}"
                DebugLog.log(TAG) { message }
                trySend(PlaybackState.Failed(message))
                close()
                return@launch
            }
            engine.language = lang.locale
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id == utteranceId) trySend(PlaybackState.Playing)
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        trySend(PlaybackState.Done)
                        close()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = onError(id, TextToSpeech.ERROR)

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) {
                        trySend(PlaybackState.Failed("System text-to-speech failed ($errorCode)"))
                        close()
                    }
                }
            })
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
                trySend(PlaybackState.Failed("System text-to-speech rejected the request"))
                close()
            }
        }
        awaitClose { }
    }

    override fun stop() {
        engine.stop()
    }

    private companion object {
        const val TAG = "TTS"
    }
}
