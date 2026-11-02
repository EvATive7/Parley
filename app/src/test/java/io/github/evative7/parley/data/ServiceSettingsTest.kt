package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.AsrService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceSettingsTest {
    private val live = ServiceSettings(dashScopeKey = "test-asr", translationKey = "test-llm")

    @Test fun `only the selected ASR provider credentials are required`() {
        assertFalse(ServiceSettings().isReady)
        assertTrue(live.isReady)
        assertTrue(live.copy(
            asrServices = listOf(AsrService.Gpt4oTranscribeNonStream),
            dashScopeKey = "",
            openAiAsrKey = "openai-asr",
        ).isReady)
        assertTrue(live.copy(
            asrServices = listOf(AsrService.DoubaoSeedAsr20Stream),
            dashScopeKey = "",
            volcAppId = "app-id",
            volcAccessToken = "access-token",
        ).isReady)
        assertFalse(live.copy(storageError = true).isReady)
    }

    @Test fun `every selected provider needs its credentials, and at least one service is needed`() {
        val both = live.copy(asrServices = listOf(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream))
        assertEquals(listOf(AsrProvider.DashScope, AsrProvider.OpenAi), both.asrProviders)
        assertEquals(listOf(SettingsError.AsrKey), both.asrErrors(AsrProvider.OpenAi))
        assertTrue(both.asrErrors(AsrProvider.DashScope).isEmpty())
        assertFalse(both.isReady)
        assertTrue(both.copy(openAiAsrKey = "openai").isReady)
        assertEquals(SettingsError.AsrNone, live.copy(asrServices = emptyList()).validationError())
    }

    @Test fun `rejects unsafe translation URLs and credential control characters`() {
        for (url in listOf("http://example.com/v1", "https://user@example.com/v1", "https://example.com?key=x")) {
            assertEquals(url, SettingsError.TranslationUrl, live.copy(translationUrl = url).validationError())
        }
        assertEquals(SettingsError.AsrKey, live.copy(dashScopeKey = "secret\r\ninjected: header").validationError())
        assertEquals(SettingsError.TranslationModel, live.copy(translationModel = " ").validationError())
    }

    @Test fun `volcengine requires AppID before access token`() {
        val volc = live.copy(
            asrServices = listOf(AsrService.DoubaoSeedAsr20NonStream),
            dashScopeKey = "",
        )
        assertEquals(
            listOf(SettingsError.AsrAppId, SettingsError.AsrKey),
            volc.validationErrors(),
        )
    }

    @Test fun `reports every invalid field so each can be marked`() {
        assertEquals(
            listOf(SettingsError.AsrKey, SettingsError.TranslationModel, SettingsError.TranslationKey),
            ServiceSettings(translationModel = "").validationErrors(),
        )
    }

    @Test fun `settings toString does not expose credentials`() {
        val settings = live.copy(
            openAiAsrKey = "openai-secret",
            volcAppId = "private-app-id",
            volcAccessToken = "volc-secret",
        )
        for (secret in listOf("test-asr", "test-llm", "openai-secret", "private-app-id", "volc-secret")) {
            assertFalse(settings.toString().contains(secret))
        }
    }
}
