package io.github.evative7.parley.state

import io.github.evative7.parley.model.Lang

data class UiState(
    val phase: PttPhase = PttPhase.Idle,
    val target: Lang = Lang.defaultTarget(),
    /** Newest first. */
    val cards: List<CardState> = emptyList(),
    val hint: Hint? = null,
)

data class CardState(
    val id: Long,
    val target: Lang,
    val stage: CardStage,
    val source: String = "",
    val sourceLang: Lang? = null,
    val translation: String = "",
    val error: ErrorKind? = null,
)

enum class CardStage { Listening, Recognizing, Translating, Done, Error }

enum class ErrorKind { Microphone, NoSpeech, Timeout, Unknown }

/** Short transient message shown above the button. */
enum class Hint { HoldToTalk, Copied }
