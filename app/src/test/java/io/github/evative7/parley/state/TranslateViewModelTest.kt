package io.github.evative7.parley.state

import androidx.lifecycle.ViewModelStore
import io.github.evative7.parley.asr.RecognitionSession
import io.github.evative7.parley.asr.SpeechRecognizer
import io.github.evative7.parley.asr.Transcript
import io.github.evative7.parley.audio.AudioSource
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.model.LanguageDetector
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import io.github.evative7.parley.translate.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val frames = Channel<ShortArray>(Channel.UNLIMITED)
    private var now = 0L
    private var recorderStopped = false
    private var recorderOpened = false
    private val sessions = mutableListOf<Session>()
    private var openError: ServiceException? = null
    private var nextTranscript = Transcript("你好", Lang("zh"))
    private var finishBlock: (suspend () -> Transcript)? = null
    private var feedError: ServiceException? = null
    private var translation: (String) -> Flow<String> = { flowOf("Hello") }
    private val translatedSources = mutableListOf<String>()
    private val targets = mutableListOf<Lang>()
    private var detected: Lang? = Lang("en")
    private var microphoneFails = false

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        store.clear()
        frames.close()
        Dispatchers.resetMain()
    }

    private inner class Session : RecognitionSession {
        override val partials = MutableStateFlow("")
        val transcript = nextTranscript
        var cancelled = false
        var finished = false
        override fun feed(pcm: ShortArray) { feedError?.let { throw it } }
        override suspend fun finish(): Transcript {
            finished = true
            assertTrue("Microphone must be released before finish", recorderStopped)
            return finishBlock?.invoke() ?: transcript
        }
        override fun cancel() { cancelled = true }
    }

    private fun vm(): TranslateViewModel {
        val recorder = AudioSource {
            recorderOpened = true
            if (microphoneFails) flow { throw SecurityException("permission") } else
                frames.receiveAsFlow().onCompletion { recorderStopped = true }
        }
        val recognizer = object : SpeechRecognizer {
            override val isStreaming = true
            override fun open(lang: String?): RecognitionSession {
                openError?.let { throw it }
                return Session().also { sessions += it }
            }
        }
        val translator = object : Translator {
            override fun translate(text: String, from: Lang?, to: Lang): Flow<String> {
                translatedSources += text
                targets += to
                return translation(text)
            }
        }
        return TranslateViewModel(recorder, recognizer, translator, LanguageDetector { detected }, MonotonicClock { now },
            object : LangPairStore {
                override suspend fun load(): LangPair? = null
                override suspend fun save(langs: LangPair) {}
            })
            .also { store.put("vm", it) }
    }

    private fun release(vm: TranslateViewModel) {
        now += 1000
        vm.onRelease()
    }

    @Test fun `permission failure is actionable and never offered as a translation retry`() = runTest {
        feedError = ServiceException(ServiceFailure.Permission)
        val vm = vm()
        vm.onPress()
        runCurrent()
        frames.send(shortArrayOf(1))
        runCurrent()
        val card = vm.state.value.cards.single()
        assertEquals(CardStage.Error, card.stage)
        assertEquals(ErrorKind.Permission, card.error)
        assertFalse(card.canRetry)
        assertTrue(recorderStopped)
        assertTrue(sessions.single().cancelled)
        assertTrue(translatedSources.isEmpty())
    }

    @Test fun `success uses recorded language pair and stops microphone before finish`() = runTest {
        nextTranscript = Transcript("Hello", null)
        val vm = vm()
        vm.setLang(0, Lang("zh-CN"))
        vm.setLang(1, Lang("en"))
        vm.onPress()
        runCurrent()
        val original = vm.state.value.cards.single().langs
        vm.setLang(0, Lang("ja"))
        vm.setLang(1, Lang("ko"))
        release(vm)
        runCurrent()
        assertEquals(PttPhase.Result, vm.state.value.phase)
        assertEquals(original.first, targets.single())
        assertEquals("Hello", vm.state.value.cards.single().translations.single().text)
        assertTrue(sessions.single().cancelled)
    }

    @Test fun `mis-tap and background cancel discard without finishing`() = runTest {
        val vm = vm()
        vm.onPress()
        runCurrent()
        now = 100
        vm.onRelease()
        runCurrent()
        assertTrue(vm.state.value.cards.isEmpty())
        assertTrue(sessions[0].cancelled)
        assertFalse(sessions[0].finished)
        vm.onPress()
        runCurrent()
        vm.onAbort()
        runCurrent()
        assertEquals(PttPhase.Idle, vm.state.value.phase)
        assertTrue(vm.state.value.cards.isEmpty())
        assertTrue(sessions[1].cancelled)
        assertFalse(sessions[1].finished)
    }

    @Test fun `open failure is surfaced before microphone starts`() = runTest {
        openError = ServiceException(ServiceFailure.Configuration)
        val vm = vm()
        vm.onPress()
        runCurrent()
        assertEquals(PttPhase.Error, vm.state.value.phase)
        assertEquals(ErrorKind.Configuration, vm.state.value.cards.single().error)
        assertFalse(recorderOpened)
        assertFalse(vm.state.value.cards.single().canRetry)
    }

    @Test fun `network error while feeding is not mislabeled as microphone error`() = runTest {
        feedError = ServiceException(ServiceFailure.RateLimit)
        val vm = vm()
        vm.onPress()
        runCurrent()
        frames.send(ShortArray(320))
        runCurrent()
        assertEquals(ErrorKind.RateLimit, vm.state.value.cards.single().error)
        assertTrue(recorderStopped)
        assertTrue(sessions.single().cancelled)
    }

    @Test fun `microphone failure closes recognition session`() = runTest {
        microphoneFails = true
        val vm = vm()
        vm.onPress()
        runCurrent()
        assertEquals(ErrorKind.Microphone, vm.state.value.cards.single().error)
        assertTrue(sessions.single().cancelled)
    }

    @Test fun `ASR timeout cancels socket and partial text is not retryable`() = runTest {
        finishBlock = { awaitCancellation() }
        val vm = vm()
        vm.onPress()
        runCurrent()
        sessions.single().partials.value = "unfinished"
        runCurrent()
        release(vm)
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        val card = vm.state.value.cards.single()
        assertEquals(ErrorKind.Timeout, card.error)
        assertFalse(card.canRetry)
        assertTrue(sessions.single().cancelled)
        vm.onRetry(card.id)
        assertEquals(PttPhase.Error, vm.state.value.phase)
        assertTrue(translatedSources.isEmpty())
    }

    @Test fun `retry targets selected old card and clears its partial translation`() = runTest {
        var failed = false
        translation = { text -> flow {
            if (text == "你好" && !failed) {
                failed = true
                emit("partial")
                throw ServiceException(ServiceFailure.Network)
            }
            emit("done:" + text)
        } }
        val vm = vm()
        vm.onPress()
        runCurrent()
        release(vm)
        runCurrent()
        val old = vm.state.value.cards.single()
        assertTrue(old.canRetry)
        assertEquals("partial", old.translations.single().text)
        nextTranscript = Transcript("第二句", Lang("zh"))
        vm.onPress()
        runCurrent()
        release(vm)
        runCurrent()
        assertEquals(PttPhase.Result, vm.state.value.phase)
        vm.onRetry(old.id)
        runCurrent()
        assertEquals(listOf("你好", "第二句", "你好"), translatedSources)
        assertEquals("done:你好", vm.state.value.cards.first { it.id == old.id }.translations.single().text)
        assertEquals("done:第二句", vm.state.value.cards.first().translations.single().text)
    }

    @Test fun `press interrupts translation without turning old card into an error`() = runTest {
        var interrupted = false
        translation = { flow {
            try { emit("partial"); awaitCancellation() } finally { interrupted = true }
        } }
        val vm = vm()
        vm.onPress()
        runCurrent()
        release(vm)
        runCurrent()
        assertEquals(PttPhase.Translating, vm.state.value.phase)
        vm.onPress()
        runCurrent()
        assertTrue(interrupted)
        assertEquals(PttPhase.Recording, vm.state.value.phase)
        assertEquals(CardStage.Done, vm.state.value.cards.last().stage)
        assertEquals("partial", vm.state.value.cards.last().translations.single().text)
        vm.onAbort()
        runCurrent()
    }

    @Test fun `empty recognition is not sent to translation`() = runTest {
        nextTranscript = Transcript("  ", null)
        val vm = vm()
        vm.onPress()
        runCurrent()
        release(vm)
        runCurrent()
        assertEquals(ErrorKind.NoSpeech, vm.state.value.cards.single().error)
        assertTrue(translatedSources.isEmpty())
    }

    @Test fun `time limit submits even if finger is still held`() = runTest {
        val vm = vm()
        vm.onPress()
        runCurrent()
        now = PttStateMachine.MAX_RECORD_MS
        advanceTimeBy(50)
        runCurrent()
        assertEquals(PttPhase.Result, vm.state.value.phase)
        assertTrue(sessions.single().finished)
        assertTrue(recorderStopped)
    }

    @Test fun `clearing view model closes live session`() = runTest {
        val vm = vm()
        vm.onPress()
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(recorderStopped)
        assertTrue(sessions.single().cancelled)
    }

    @Test fun `unknown detected language is translated into both conversation languages`() = runTest {
        nextTranscript = Transcript("ambiguous", null)
        detected = null
        val vm = vm()
        vm.onPress()
        runCurrent()
        release(vm)
        runCurrent()
        val card = vm.state.value.cards.single()
        assertEquals(listOf(card.langs.first, card.langs.second), targets)
        assertEquals(listOf("Hello", "Hello"), card.translations.map { it.text })
        assertEquals(CardStage.Done, card.stage)
    }

    @Test fun `a third language is translated into both, and one failure fails the card`() = runTest {
        nextTranscript = Transcript("Bonjour", Lang("fr"))
        translation = { flow { emit("x"); throw ServiceException(ServiceFailure.Network) } }
        val vm = vm()
        vm.setLang(0, Lang("zh-CN"))
        vm.setLang(1, Lang("en"))
        vm.onPress()
        runCurrent()
        release(vm)
        runCurrent()
        val card = vm.state.value.cards.single()
        assertEquals(listOf(Lang("zh-CN"), Lang("en")), card.translations.map { it.to })
        assertEquals(CardStage.Error, card.stage)
        assertEquals(ErrorKind.Network, card.error)
        assertTrue(card.canRetry)
    }
}
