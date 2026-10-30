package io.github.evative7.parley.state

import io.github.evative7.parley.model.Lang

data class UiState(
    val phase: PttPhase = PttPhase.Idle,
    /** The two sides of the conversation; speech in one is translated into the other. */
    val langs: LangPair = LangPair.default(),
    /** Newest first. */
    val cards: List<CardState> = emptyList(),
    val hint: Hint? = null,
)

data class LangPair(val first: Lang, val second: Lang) {
    operator fun get(slot: Int): Lang = if (slot == 0) first else second

    fun swapped() = LangPair(second, first)

    /** Speech in [second] goes to [first]; anything else, including an undetected language, goes to [second]. */
    fun targetFor(source: Lang?): Lang = if (source?.base == second.base) first else second

    /** Puts [lang] in [slot]; picking the language the other slot holds swaps the two. */
    fun with(slot: Int, lang: Lang): LangPair = when {
        lang == this[1 - slot] -> swapped()
        slot == 0 -> copy(first = lang)
        else -> copy(second = lang)
    }

    companion object {
        fun default(): LangPair = Lang.defaultFirst().let { LangPair(it, Lang.defaultSecond(it)) }
    }
}

data class CardState(
    val id: Long,
    /** The pair in effect when the card was recorded. */
    val langs: LangPair,
    val stage: CardStage,
    val source: String = "",
    val sourceLang: Lang? = null,
    /** Known once the source language has been recognized. */
    val target: Lang? = null,
    val translation: String = "",
    val error: ErrorKind? = null,
)

enum class CardStage { Listening, Recognizing, Translating, Done, Error }

enum class ErrorKind { Microphone, NoSpeech, Timeout, Unknown }

/** Short transient message shown above the button. */
enum class Hint { Copied }
