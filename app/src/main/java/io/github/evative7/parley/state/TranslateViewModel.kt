package io.github.evative7.parley.state

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.evative7.parley.asr.RecognitionSession
import io.github.evative7.parley.asr.SpeechRecognizer
import io.github.evative7.parley.audio.AudioRecorder
import io.github.evative7.parley.audio.VolumeMeter
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.translate.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val recorder: AudioRecorder,
    private val recognizer: SpeechRecognizer,
    private val translator: Translator,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // High-frequency values live outside UiState so only the button recomposes.
    private val _volume = MutableStateFlow(0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val meter = VolumeMeter()
    private var session: RecognitionSession? = null
    private var recordJob: Job? = null
    private var pipelineJob: Job? = null
    private var hintJob: Job? = null
    private var activeId = -1L
    private var nextId = 0L
    private var pressedAt = 0L

    fun onPress() = dispatch(PttEvent.Press)
    fun onDrag(armed: Boolean) = dispatch(PttEvent.Drag(armed))
    fun onRelease() = dispatch(PttEvent.Release(SystemClock.elapsedRealtime() - pressedAt))
    fun onAbort() = dispatch(PttEvent.Abort)
    fun onRetry() = dispatch(PttEvent.Retry)

    fun setTarget(lang: Lang) = _state.update { it.copy(target = lang) }

    fun toggleFavorite(id: Long) = updateCard(id) { copy(favorite = !favorite) }

    fun redo(id: Long) {
        _state.update { s -> s.copy(cards = s.cards.filterNot { it.id == id }) }
        showHint(Hint.Redo)
    }

    fun showHint(hint: Hint) {
        hintJob?.cancel()
        _state.update { it.copy(hint = hint) }
        hintJob = viewModelScope.launch {
            delay(2_000)
            _state.update { it.copy(hint = null) }
        }
    }

    private fun dispatch(event: PttEvent) {
        val t = PttStateMachine.reduce(_state.value.phase, event)
        _state.update { it.copy(phase = t.phase) }
        t.effects.forEach(::perform)
    }

    private fun perform(effect: PttEffect) {
        when (effect) {
            PttEffect.StartRecording -> startRecording()
            PttEffect.Submit -> submit()
            PttEffect.Resubmit -> _state.value.cards.firstOrNull { it.id == activeId }
                ?.takeIf { it.source.isNotBlank() }
                ?.let { card ->
                    updateCard(card.id) { copy(stage = CardStage.Translating, translation = "", error = null) }
                    pipelineJob = viewModelScope.launch { translate(card.id, card.source, card.sourceLang) }
                }
            is PttEffect.Discard -> discard(effect.reason)
            PttEffect.InterruptTranslation -> {
                pipelineJob?.cancel()
                updateCard(activeId) { copy(stage = CardStage.Done) }
            }
        }
    }

    private fun startRecording() {
        pressedAt = SystemClock.elapsedRealtime()
        meter.reset()
        _elapsedMs.value = 0
        val id = nextId++
        activeId = id
        _state.update { s ->
            s.copy(hint = null, cards = listOf(CardState(id, s.target, CardStage.Listening)) + s.cards)
        }
        val s = recognizer.open(lang = null)
        session = s
        recordJob = viewModelScope.launch {
            launch { s.partials.collect { text -> updateCard(id) { copy(source = text) } } }
            launch {
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - pressedAt
                    _elapsedMs.value = elapsed
                    if (elapsed >= PttStateMachine.MAX_RECORD_MS) dispatch(PttEvent.TimeLimit)
                    delay(50)
                }
            }
            try {
                recorder.frames().collect { pcm ->
                    _volume.value = meter.process(pcm)
                    s.feed(pcm)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, ErrorKind.Microphone)
                recordJob?.cancel()
            }
        }
    }

    private fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
        _volume.value = 0f
    }

    private fun submit() {
        val s = session ?: return
        val id = activeId
        stopRecording()
        updateCard(id) { copy(stage = CardStage.Recognizing) }
        pipelineJob = viewModelScope.launch {
            val transcript = try {
                withTimeout(TIMEOUT_MS) { s.finish() }
            } catch (e: TimeoutCancellationException) {
                fail(id, ErrorKind.Timeout); return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, ErrorKind.Unknown); return@launch
            } finally {
                session = null
            }
            if (transcript.text.isBlank()) {
                fail(id, ErrorKind.NoSpeech); return@launch
            }
            updateCard(id) {
                copy(source = transcript.text, sourceLang = transcript.lang, stage = CardStage.Translating)
            }
            dispatch(PttEvent.Recognized)
            translate(id, transcript.text, transcript.lang)
        }
    }

    private suspend fun translate(id: Long, text: String, from: Lang?) {
        val to = _state.value.cards.first { it.id == id }.target
        try {
            withTimeout(TIMEOUT_MS) {
                translator.translate(text, from, to).collect { fragment ->
                    updateCard(id) { copy(translation = translation + fragment) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            fail(id, ErrorKind.Timeout); return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(id, ErrorKind.Unknown); return
        }
        updateCard(id) { copy(stage = CardStage.Done) }
        dispatch(PttEvent.Translated)
    }

    private fun discard(reason: PttEffect.Discard.Reason) {
        stopRecording()
        session?.cancel()
        session = null
        _state.update { s -> s.copy(cards = s.cards.filterNot { it.id == activeId }) }
        if (reason == PttEffect.Discard.Reason.TooShort) showHint(Hint.HoldToTalk)
    }

    private fun fail(id: Long, kind: ErrorKind) {
        updateCard(id) { copy(stage = CardStage.Error, error = kind) }
        dispatch(PttEvent.Failed)
    }

    private inline fun updateCard(id: Long, crossinline f: CardState.() -> CardState) =
        _state.update { s -> s.copy(cards = s.cards.map { if (it.id == id) it.f() else it }) }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
