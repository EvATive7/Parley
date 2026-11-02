package io.github.evative7.parley.asr

import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.network.ServiceException
import io.github.evative7.parley.network.ServiceFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRecognizerTest {
    private var current = ServiceSettings(
        dashScopeKey = "dash", openAiAsrKey = "openai", translationKey = "llm", asrGraceMs = GRACE_MS,
    )
    private val source = object : ServiceSettingsSource { override val current get() = this@RoutingRecognizerTest.current }
    private val dash = FakeRecognizer()
    private val openAi = FakeRecognizer()
    private val router = RoutingRecognizer(source, Dispatchers.Unconfined) { service ->
        if (service.provider == AsrProvider.DashScope) dash else openAi
    }

    private fun select(vararg services: AsrService) {
        current = current.copy(asrServices = services.toList())
    }

    @Test fun `one service is used directly and a deselected recognizer is released`() {
        router.prepare()
        assertEquals(listOf(AsrService.Default), dash.prepared)
        assertTrue(router.isStreaming)
        val session = router.open(null)
        assertSame(dash.session(AsrService.Default), session)

        select(AsrService.Gpt4oMiniTranscribeNonStream)
        router.prepare()
        assertEquals(1, dash.released)
        assertEquals(listOf(AsrService.Gpt4oMiniTranscribeNonStream), openAi.prepared)
        assertFalse(router.isStreaming)
    }

    @Test fun `audio reaches every service and the first streaming one shows partials`() = runTest {
        select(AsrService.Gpt4oTranscribeNonStream, AsrService.FunAsrRealtimeStream)
        val session = router.open(null)
        session.feed(shortArrayOf(1, 2))
        assertEquals(1, dash.session(AsrService.FunAsrRealtimeStream).fed)
        assertEquals(1, openAi.session(AsrService.Gpt4oTranscribeNonStream).fed)
        dash.session(AsrService.FunAsrRealtimeStream).partials.value = "hel"
        assertSame(dash.session(AsrService.FunAsrRealtimeStream).partials, session.partials)
    }

    @Test fun `the fastest transcript wins and the others arriving in the grace period are alternatives`() = runTest {
        select(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream, AsrService.Gpt4oMiniTranscribeNonStream)
        val session = router.open(null)
        val result = async { session.finish() }
        runCurrent()
        openAi.session(AsrService.Gpt4oTranscribeNonStream).result.complete(Transcript("hello there", null))
        runCurrent()
        assertFalse(result.isCompleted)
        dash.session(AsrService.FunAsrRealtimeStream).result.complete(Transcript("hello bear", null))
        advanceTimeBy(GRACE_MS + 1)
        runCurrent()
        // The third never answers: the grace period ends the wait.
        val transcript = result.await()
        assertEquals("hello there", transcript.text)
        assertEquals(AsrService.Gpt4oTranscribeNonStream, transcript.service)
        assertEquals(listOf(AsrAlternative(AsrService.FunAsrRealtimeStream, "hello bear")), transcript.alternatives)

        // The third answers after the grace period: shown later, and then the session is over.
        val late = async { transcript.late.toList() }
        runCurrent()
        openAi.session(AsrService.Gpt4oMiniTranscribeNonStream).result.complete(Transcript("hello where", null))
        assertEquals(listOf(AsrAlternative(AsrService.Gpt4oMiniTranscribeNonStream, "hello where")), late.await())
        assertTrue(dash.session(AsrService.FunAsrRealtimeStream).cancelled)
    }

    @Test fun `identical transcripts are still listed, each under its service`() = runTest {
        select(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream)
        val session = router.open(null)
        dash.session(AsrService.FunAsrRealtimeStream).result.complete(Transcript("same", null))
        openAi.session(AsrService.Gpt4oTranscribeNonStream).result.complete(Transcript(" same ", null))
        val transcript = session.finish()
        assertEquals(1, transcript.alternatives.size)
        assertEquals("same", transcript.alternatives.single().text)
    }

    @Test fun `one failing service doesn't fail the press, all failing does`() = runTest {
        select(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream)
        val session = router.open(null)
        dash.session(AsrService.FunAsrRealtimeStream).result.completeExceptionally(ServiceException(ServiceFailure.Network))
        openAi.session(AsrService.Gpt4oTranscribeNonStream).result.complete(Transcript("ok", null))
        assertEquals("ok", session.finish().text)

        val failing = router.open(null)
        val auth = ServiceException(ServiceFailure.Authentication)
        dash.session(AsrService.FunAsrRealtimeStream).result.completeExceptionally(auth)
        openAi.session(AsrService.Gpt4oTranscribeNonStream).result.completeExceptionally(ServiceException(ServiceFailure.Network))
        assertSame(auth, runCatching { failing.finish() }.exceptionOrNull())
    }

    @Test fun `nothing heard by anyone is no speech`() = runTest {
        select(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream)
        val session = router.open(null)
        dash.session(AsrService.FunAsrRealtimeStream).result.complete(Transcript("", null))
        openAi.session(AsrService.Gpt4oTranscribeNonStream).result.completeExceptionally(ServiceException(ServiceFailure.Network))
        assertEquals("", session.finish().text)
    }

    @Test fun `a service failing while recording is dropped, and the press fails once all have`() {
        select(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream)
        val session = router.open(null)
        dash.session(AsrService.FunAsrRealtimeStream).feedError = ServiceException(ServiceFailure.Network)
        session.feed(shortArrayOf(1))
        assertTrue(dash.session(AsrService.FunAsrRealtimeStream).cancelled)
        session.feed(shortArrayOf(1))
        assertEquals(0, dash.session(AsrService.FunAsrRealtimeStream).fed)
        assertEquals(2, openAi.session(AsrService.Gpt4oTranscribeNonStream).fed)
        openAi.session(AsrService.Gpt4oTranscribeNonStream).feedError = ServiceException(ServiceFailure.Network)
        assertThrows(ServiceException::class.java) { session.feed(shortArrayOf(1)) }
    }

    @Test fun `cancel reaches every service`() {
        select(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream)
        router.open(null).cancel()
        assertTrue(dash.session(AsrService.FunAsrRealtimeStream).cancelled)
        assertTrue(openAi.session(AsrService.Gpt4oTranscribeNonStream).cancelled)
    }

    private class FakeSession : RecognitionSession {
        var fed = 0
        var cancelled = false
        var feedError: Exception? = null
        val result = CompletableDeferred<Transcript>()
        override val partials = MutableStateFlow("")
        override fun feed(pcm: ShortArray) {
            feedError?.let { throw it }
            fed++
        }
        override suspend fun finish() = result.await()
        override fun cancel() { cancelled = true }
    }

    private class FakeRecognizer : ServiceRecognizer {
        val prepared = mutableListOf<AsrService>()
        var released = 0
        val sessions = mutableMapOf<AsrService, FakeSession>()
        fun session(service: AsrService) = sessions.getValue(service)
        override fun prepare(service: AsrService) { prepared += service }
        override fun release() { released++ }
        override fun open(service: AsrService, lang: String?) = FakeSession().also { sessions[service] = it }
    }

    private companion object { const val GRACE_MS = 1_500L }
}
