package io.github.evative7.parley.state

import io.github.evative7.parley.state.PttEffect.Discard.Reason
import org.junit.Assert.assertEquals
import org.junit.Test

class PttStateMachineTest {

    private fun reduce(phase: PttPhase, event: PttEvent) = PttStateMachine.reduce(phase, event)

    @Test
    fun `press starts recording from idle, result and error`() {
        for (phase in listOf(PttPhase.Idle, PttPhase.Result, PttPhase.Error)) {
            assertEquals(
                Transition(PttPhase.Recording, listOf(PttEffect.StartRecording)),
                reduce(phase, PttEvent.Press),
            )
        }
    }

    @Test
    fun `press while translating interrupts it first`() {
        assertEquals(
            listOf(PttEffect.InterruptTranslation, PttEffect.StartRecording),
            reduce(PttPhase.Translating, PttEvent.Press).effects,
        )
    }

    @Test
    fun `press while recognizing is ignored`() {
        assertEquals(Transition(PttPhase.Recognizing), reduce(PttPhase.Recognizing, PttEvent.Press))
    }

    @Test
    fun `short hold is discarded as a mis-tap`() {
        assertEquals(
            Transition(PttPhase.Idle, listOf(PttEffect.Discard(Reason.TooShort))),
            reduce(PttPhase.Recording, PttEvent.Release(heldMs = 499)),
        )
    }

    @Test
    fun `long enough hold is submitted`() {
        assertEquals(
            Transition(PttPhase.Recognizing, listOf(PttEffect.Submit)),
            reduce(PttPhase.Recording, PttEvent.Release(heldMs = 500)),
        )
    }

    @Test
    fun `slide up arms cancel and slide back disarms`() {
        assertEquals(PttPhase.CancelArmed, reduce(PttPhase.Recording, PttEvent.Drag(armed = true)).phase)
        assertEquals(PttPhase.Recording, reduce(PttPhase.CancelArmed, PttEvent.Drag(armed = false)).phase)
    }

    @Test
    fun `release while armed cancels`() {
        assertEquals(
            Transition(PttPhase.Idle, listOf(PttEffect.Discard(Reason.Cancelled))),
            reduce(PttPhase.CancelArmed, PttEvent.Release(heldMs = 5_000)),
        )
    }

    @Test
    fun `time limit submits`() {
        assertEquals(PttEffect.Submit, reduce(PttPhase.Recording, PttEvent.TimeLimit).effects.single())
    }

    @Test
    fun `abort only matters while recording`() {
        assertEquals(PttPhase.Idle, reduce(PttPhase.Recording, PttEvent.Abort).phase)
        assertEquals(Transition(PttPhase.Translating), reduce(PttPhase.Translating, PttEvent.Abort))
    }

    @Test
    fun `pipeline runs recognizing to translating to result`() {
        assertEquals(PttPhase.Translating, reduce(PttPhase.Recognizing, PttEvent.Recognized).phase)
        assertEquals(PttPhase.Result, reduce(PttPhase.Translating, PttEvent.Translated).phase)
        assertEquals(PttPhase.Error, reduce(PttPhase.Translating, PttEvent.Failed).phase)
    }

    @Test
    fun `retry from error translates again`() {
        assertEquals(
            Transition(PttPhase.Translating, listOf(PttEffect.Resubmit)),
            reduce(PttPhase.Error, PttEvent.Retry),
        )
    }
}
