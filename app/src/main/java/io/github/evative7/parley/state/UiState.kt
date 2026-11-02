package io.github.evative7.parley.state

import io.github.evative7.parley.asr.AsrAlternative
import io.github.evative7.parley.asr.AsrService
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

    /**
     * The slot whose language [lang] is, or null if it is neither or can't tell them apart: an exact tag
     * first, then the base language, as long as only one side has that base.
     */
    fun slotOf(lang: Lang?): Int? = when {
        lang == null -> null
        lang.tag.equals(first.tag, ignoreCase = true) -> 0
        lang.tag.equals(second.tag, ignoreCase = true) -> 1
        first.base == second.base -> null
        lang.base == first.base -> 0
        lang.base == second.base -> 1
        else -> null
    }

    /**
     * Where speech in [source] is translated: into the other language when it is one of the pair; into both
     * when it is neither, or when it isn't known which, since then nobody can be assumed to understand it.
     */
    fun targetsFor(source: Lang?): List<Lang> = slotOf(source)?.let { listOf(this[1 - it]) } ?: listOf(first, second)

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

/** One translation of a card's speech. */
data class CardTranslation(val to: Lang, val text: String = "")

data class CardState(
    val id: Long,
    /** The pair in effect when the card was recorded. */
    val langs: LangPair,
    val stage: CardStage,
    val source: String = "",
    val sourceLang: Lang? = null,
    /** Which service heard [source], when several ran. */
    val sourceService: AsrService? = null,
    /** What the other ASR services heard, when several ran; the translator weighs them against [source]. */
    val alternatives: List<AsrAlternative> = emptyList(),
    /**
     * Empty until the speech has been recognized; then one per target language, in the pair's order:
     * one for either side's speech, two for anyone else's.
     */
    val translations: List<CardTranslation> = emptyList(),
    val error: ErrorKind? = null,
    /** Only finalized ASR text may be resent to the translator. */
    val canRetry: Boolean = false,
)

/** Translated into both languages: the speaker is neither side, or couldn't be told. */
val CardState.forBoth: Boolean get() = translations.size > 1

/** What one side of the landscape transcript shows for a card. */
sealed interface CardCell {
    /** The speaker's language isn't known yet, so this may turn out to be either side's text. */
    data class Hearing(val text: String) : CardCell
    data class Source(val text: String) : CardCell
    data class Translation(val translation: CardTranslation) : CardCell

    /** Speech that is neither side's: each side gets the whole card, with the translation into its language. */
    data class Whole(val translation: CardTranslation) : CardCell
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
fun CardState.cell(slot: Int): CardCell {
    if (translations.isEmpty()) return CardCell.Hearing(source)
    val mine = translations.firstOrNull { it.to == langs[slot] }
    return when {
        forBoth && mine != null -> CardCell.Whole(mine)
        mine != null -> CardCell.Translation(mine)
        else -> CardCell.Source(source)
    }
}

/**
 * Whether [CardState.langs]' [slot] shows the card's error. A recognition error, or any error on speech
 * translated both ways, concerns both sides; a translation error only the side that was waiting for it.
 */
fun CardState.showsError(slot: Int): Boolean =
    stage == CardStage.Error && (translations.isEmpty() || forBoth || translations.single().to == langs[slot])

enum class CardStage { Listening, Recognizing, Translating, Done, Error }

enum class ErrorKind { Microphone, NoSpeech, Timeout, Configuration, Authentication, Permission, RateLimit, Network, Service, Unknown }

/** Short transient message shown above the button. */
enum class Hint { Copied }
