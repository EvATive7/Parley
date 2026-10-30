package io.github.evative7.parley.asr

import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.flow.Flow

/** A cloud speech-to-text service. Streaming and whole-file services share this interface. */
interface SpeechRecognizer {
    /** Whether [RecognitionSession.partials] emits text while the user is still speaking. */
    val isStreaming: Boolean

    /** Starts a session. [lang] is a hint; `null` lets the service detect the language. */
    fun open(lang: String?): RecognitionSession
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

data class Transcript(val text: String, val lang: Lang?)
