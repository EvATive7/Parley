package io.github.evative7.parley.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.evative7.parley.asr.RecognitionSession
import io.github.evative7.parley.asr.SpeechRecognizer
import io.github.evative7.parley.audio.AudioSource
import io.github.evative7.parley.audio.VolumeMeter
import io.github.evative7.parley.debug.DebugLog
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
    private val langStore: LangPairStore,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Set once the user picks a language, so a slow load can't overwrite the pick. */
    private var langsPicked = false

    init {
        // The bundled language model loads on first use; do that now rather than after the first sentence.
        viewModelScope.launch { runCatching { detector.detect("warm up") } }
        viewModelScope.launch {
            val saved = langStore.load() ?: return@launch
            if (!langsPicked) _state.update { it.copy(langs = saved) }
        }
    }

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
    // Debug-mode timings for the current press.
    private var releasedAt = 0L
    private var frameCount = 0

    fun onPress() = dispatch(PttEvent.Press)
    fun onDrag(armed: Boolean) = dispatch(PttEvent.Drag(armed))
    fun onRelease() = dispatch(PttEvent.Release(clock.nowMs() - pressedAt))
    fun onAbort() = dispatch(PttEvent.Abort)

    /** The screen is visible: connect ahead of the next press so the handshake is not after it. */
    fun onForeground() {
        DebugLog.log(TAG) { "foreground: prepare ASR and translation connections" }
        recognizer.prepare()
        translator.prepare()
    }

    /** Going to the background (or an incoming call) ends the recording without sending it. */
    fun onBackground() {
        DebugLog.log(TAG) { "background: abort and release ASR" }
        onAbort()
        recognizer.release()
    }

    fun onRetry(id: Long) {
        if (_state.value.phase in BUSY_PHASES) return
        val card = _state.value.cards.firstOrNull {
            it.id == id && it.stage == CardStage.Error && it.canRetry
        } ?: return
        activeId = card.id
        DebugLog.log(TAG) { "#${card.id} retry translation" }
        dispatch(PttEvent.Retry)
    }

    fun setLang(slot: Int, lang: Lang) = pickLangs { it.with(slot, lang) }
    fun swapLangs() = pickLangs { it.swapped() }

    private fun pickLangs(f: (LangPair) -> LangPair) {
        langsPicked = true
        val langs = f(_state.value.langs)
        _state.update { it.copy(langs = langs) }
        viewModelScope.launch { langStore.save(langs) }
    }

    /**
     * Replaces a settled card's recognized text with [text] and translates it again, as a retry would.
     * The language is detected anew: the edit may have changed it.
     */
    fun editSource(id: Long, text: String) {
        val edited = text.trim()
        if (edited.isEmpty() || _state.value.phase in BUSY_PHASES) return
        val card = _state.value.cards.firstOrNull { it.id == id } ?: return
        if (card.stage != CardStage.Done && card.stage != CardStage.Error) return
        DebugLog.log(TAG) { "#$id source edited (${edited.length} chars)" }
        viewModelScope.launch {
            val sourceLang = withTimeoutOrNull(1_500) {
                try {
                    detector.detect(edited)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } ?: card.sourceLang
            // A new recording may have started while detecting; it owns the pipeline now.
            if (_state.value.phase in BUSY_PHASES) return@launch
            if (_state.value.cards.none { it.id == id }) return@launch
            updateCard(id) {
                copy(
                    source = edited,
                    sourceLang = sourceLang,
                    target = langs.targetFor(sourceLang),
                    stage = CardStage.Error,
                    canRetry = true,
                )
            }
            activeId = id
            dispatch(PttEvent.Retry)
        }
    }

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
        val from = _state.value.phase
        val transition = PttStateMachine.reduce(from, event)
        _state.update { it.copy(phase = transition.phase) }
        if (transition.phase != from || transition.effects.isNotEmpty()) {
            DebugLog.log("State") { "$from → ${transition.phase} on $event" }
        }
        transition.effects.forEach(::perform)
    }

    private fun perform(effect: PttEffect) {
        when (effect) {
            PttEffect.StartRecording -> startRecording()
            PttEffect.Submit -> submit()
            PttEffect.Resubmit -> _state.value.cards.firstOrNull { it.id == activeId }
                ?.takeIf { it.canRetry }
                ?.let { card ->
                    releasedAt = clock.nowMs()
                    updateCard(card.id) { copy(stage = CardStage.Translating, translation = "", error = null) }
                    pipelineJob = viewModelScope.launch { translate(card.id, card.source, card.sourceLang) }
                }
            is PttEffect.Discard -> discard()
            PttEffect.InterruptTranslation -> {
                DebugLog.log(TAG) { "#$activeId translation interrupted by a new press" }
                pipelineJob?.cancel()
                updateCard(activeId) { copy(stage = CardStage.Done, canRetry = false) }
            }
        }
    }

    private fun startRecording() {
        pressedAt = clock.nowMs()
        frameCount = 0
        meter.reset()
        _elapsedMs.value = 0
        val id = nextId++
        activeId = id
        _state.update { s ->
            s.copy(hint = null, cards = listOf(CardState(id, s.langs, CardStage.Listening)) + s.cards)
        }
        DebugLog.log(TAG) { "#$id press: open ASR session (streaming=${recognizer.isStreaming})" }
        val currentSession = try {
            recognizer.open(lang = null)
        } catch (e: Exception) {
            fail(id, e.errorKind(), e)
            return
        }
        DebugLog.log(TAG) { "#$id ASR session opened +${sincePress()} ms" }
        session = currentSession
        recordJob = viewModelScope.launch {
            launch {
                var first = true
                currentSession.partials.collect { text ->
                    if (first && text.isNotEmpty()) {
                        first = false
                        DebugLog.log(TAG) { "#$id first partial +${sincePress()} ms (${text.length} chars)" }
                    }
                    updateCard(id) { copy(source = text) }
                }
            }
            launch {
                while (true) {
                    val elapsed = clock.nowMs() - pressedAt
                    _elapsedMs.value = elapsed
                    if (elapsed >= PttStateMachine.MAX_RECORD_MS) {
                        DebugLog.log(TAG) { "#$id recording time limit reached" }
                        dispatch(PttEvent.TimeLimit)
                        return@launch
                    }
                    delay(50)
                }
            }
            try {
                recorder.frames().catch { throw MicrophoneFailure(it) }.collect { pcm ->
                    if (frameCount++ == 0) DebugLog.log(TAG) { "#$id first mic frame +${sincePress()} ms" }
                    _volume.value = meter.process(pcm)
                    currentSession.feed(pcm)
                }
                // A microphone stream ending before release is also a recording failure.
                fail(id, ErrorKind.Microphone, IllegalStateException("microphone stream ended"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, e.errorKind(), e)
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
        releasedAt = clock.nowMs()
        DebugLog.log(TAG) {
            "#$id release: recorded ${releasedAt - pressedAt} ms, $frameCount frames; waiting for final transcript"
        }
        updateCard(id) { copy(stage = CardStage.Recognizing) }
        pipelineJob = viewModelScope.launch {
            val transcript = try {
                withTimeout(TIMEOUT_MS) {
                    // Wait for the last feed and AudioRecord release before committing the session.
                    stopped?.join()
                    currentSession.finish()
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) { fail(id, ErrorKind.Timeout, e); return@launch }
                throw e
            } catch (e: Exception) {
                fail(id, e.errorKind(), e)
                return@launch
            } finally {
                currentSession.cancel()
                if (session === currentSession) session = null
                recognizer.prepare()
            }
            DebugLog.log(TAG) {
                "#$id final transcript +${sinceRelease()} ms after release (${transcript.text.length} chars)"
            }
            if (transcript.text.isBlank()) {
                fail(id, ErrorKind.NoSpeech)
                return@launch
            }
            val detectStart = clock.nowMs()
            val sourceLang = transcript.lang ?: withTimeoutOrNull(1_500) {
                try {
                    detector.detect(transcript.text)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // Short or ambiguous text may not have a reliable language ID.
                }
            }
            DebugLog.log(TAG) {
                if (transcript.lang != null) "#$id language ${transcript.lang.tag} from ASR"
                else "#$id language detected: ${sourceLang?.tag ?: "unknown"} in ${clock.nowMs() - detectStart} ms"
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
        val start = clock.nowMs()
        var chunks = 0
        var chars = 0
        DebugLog.log(TAG) { "#$id translate ${from?.tag ?: "auto"} → ${to.tag} (${text.length} chars)" }
        try {
            withTimeout(TIMEOUT_MS) {
                translator.translate(text, from, to).collect { fragment ->
                    if (chunks++ == 0) DebugLog.log(TAG) { "#$id first translated text +${clock.nowMs() - start} ms" }
                    chars += fragment.length
                    updateCard(id) { copy(translation = translation + fragment) }
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) { fail(id, ErrorKind.Timeout, e); return }
            throw e
        } catch (e: Exception) {
            fail(id, e.errorKind(), e)
            return
        }
        DebugLog.log(TAG) {
            "#$id translation done in ${clock.nowMs() - start} ms ($chars chars, $chunks chunks); " +
                "release → done ${sinceRelease()} ms"
        }
        updateCard(id) { copy(stage = CardStage.Done, canRetry = false) }
        if (id == activeId) dispatch(PttEvent.Translated)
    }

    private fun discard() {
        DebugLog.log(TAG) { "#$activeId discarded after ${sincePress()} ms" }
        stopRecording()
        session?.cancel()
        session = null
        recognizer.prepare()
        _state.update { s -> s.copy(cards = s.cards.filterNot { it.id == activeId }) }
    }

    private fun fail(id: Long, kind: ErrorKind, cause: Throwable? = null) {
        if (id != activeId) return
        DebugLog.log(TAG) {
            "#$id failed: $kind" + (cause?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: "")
        }
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

    private class MicrophoneFailure(cause: Throwable) : Exception(cause.message, cause)

    private fun sincePress() = clock.nowMs() - pressedAt
    private fun sinceRelease() = clock.nowMs() - releasedAt

    private companion object {
        const val TAG = "Flow"
        const val TIMEOUT_MS = 10_000L
        val BUSY_PHASES = setOf(PttPhase.Recording, PttPhase.CancelArmed, PttPhase.Recognizing, PttPhase.Translating)
    }
}
