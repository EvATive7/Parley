package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.mock.MockAsrFault
import io.github.evative7.parley.mock.MockSpeed
import io.github.evative7.parley.mock.MockTranslationFault
import io.github.evative7.parley.mock.MockTtsFault
import io.github.evative7.parley.tts.MiniMaxRegion
import io.github.evative7.parley.tts.TtsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigFileTest {
    private val real = TranslationPreset("a", "Work", "https://api.example.com/v1", "gpt-4o-mini", "sk-1")
    private val mock = TranslationPreset(
        "b", "Simulated", "", "", "", mock = true,
        mockSpeed = MockSpeed.Slow, mockFault = MockTranslationFault.RateLimit,
    )
    private val settings = ServiceSettings(
        asrServices = listOf(AsrService.DoubaoSeedAsr20Stream, AsrService.FunAsrRealtimeNonStream),
        asrGraceMs = 2_500,
        dashScopeRegion = DashScopeRegion.Singapore,
        dashScopeKey = "ds",
        openAiKey = "oa",
        volcengineAppId = "app",
        volcengineAccessToken = "token",
        miniMaxRegion = MiniMaxRegion.International,
        miniMaxKey = "mini",
        mockAsrSpeed = MockSpeed.Fast,
        mockAsrFault = MockAsrFault.NoSpeech,
        ttsService = TtsService.MiniMaxSpeech28Hd,
        ttsAutoPlay = true,
        ttsAutoPlayFaceToFace = true,
        ttsVoices = mapOf(TtsService.MiniMaxSpeech28Hd.id to "voice-id"),
        mockTtsSpeed = MockSpeed.Slow,
        mockTtsFault = MockTtsFault.Timeout,
    ).putTranslationPreset(real).putTranslationPreset(mock).selectTranslation(real)

    @Test fun `round trip keeps every setting and the selected service`() {
        assertEquals(settings, ConfigFile.decode(ConfigFile.encode(settings, "1.0")))
    }

    @Test fun `a newer format is refused rather than guessed`() {
        val newer = ConfigFile.encode(settings, "1.0")
            .replace("\"version\": ${ConfigFile.VERSION}", "\"version\": ${ConfigFile.VERSION + 1}")
        val e = assertThrows(ConfigFileException::class.java) { ConfigFile.decode(newer) }
        assertEquals(ConfigFileException.Reason.TooNew, e.reason)
    }

    @Test fun `other files are not configurations`() {
        for (text in listOf("", "not json", "[]", """{"format":"other","version":1}""", """{"format":"parley-config"}""")) {
            val e = assertThrows(ConfigFileException::class.java) { ConfigFile.decode(text) }
            assertEquals(text, ConfigFileException.Reason.NotAConfig, e.reason)
        }
    }

    @Test fun `missing selection falls back to the first service`() {
        val text = """{"format":"parley-config","version":1,"translation":{"services":[
            {"id":"x","name":"X","type":"openai-compatible","url":"https://x.example/v1","model":"m","key":"k"}]}}"""
        val decoded = ConfigFile.decode(text)
        assertEquals("x", decoded.translationPresetId)
        assertEquals("https://x.example/v1", decoded.translationUrl)
        assertEquals(listOf(AsrService.Default), decoded.asrServices)
    }

    @Test fun `a version 1 file's single service becomes the list`() {
        val text = """{"format":"parley-config","version":1,"asr":{"service":"gpt-4o-transcribe-non-stream-input",
            "openai":{"key":"oa"}}}"""
        val decoded = ConfigFile.decode(text)
        assertEquals(listOf(AsrService.Gpt4oTranscribeNonStream), decoded.asrServices)
        assertEquals("oa", decoded.openAiKey)
    }

    @Test fun `a version 2 file moves credentials to providers and adds TTS defaults`() {
        val text = """{"format":"parley-config","version":2,"asr":{
            "services":["gpt-4o-transcribe-non-stream-input"],"grace_ms":2500,
            "dashscope":{"region":"Singapore","key":"ds"},
            "openai":{"key":"oa"},
            "volcengine":{"app_id":"app","access_token":"token"},
            "mock":{"speed":"Fast","fault":"NoSpeech"}}}"""
        val decoded = ConfigFile.decode(text)
        assertEquals(listOf(AsrService.Gpt4oTranscribeNonStream), decoded.asrServices)
        assertEquals(2_500L, decoded.asrGraceMs)
        assertEquals(DashScopeRegion.Singapore, decoded.dashScopeRegion)
        assertEquals("ds", decoded.dashScopeKey)
        assertEquals("oa", decoded.openAiKey)
        assertEquals("app", decoded.volcengineAppId)
        assertEquals("token", decoded.volcengineAccessToken)
        assertEquals(TtsService.Default, decoded.ttsService)
        assertFalse(decoded.ttsAutoPlay)
        assertFalse(decoded.ttsAutoPlayFaceToFace)
        assertTrue(decoded.ttsVoices.isEmpty())
    }

    @Test fun `unknown and repeated services are dropped, and none left means the default`() {
        val text = """{"format":"parley-config","version":2,"asr":{"services":
            ["removed-service","doubao-seed-asr-2-0-stream-input","doubao-seed-asr-2-0-stream-input"]}}"""
        assertEquals(listOf(AsrService.DoubaoSeedAsr20Stream), ConfigFile.decode(text).asrServices)
        val none = """{"format":"parley-config","version":2,"asr":{"services":["removed-service"]}}"""
        assertEquals(listOf(AsrService.Default), ConfigFile.decode(none).asrServices)
    }

    @Test fun `the grace period defaults when missing and is kept within bounds`() {
        val missing = """{"format":"parley-config","version":2,"asr":{"services":["mock-stream"]}}"""
        assertEquals(ServiceSettings.DEFAULT_ASR_GRACE_MS, ConfigFile.decode(missing).asrGraceMs)
        val huge = """{"format":"parley-config","version":2,"asr":{"grace_ms":999999}}"""
        assertEquals(ServiceSettings.MAX_ASR_GRACE_MS, ConfigFile.decode(huge).asrGraceMs)
    }

    @Test fun `a version 0 file is not a configuration`() {
        val e = assertThrows(ConfigFileException::class.java) {
            ConfigFile.decode("""{"format":"parley-config","version":0}""")
        }
        assertEquals(ConfigFileException.Reason.NotAConfig, e.reason)
    }

    @Test fun `version 3 writes shared providers and ASR services as a list`() {
        val text = ConfigFile.encode(settings, "1.0")
        assertTrue(text.contains("\"providers\""))
        assertTrue(text.contains("\"services\""))
        assertFalse(text.contains("\"asr\": {\n        \"service\""))
    }
}
