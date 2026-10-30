package io.github.evative7.parley.asr

import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.ServiceSettingsSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRecognizerTest {
    @Test fun `routes selected service and releases previous prepared recognizer`() {
        var current = ServiceSettings(dashScopeKey = "dash", translationKey = "llm")
        val source = object : ServiceSettingsSource { override val current get() = current }
        val dash = FakeRecognizer(true)
        val openAi = FakeRecognizer(false)
        val router = RoutingRecognizer(source) { service ->
            if (service.provider == AsrProvider.DashScope) dash else openAi
        }

        router.prepare()
        assertEquals(1, dash.prepared)
        assertTrue(router.isStreaming)
        assertSame(dash.session, router.open(null))

        current = current.copy(
            asrService = AsrService.Gpt4oMiniTranscribeNonStream,
            dashScopeKey = "",
            openAiAsrKey = "openai",
        )
        router.prepare()
        assertEquals(1, dash.released)
        assertEquals(1, openAi.prepared)
        assertFalse(router.isStreaming)
        assertSame(openAi.session, router.open(null))
    }

    private class FakeRecognizer(override val isStreaming: Boolean) : SpeechRecognizer {
        var prepared = 0
        var released = 0
        val session = object : RecognitionSession {
            override fun feed(pcm: ShortArray) = Unit
            override val partials = MutableStateFlow("")
            override suspend fun finish() = Transcript("ok", null)
            override fun cancel() = Unit
        }
        override fun prepare() { prepared++ }
        override fun release() { released++ }
        override fun open(lang: String?) = session
    }
}
