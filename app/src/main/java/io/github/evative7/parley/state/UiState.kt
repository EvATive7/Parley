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
    /** Only finalized ASR text may be resent to the translator. */
    val canRetry: Boolean = false,
)

/** What one side of the landscape transcript shows for a card. */
sealed interface CardCell {
    /** The speaker's language isn't known yet, so this may turn out to be either side's text. */
    data class Hearing(val text: String) : CardCell
    data class Source(val text: String) : CardCell
    data class Translation(val text: String) : CardCell
}

/**
 * The slot of [CardState.langs] shown on the side that holds [current]'s [slot] now. Cards keep the pair
 * they were recorded with, so after a swap or a new pick they follow their languages to the matching side.
 */
fun CardState.slotFor(current: LangPair, slot: Int): Int {
    fun match(lang: Lang): Int? = when {
        lang == langs.first -> 0
        lang == langs.second -> 1
        lang.base == langs.first.base -> 0
        lang.base == langs.second.base -> 1
        else -> null
    }
    val first = match(current.first) ?: match(current.second)?.let { 1 - it } ?: 0
    return if (slot == 0) first else 1 - first
}

/** The text for [CardState.langs]' [slot]: the translation on the target's side, the speech on the other. */
fun CardState.cell(slot: Int): CardCell = when (target) {
    null -> CardCell.Hearing(source)
    langs[slot] -> CardCell.Translation(translation)
    else -> CardCell.Source(source)
}

enum class CardStage { Listening, Recognizing, Translating, Done, Error }

enum class ErrorKind { Microphone, NoSpeech, Timeout, Configuration, Authentication, Permission, RateLimit, Network, Service, Unknown }

/** Short transient message shown above the button. */
enum class Hint { Copied }
