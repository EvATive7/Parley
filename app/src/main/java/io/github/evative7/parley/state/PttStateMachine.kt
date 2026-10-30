package io.github.evative7.parley.state

/** The seven states of the push-to-talk button. */
enum class PttPhase { Idle, Recording, CancelArmed, Recognizing, Translating, Result, Error }

sealed interface PttEvent {
    /** Finger touched the button. */
    data object Press : PttEvent

    /** Finger moved; [armed] is true once it is more than 80 dp above the press point. */
    data class Drag(val armed: Boolean) : PttEvent

    /** Finger lifted after [heldMs]. */
    data class Release(val heldMs: Long) : PttEvent

    /** Recording hit the 60 s limit. */
    data object TimeLimit : PttEvent

    /** Gesture cancelled, app went to background, or a call came in. */
    data object Abort : PttEvent

    data object Recognized : PttEvent
    data object Translated : PttEvent
    data object Failed : PttEvent

    /** User tapped retry on a failed card. */
    data object Retry : PttEvent
}

sealed interface PttEffect {
    data object StartRecording : PttEffect

    /** Stop the microphone and send the audio for recognition. */
    data object Submit : PttEffect

    /** Translate the failed card's recognized text again. */
    data object Resubmit : PttEffect

    data class Discard(val reason: Reason) : PttEffect {
        enum class Reason { TooShort, Cancelled, Aborted }
    }

    /** A new sentence started while the last one was still translating: stop it, keep its text. */
    data object InterruptTranslation : PttEffect
}

data class Transition(val phase: PttPhase, val effects: List<PttEffect> = emptyList())

/** Pure reducer for the push-to-talk flow; side effects are returned, not performed. */
object PttStateMachine {
    const val MIN_HOLD_MS = 500L
    const val MAX_RECORD_MS = 60_000L

    fun reduce(phase: PttPhase, event: PttEvent): Transition {
        val stay = Transition(phase)
        return when (event) {
            PttEvent.Press -> when (phase) {
                PttPhase.Idle, PttPhase.Result, PttPhase.Error ->
                    Transition(PttPhase.Recording, listOf(PttEffect.StartRecording))
                PttPhase.Translating ->
                    Transition(PttPhase.Recording, listOf(PttEffect.InterruptTranslation, PttEffect.StartRecording))
                else -> stay
            }

            is PttEvent.Drag -> when {
                phase == PttPhase.Recording && event.armed -> Transition(PttPhase.CancelArmed)
                phase == PttPhase.CancelArmed && !event.armed -> Transition(PttPhase.Recording)
                else -> stay
            }

            is PttEvent.Release -> when (phase) {
                PttPhase.Recording ->
                    if (event.heldMs < MIN_HOLD_MS) discard(PttEffect.Discard.Reason.TooShort)
                    else Transition(PttPhase.Recognizing, listOf(PttEffect.Submit))
                PttPhase.CancelArmed -> discard(PttEffect.Discard.Reason.Cancelled)
                else -> stay
            }

            PttEvent.TimeLimit -> when (phase) {
                PttPhase.Recording -> Transition(PttPhase.Recognizing, listOf(PttEffect.Submit))
                PttPhase.CancelArmed -> discard(PttEffect.Discard.Reason.Cancelled)
                else -> stay
            }

            PttEvent.Abort -> when (phase) {
                PttPhase.Recording, PttPhase.CancelArmed -> discard(PttEffect.Discard.Reason.Aborted)
                else -> stay
            }

            PttEvent.Recognized ->
                if (phase == PttPhase.Recognizing) Transition(PttPhase.Translating) else stay

            PttEvent.Translated ->
                if (phase == PttPhase.Translating) Transition(PttPhase.Result) else stay

            PttEvent.Failed -> when (phase) {
                PttPhase.Recording, PttPhase.Recognizing, PttPhase.Translating -> Transition(PttPhase.Error)
                else -> stay
            }

            PttEvent.Retry -> when (phase) {
                PttPhase.Idle, PttPhase.Result, PttPhase.Error ->
                    Transition(PttPhase.Translating, listOf(PttEffect.Resubmit))
                else -> stay
            }
        }
    }

    private fun discard(reason: PttEffect.Discard.Reason) =
        Transition(PttPhase.Idle, listOf(PttEffect.Discard(reason)))
}
