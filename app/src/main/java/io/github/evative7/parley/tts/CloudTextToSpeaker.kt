package io.github.evative7.parley.tts

import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import java.io.ByteArrayOutputStream

/** Connects a streaming network synthesizer to playback and the memory-only replay cache. */
class CloudTextToSpeaker(
    private val synthesizer: SpeechSynthesizer,
    private val player: PcmPlayer,
    private val cache: SpeechCache,
) : TextToSpeaker {
    override fun speak(key: SpeechKey, text: String, lang: Lang): Flow<PlaybackState> = channelFlow {
        // A channel, not a plain flow: the player reports the first audio from its own thread.
        send(PlaybackState.Preparing)
        val started = System.nanoTime()
        val cached = cache.get(key)
        val pcm = ByteArrayOutputStream()
        try {
            val audio = if (cached != null) {
                flowOf(cached)
            } else {
                val session = synthesizer.open(lang, key.voice)
                session.append(text)
                session.finish()
                session.audio.onEach(pcm::write)
            }
            var first = true
            val whole = player.play(audio) {
                if (first) {
                    first = false
                    val firstMs = (System.nanoTime() - started) / 1_000_000
                    DebugLog.log(TAG) { "#${key.cardId} TTS ${text.length} chars; first audio ${firstMs} ms" }
                    send(PlaybackState.Playing)
                }
            }
            // A clip cut short is not kept, or a replay would stop at the same place.
            if (cached == null && whole) cache.put(key, pcm.toByteArray())
            send(PlaybackState.Done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.log(TAG) { "#${key.cardId} TTS failed (${e.javaClass.simpleName}: ${e.message})" }
            send(PlaybackState.Failed(e.message))
        }
    }

    override fun stop() = player.stop()

    private companion object {
        const val TAG = "TTS"
    }
}
