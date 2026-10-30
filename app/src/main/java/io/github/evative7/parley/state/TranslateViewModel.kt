package io.github.evative7.parley.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.evative7.parley.asr.RecognitionSession
import io.github.evative7.parley.asr.SpeechRecognizer
import io.github.evative7.parley.audio.AudioSource
import io.github.evative7.parley.audio.VolumeMeter
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.model.LanguageDetector
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.translate.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val recorder: AudioSource,
    private val recognizer: SpeechRecognizer,
    private val translator: Translator,
    private val detector: LanguageDetector,
    private val clock: MonotonicClock,
) : ViewModel() {
    init {
        // The bundled language model loads on first use; do that now rather than after the first sentence.
        viewModelScope.launch { runCatching { detector.detect("warm up") } }
    }

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
    fun onRelease() = dispatch(PttEvent.Release(clock.nowMs() - pressedAt))
    fun onAbort() = dispatch(PttEvent.Abort)

    /** The screen is visible: connect ahead of the next press so the handshake is not after it. */
    fun onForeground() {
        recognizer.prepare()
        translator.prepare()
    }

    /** Going to the background (or an incoming call) ends the recording without sending it. */
    fun onBackground() {
        onAbort()
        recognizer.release()
    }

    fun onRetry(id: Long) {
        if (_state.value.phase in BUSY_PHASES) return
        val card = _state.value.cards.firstOrNull {
            it.id == id && it.stage == CardStage.Error && it.canRetry
        } ?: return
        activeId = card.id
        dispatch(PttEvent.Retry)
    }

    fun setLang(slot: Int, lang: Lang) = _state.update { it.copy(langs = it.langs.with(slot, lang)) }
    fun swapLangs() = _state.update { it.copy(langs = it.langs.swapped()) }

    fun delete(id: Long) {
        if (id == activeId && _state.value.phase in BUSY_PHASES) return
        if (id == activeId) activeId = -1
        _state.update { s -> s.copy(cards = s.cards.filterNot { it.id == id }) }
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
        val transition = PttStateMachine.reduce(_state.value.phase, event)
        _state.update { it.copy(phase = transition.phase) }
        transition.effects.forEach(::perform)
    }

    private fun perform(effect: PttEffect) {
        when (effect) {
            PttEffect.StartRecording -> startRecording()
            PttEffect.Submit -> submit()
            PttEffect.Resubmit -> _state.value.cards.firstOrNull { it.id == activeId }
                ?.takeIf { it.canRetry }
                ?.let { card ->
                    updateCard(card.id) { copy(stage = CardStage.Translating, translation = "", error = null) }
                    pipelineJob = viewModelScope.launch { translate(card.id, card.source, card.sourceLang) }
                }
            is PttEffect.Discard -> discard()
            PttEffect.InterruptTranslation -> {
                pipelineJob?.cancel()
                updateCard(activeId) { copy(stage = CardStage.Done, canRetry = false) }
            }
        }
    }

    private fun startRecording() {
        pressedAt = clock.nowMs()
        meter.reset()
        _elapsedMs.value = 0
        val id = nextId++
        activeId = id
        _state.update { s ->
            s.copy(hint = null, cards = listOf(CardState(id, s.langs, CardStage.Listening)) + s.cards)
        }
        val currentSession = try {
            recognizer.open(lang = null)
        } catch (e: Exception) {
            fail(id, e.errorKind())
            return
        }
        session = currentSession
        recordJob = viewModelScope.launch {
            launch {
                currentSession.partials.collect { text -> updateCard(id) { copy(source = text) } }
            }
            launch {
                while (true) {
                    val elapsed = clock.nowMs() - pressedAt
                    _elapsedMs.value = elapsed
                    if (elapsed >= PttStateMachine.MAX_RECORD_MS) {
                        dispatch(PttEvent.TimeLimit)
                        return@launch
                    }
                    delay(50)
                }
            }
            try {
                recorder.frames().catch { throw MicrophoneFailure() }.collect { pcm ->
                    _volume.value = meter.process(pcm)
                    currentSession.feed(pcm)
                }
                // A microphone stream ending before release is also a recording failure.
                fail(id, ErrorKind.Microphone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, e.errorKind())
            }
        }
    }

    private fun stopRecording(): Job? {
        val job = recordJob
        recordJob = null
        job?.cancel()
        _volume.value = 0f
        return job
    }

    private fun submit() {
        val currentSession = session ?: return
        val id = activeId
        val stopped = stopRecording()
        updateCard(id) { copy(stage = CardStage.Recognizing) }
        pipelineJob = viewModelScope.launch {
            val transcript = try {
                withTimeout(TIMEOUT_MS) {
                    // Wait for the last feed and AudioRecord release before committing the session.
                    stopped?.join()
                    currentSession.finish()
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) { fail(id, ErrorKind.Timeout); return@launch }
                throw e
            } catch (e: Exception) {
                fail(id, e.errorKind())
                return@launch
            } finally {
                currentSession.cancel()
                if (session === currentSession) session = null
                recognizer.prepare()
            }
            if (transcript.text.isBlank()) {
                fail(id, ErrorKind.NoSpeech)
                return@launch
            }
            val sourceLang = transcript.lang ?: withTimeoutOrNull(1_500) {
                try {
                    detector.detect(transcript.text)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // Short or ambiguous text may not have a reliable language ID.
                }
            }
            updateCard(id) {
                copy(
                    source = transcript.text,
                    sourceLang = sourceLang,
                    target = langs.targetFor(sourceLang),
                    stage = CardStage.Translating,
                    canRetry = true,
                )
            }
            dispatch(PttEvent.Recognized)
            translate(id, transcript.text, sourceLang)
        }
    }

    private suspend fun translate(id: Long, text: String, from: Lang?) {
        val card = _state.value.cards.firstOrNull { it.id == id } ?: return
        val to = card.target ?: card.langs.targetFor(from)
        try {
            withTimeout(TIMEOUT_MS) {
                translator.translate(text, from, to).collect { fragment ->
                    updateCard(id) { copy(translation = translation + fragment) }
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) { fail(id, ErrorKind.Timeout); return }
            throw e
        } catch (e: Exception) {
            fail(id, e.errorKind())
            return
        }
        updateCard(id) { copy(stage = CardStage.Done, canRetry = false) }
        if (id == activeId) dispatch(PttEvent.Translated)
    }

    private fun discard() {
        stopRecording()
        session?.cancel()
        session = null
        recognizer.prepare()
        _state.update { s -> s.copy(cards = s.cards.filterNot { it.id == activeId }) }
    }

    private fun fail(id: Long, kind: ErrorKind) {
        if (id != activeId) return
        stopRecording()
        session?.cancel()
        session = null
        updateCard(id) { copy(stage = CardStage.Error, error = kind) }
        dispatch(PttEvent.Failed)
    }

    override fun onCleared() {
        stopRecording()
        session?.cancel()
        session = null
        recognizer.release()
    }

    private inline fun updateCard(id: Long, crossinline f: CardState.() -> CardState) =
        _state.update { s -> s.copy(cards = s.cards.map { if (it.id == id) it.f() else it }) }

    private fun Exception.errorKind() = when (this) {
        is MicrophoneFailure -> ErrorKind.Microphone
        is ServiceException -> when (failure) {
            ServiceFailure.Configuration -> ErrorKind.Configuration
            ServiceFailure.Authentication -> ErrorKind.Authentication
            ServiceFailure.Permission -> ErrorKind.Permission
            ServiceFailure.RateLimit -> ErrorKind.RateLimit
            ServiceFailure.Timeout -> ErrorKind.Timeout
            ServiceFailure.Network -> ErrorKind.Network
            ServiceFailure.Response -> ErrorKind.Service
        }
        else -> ErrorKind.Unknown
    }

    private class MicrophoneFailure : Exception()

    private companion object {
        const val TIMEOUT_MS = 10_000L
        val BUSY_PHASES = setOf(PttPhase.Recording, PttPhase.CancelArmed, PttPhase.Recognizing, PttPhase.Translating)
    }
}
