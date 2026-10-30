package io.github.evative7.parley.tts

import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.flow.Flow

/** Reads a translation aloud. V1 defines the interface only; there is no implementation yet. */
interface TextToSpeaker {
    fun speak(text: String, lang: Lang): Flow<PlaybackState>
    fun stop()
}

enum class PlaybackState { Preparing, Playing, Done }
