package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.tts.MiniMaxRegion
import io.github.evative7.parley.tts.TtsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            openAiKey = "openai-asr",
            ttsService = null,
        ).isReady)
        assertTrue(live.copy(
            asrServices = listOf(AsrService.DoubaoSeedAsr20Stream),
            dashScopeKey = "",
            volcengineAppId = "app-id",
            volcengineAccessToken = "access-token",
            ttsService = null,
        ).isReady)
        assertFalse(live.copy(storageError = true).isReady)
    }

    @Test fun `every selected provider needs its credentials, and at least one service is needed`() {
        val both = live.copy(
            asrServices = listOf(AsrService.FunAsrRealtimeStream, AsrService.Gpt4oTranscribeNonStream),
            ttsService = null,
        )
        assertEquals(listOf(AsrProvider.DashScope, AsrProvider.OpenAi), both.asrProviders)
        assertEquals(listOf(SettingsError.AsrKey), both.asrErrors(AsrProvider.OpenAi))
        assertTrue(both.asrErrors(AsrProvider.DashScope).isEmpty())
        assertFalse(both.isReady)
        assertTrue(both.copy(openAiKey = "openai").isReady)
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
            ttsService = null,
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

    @Test fun `selected TTS provider credentials are required even when ASR uses another provider`() {
        val miniMax = live.copy(
            ttsService = TtsService.MiniMaxSpeech28Turbo,
            miniMaxRegion = MiniMaxRegion.International,
        )
        assertEquals(listOf(AsrProvider.DashScope, AsrProvider.MiniMax), miniMax.usedProviders)
        assertEquals(SettingsError.AsrKey, miniMax.validationError())
        assertTrue(miniMax.copy(miniMaxKey = "minimax-key").isReady)
        assertTrue(miniMax.copy(ttsService = null).isReady)
    }

    @Test fun `voice selection refuses unmapped languages but always accepts a custom ID`() {
        val cosy = live.copy(ttsService = TtsService.CosyVoiceV3Flash)
        assertNull(cosy.speechChoice(7, Lang("fr"), "bonjour"))
        val custom = cosy.copy(ttsVoices = mapOf(TtsService.CosyVoiceV3Flash.id to "custom"))
            .speechChoice(7, Lang("fr"), "bonjour")!!
        assertEquals(TtsService.CosyVoiceV3Flash, custom.key.service)
        assertEquals("custom", custom.key.voice)
    }

    @Test fun `the default service has a voice for every offered language`() {
        for (lang in Lang.targets) assertNotNull(live.speechChoice(7, lang, "x"))
    }

    @Test fun `voices offered in only one region are refused elsewhere`() {
        val cosy = live.copy(ttsService = TtsService.CosyVoiceV3Flash)
        assertEquals("loongtomoka_v3", cosy.copy(dashScopeRegion = DashScopeRegion.Beijing).speechChoice(7, Lang("ja"), "x")!!.key.voice)
        assertNull(cosy.copy(dashScopeRegion = DashScopeRegion.Singapore).speechChoice(7, Lang("ja"), "x"))
    }

    @Test fun `settings toString does not expose credentials`() {
        val settings = live.copy(
            openAiKey = "openai-secret",
            volcengineAppId = "private-app-id",
            volcengineAccessToken = "volc-secret",
        )
        for (secret in listOf("test-asr", "test-llm", "openai-secret", "private-app-id", "volc-secret")) {
            assertFalse(settings.toString().contains(secret))
        }
    }
}
