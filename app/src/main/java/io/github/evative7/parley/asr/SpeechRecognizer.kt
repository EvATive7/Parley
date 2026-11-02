package io.github.evative7.parley.asr

import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A cloud speech-to-text service. Streaming and whole-file services share this interface. */
interface SpeechRecognizer {
    /** Whether [RecognitionSession.partials] emits text while the user is still speaking. */
    val isStreaming: Boolean

    /** The screen is visible: connect ahead of the next press. Sends no audio. */
    fun prepare() {}

    /** The screen is gone: drop anything [prepare] opened. */
    fun release() {}

    /** Starts a session. [lang] is a hint; `null` lets the service detect the language. */
    fun open(lang: String?): RecognitionSession
}

/**
 * The recognizers of one provider. Several of its services may be selected at once, so each call names the
 * one it is for; credentials still come from the settings.
 */
interface ServiceRecognizer {
    /** The screen is visible: connect ahead of the next press for [service]. Sends no audio. */
    fun prepare(service: AsrService) {}

    /** The screen is gone, or none of this provider's services is selected any more. */
    fun release() {}

    fun open(service: AsrService, lang: String?): RecognitionSession
}

interface RecognitionSession {
    /** Feeds one frame of 16 kHz mono PCM. Whole-file services buffer it until [finish]. */
    fun feed(pcm: ShortArray)

    /** Running transcript of what has been heard so far. Empty for whole-file services. */
    val partials: Flow<String>

    /** Ends the audio and returns the final transcript. Whole-file services upload here. */
    suspend fun finish(): Transcript

    /** Drops the session without sending anything further. */
    fun cancel()
}

data class Transcript(
    val text: String,
    val lang: Lang?,
    /** Which of several selected services heard [text]; null with only one. */
    val service: AsrService? = null,
    /** What the other selected services heard in time to help the translation. */
    val alternatives: List<AsrAlternative> = emptyList(),
    /** What slower services hear afterwards; it completes once all have answered or given up. */
    val late: Flow<AsrAlternative> = emptyFlow(),
)

/** One other service's transcript of the same speech. */
data class AsrAlternative(val service: AsrService, val text: String)
