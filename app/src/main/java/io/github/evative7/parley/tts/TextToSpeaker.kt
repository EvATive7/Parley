package io.github.evative7.parley.tts

import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.flow.Flow

/** Reads translations aloud. Cards and the view model only depend on this interface. */
interface TextToSpeaker {
    fun speak(key: SpeechKey, text: String, lang: Lang): Flow<PlaybackState>
    fun stop()
}

/** A network synthesizer. Every session produces PCM 16-bit mono audio at 24 kHz. */
interface SpeechSynthesizer {
    fun open(lang: Lang, voice: String): SynthesisSession
}

interface SynthesisSession {
    val audio: Flow<ByteArray>
    fun append(text: String)
    fun finish()
}

data class SpeechKey(
    val cardId: Long,
    val lang: Lang,
    val service: TtsService,
    val voice: String,
    val text: String,
)

sealed interface PlaybackState {
    data object Preparing : PlaybackState
    data object Playing : PlaybackState
    data object Done : PlaybackState
    /** [noVoice]: nothing was sent, because the service has no voice for the language. */
    data class Failed(val message: String? = null, val noVoice: Boolean = false) : PlaybackState
}
