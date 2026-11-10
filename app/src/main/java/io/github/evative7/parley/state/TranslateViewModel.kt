package io.github.evative7.parley.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.evative7.parley.asr.RecognitionSession
import io.github.evative7.parley.asr.SpeechRecognizer
import io.github.evative7.parley.asr.Transcript
import io.github.evative7.parley.audio.AudioSource
import io.github.evative7.parley.audio.VolumeMeter
import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.model.LanguageDetector
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.translate.Translator
import io.github.evative7.parley.tts.PlaybackState
import io.github.evative7.parley.tts.SpeechCache
import io.github.evative7.parley.tts.SpeechKey
import io.github.evative7.parley.tts.TextToSpeaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val recorder: AudioSource,
    private val recognizer: SpeechRecognizer,
    private val translator: Translator,
    private val detector: LanguageDetector,
    private val clock: MonotonicClock,
    private val langStore: LangPairStore,
    private val conversationStore: ConversationStore,
    private val speaker: TextToSpeaker,
    private val speechCache: SpeechCache,
    private val settings: ServiceSettingsSource,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Set once the user picks a language, so a slow load can't overwrite the pick. */
    private var langsPicked = false

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
    private var speechJob: Job? = null
    private var activeSpeech: Pair<Long, Lang>? = null
    /** Speech being synthesized ahead of a tap; a tap on it waits rather than synthesizing twice. */
    private val generating = mutableMapOf<SpeechKey, Job>()
    private var foreground = false
    private var faceToFace = false
    private var activeId = -1L
    private var nextId = 0L
    private var pressedAt = 0L
    // Debug-mode timings for the current press.
    private var releasedAt = 0L
    private var frameCount = 0
    private val conversationSaveMutex = Mutex()

    init {
        // The bundled language model loads on first use; do that now rather than after the first sentence.
        viewModelScope.launch { runCatching { detector.detect("warm up") } }
        viewModelScope.launch {
            val saved = langStore.load() ?: return@launch
            if (!langsPicked) _state.update { it.copy(langs = saved) }
        }
        viewModelScope.launch {
            val restored = conversationStore.load().orEmpty().map { it.copy(id = nextId++) }
            if (restored.isNotEmpty()) {
                // Cards made while disk/keystore I/O was in progress are newer, so they stay first.
                _state.update { it.copy(cards = it.cards + restored) }
            }
            state.map { value -> value.cards.filter { it.isPersistable() } }
                .distinctUntilChanged()
                .drop(1)
                .debounce(500)
                .collect { cards -> conversationSaveMutex.withLock { conversationStore.save(cards) } }
        }
    }

    fun onPress() {
        stopSpeech()
        dispatch(PttEvent.Press)
    }
    fun onDrag(armed: Boolean) = dispatch(PttEvent.Drag(armed))
    fun onRelease() = dispatch(PttEvent.Release(clock.nowMs() - pressedAt))
    fun onAbort() = dispatch(PttEvent.Abort)

    /** The screen is visible: connect ahead of the next press so the handshake is not after it. */
    fun onForeground() {
        foreground = true
        DebugLog.log(TAG) { "foreground: prepare ASR and translation connections" }
        recognizer.prepare()
        translator.prepare()
    }

    /** Going to the background (or an incoming call) ends the recording without sending it. */
    fun onBackground() {
        foreground = false
        stopSpeech()
        DebugLog.log(TAG) { "background: abort and release ASR" }
        onAbort()
        recognizer.release()
    }

    fun setFaceToFace(enabled: Boolean) {
        faceToFace = enabled
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

    /** A user tap starts or interrupts one settled translation. */
    fun toggleSpeech(id: Long, lang: Lang) {
        val card = _state.value.cards.firstOrNull { it.id == id && it.stage == CardStage.Done } ?: return
        val translation = card.translations.firstOrNull { it.to == lang && it.text.isNotBlank() } ?: return
        if (activeSpeech == (id to lang) && speechJob?.isActive == true) {
            stopSpeech(id)
            return
        }
        val saved = settings.current ?: return
        val choice = saved.speechChoice(id, lang, translation.text)
        if (choice == null) {
            updateCard(id) {
                copy(translations = translations.map {
                    if (it.to == lang && it.text == translation.text) {
                        it.copy(playback = PlaybackState.Failed(noVoice = true))
                    } else it
                })
            }
            return
        }
        playSpeech(choice.key, translation)
    }

    private fun playSpeech(key: SpeechKey, translation: CardTranslation) {
        stopSpeech()
        activeSpeech = key.cardId to key.lang
        speechJob = viewModelScope.launch {
            generating[key]?.let { pending ->
                setPlayback(key, PlaybackState.Preparing)
                pending.join()
            }
            speaker.speak(key, translation.text, translation.to).collect { playback ->
                setPlayback(key, playback)
                if (playback == PlaybackState.Done || playback is PlaybackState.Failed) {
                    activeSpeech = null
                }
            }
        }
    }

    private fun setPlayback(key: SpeechKey, playback: PlaybackState) = updateCard(key.cardId) {
        copy(translations = translations.map {
            if (it.to == key.lang && it.text == key.text) it.copy(playback = playback) else it
        })
    }

    private fun stopSpeech(cardId: Long? = null) {
        val active = activeSpeech ?: return
        if (cardId != null && active.first != cardId) return
        speechJob?.cancel()
        speechJob = null
        speaker.stop()
        activeSpeech = null
        updateCard(active.first) {
            copy(translations = translations.map {
                if (it.to == active.second) it.copy(playback = null) else it
            })
        }
    }

    /** With auto-generation on, plays a settled card's translation when the layout's switch allows, else readies it. */
    private fun autoGenerate(card: CardState) {
        val saved = settings.current ?: return
        if (!saved.ttsAutoGenerate || card.stage != CardStage.Done) return
        autoPlayTranslation(card, saved, foreground, faceToFace)?.let { translation ->
            saved.speechChoice(card.id, translation.to, translation.text)?.let { playSpeech(it.key, translation) }
            return
        }
        for (translation in card.translations) {
            if (translation.text.isBlank()) continue
            val key = saved.speechChoice(card.id, translation.to, translation.text)?.key ?: continue
            if (key in generating) continue
            val job = viewModelScope.launch { speaker.prepare(key, translation.text, translation.to) }
            generating[key] = job
            job.invokeOnCompletion { if (generating[key] === job) generating.remove(key) }
        }
    }

    private fun cancelGenerating(cardId: Long? = null) {
        generating.filterKeys { cardId == null || it.cardId == cardId }.forEach { (key, job) ->
            job.cancel()
            generating.remove(key)
        }
    }

    /**
     * Replaces a settled card's recognized text with [text] and translates it again, as a retry would.
     * The language is detected anew: the edit may have changed it. The edit is what was said, so the other
     * services' transcripts no longer matter.
     */
    fun editSource(id: Long, text: String) {
        val edited = text.trim()
        if (edited.isEmpty() || _state.value.phase in BUSY_PHASES) return
        val card = _state.value.cards.firstOrNull { it.id == id } ?: return
        if (card.stage != CardStage.Done && card.stage != CardStage.Error) return
        stopSpeech(id)
        cancelGenerating(id)
        speechCache.removeCard(id)
        DebugLog.log(TAG) { "#$id source edited (${edited.length} chars)" }
        viewModelScope.launch {
            val sourceLang = detect(edited) ?: card.sourceLang
            // A new recording may have started while detecting; it owns the pipeline now.
            if (_state.value.phase in BUSY_PHASES) return@launch
            if (_state.value.cards.none { it.id == id }) return@launch
            retranslate(id, edited, sourceLang)
        }
    }

    /** Adds a card for typed [text] and translates it, as if it had been heard. */
    fun submitText(text: String) {
        val typed = text.trim()
        if (typed.isEmpty() || _state.value.phase in BUSY_PHASES) return
        val id = nextId++
        DebugLog.log(TAG) { "#$id typed (${typed.length} chars)" }
        _state.update { s ->
            s.copy(hint = null, cards = listOf(CardState(id, s.langs, CardStage.Recognizing, source = typed)) + s.cards)
        }
        viewModelScope.launch {
            val sourceLang = detect(typed)
            if (_state.value.cards.none { it.id == id }) return@launch
            if (_state.value.phase in BUSY_PHASES) {
                // A recording started while detecting and owns the pipeline; the text waits for a retry.
                updateCard(id) {
                    copy(
                        sourceLang = sourceLang,
                        translations = langs.targetsFor(sourceLang).map(::CardTranslation),
                        stage = CardStage.Error,
                        error = ErrorKind.Unknown,
                        canRetry = true,
                    )
                }
                return@launch
            }
            retranslate(id, typed, sourceLang)
        }
    }

    /** Sets card [id]'s text and language and translates it as a retry would. Call when not busy. */
    private fun retranslate(id: Long, text: String, sourceLang: Lang?) {
        updateCard(id) {
            copy(
                source = text,
                sourceLang = sourceLang,
                sourceService = null,
                alternatives = emptyList(),
                translations = langs.targetsFor(sourceLang).map(::CardTranslation),
                stage = CardStage.Error,
                canRetry = true,
            )
        }
        activeId = id
        dispatch(PttEvent.Retry)
    }

    /** The language of [text], or null when it can't be told quickly. */
    private suspend fun detect(text: String): Lang? = withTimeoutOrNull(1_500) {
        try {
            detector.detect(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // Short or ambiguous text may not have a reliable language ID.
        }
    }

    fun delete(id: Long) {
        if (id == activeId && _state.value.phase in BUSY_PHASES) return
        stopSpeech(id)
        cancelGenerating(id)
        speechCache.removeCard(id)
        if (id == activeId) activeId = -1
        _state.update { s -> s.copy(cards = s.cards.filterNot { it.id == id }) }
    }

    fun clearConversation() {
        if (_state.value.phase in BUSY_PHASES) return
        stopSpeech()
        cancelGenerating()
        speechCache.clear()
        activeId = -1
        _state.update { it.copy(cards = emptyList()) }
        viewModelScope.launch {
            conversationSaveMutex.withLock { conversationStore.save(emptyList()) }
        }
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
                    updateCard(card.id) {
                        copy(stage = CardStage.Translating, translations = translations.map { it.copy(text = "") }, error = null)
                    }
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
            var finished = false
            val transcript = try {
                withTimeout(TIMEOUT_MS) {
                    // Wait for the last feed and AudioRecord release before committing the session.
                    stopped?.join()
                    currentSession.finish()
                }.also { finished = true }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) { fail(id, ErrorKind.Timeout, e); return@launch }
                throw e
            } catch (e: Exception) {
                fail(id, e.errorKind(), e)
                return@launch
            } finally {
                // A finished session may still be hearing from slower services; collecting them ends it.
                if (!finished) currentSession.cancel()
                if (session === currentSession) session = null
                recognizer.prepare()
            }
            collectLate(id, transcript, currentSession)
            DebugLog.log(TAG) {
                "#$id final transcript +${sinceRelease()} ms after release (${transcript.text.length} chars)"
            }
            if (transcript.text.isBlank()) {
                fail(id, ErrorKind.NoSpeech)
                return@launch
            }
            val detectStart = clock.nowMs()
            val sourceLang = transcript.lang ?: detect(transcript.text)
            DebugLog.log(TAG) {
                if (transcript.lang != null) "#$id language ${transcript.lang.tag} from ASR"
                else "#$id language detected: ${sourceLang?.tag ?: "unknown"} in ${clock.nowMs() - detectStart} ms"
            }
            updateCard(id) {
                copy(
                    source = transcript.text,
                    sourceLang = sourceLang,
                    sourceService = transcript.service,
                    alternatives = transcript.alternatives,
                    translations = langs.targetsFor(sourceLang).map(::CardTranslation),
                    stage = CardStage.Translating,
                    canRetry = true,
                )
            }
            dispatch(PttEvent.Recognized)
            translate(id, transcript.text, sourceLang)
        }
    }

    /**
     * Adds what slower services hear to card [id], for reading; the translation has started without them. Not
     * after the text was edited: they would be alternatives to words nobody said. Ends [session] when done.
     */
    private fun collectLate(id: Long, transcript: Transcript, session: RecognitionSession) {
        viewModelScope.launch {
            try {
                transcript.late.collect { alternative ->
                    updateCard(id) {
                        if (source == transcript.text) copy(alternatives = alternatives + alternative) else this
                    }
                }
            } finally {
                session.cancel()
            }
        }
    }

    /** Translates into each of the card's targets at once; the first failure stops them all. */
    private suspend fun translate(id: Long, text: String, from: Lang?) {
        val card = _state.value.cards.firstOrNull { it.id == id } ?: return
        val targets = card.translations.map { it.to }.ifEmpty { card.langs.targetsFor(from) }
        // The same words twice tell the translator nothing more.
        val references = card.alternatives.map { it.text }.filter { it != text }.distinct()
        val start = clock.nowMs()
        var chunks = 0
        var chars = 0
        DebugLog.log(TAG) {
            "#$id translate ${from?.tag ?: "auto"} → ${targets.joinToString { it.tag }} (${text.length} chars, " +
                "${references.size} reference(s))"
        }
        try {
            withTimeout(TIMEOUT_MS) {
                coroutineScope {
                    for (to in targets) launch {
                        translator.translate(text, from, to, references).collect { fragment ->
                            if (chunks++ == 0) DebugLog.log(TAG) { "#$id first translated text +${clock.nowMs() - start} ms" }
                            chars += fragment.length
                            updateCard(id) {
                                copy(translations = translations.map { if (it.to == to) it.copy(text = it.text + fragment) else it })
                            }
                        }
                    }
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
        _state.value.cards.firstOrNull { it.id == id }?.let(::autoGenerate)
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
        stopSpeech()
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

    private fun CardState.isPersistable(): Boolean = when (stage) {
        CardStage.Done -> true
        CardStage.Error -> source.isNotEmpty() || translations.isNotEmpty()
        else -> false
    }

    private companion object {
        const val TAG = "Flow"
        const val TIMEOUT_MS = 10_000L
        val BUSY_PHASES = setOf(PttPhase.Recording, PttPhase.CancelArmed, PttPhase.Recognizing, PttPhase.Translating)
    }
}

/** The one translation eligible for automatic playback, or null when playback must remain tap-only. */
internal fun autoPlayTranslation(
    card: CardState,
    settings: ServiceSettings,
    foreground: Boolean,
    faceToFace: Boolean,
): CardTranslation? = card.translations.singleOrNull()?.takeIf {
    card.stage == CardStage.Done &&
        it.text.isNotBlank() &&
        settings.ttsService != null &&
        settings.ttsAutoGenerate &&
        foreground &&
        (if (faceToFace) settings.ttsAutoPlayFaceToFace else settings.ttsAutoPlayPortrait)
}
