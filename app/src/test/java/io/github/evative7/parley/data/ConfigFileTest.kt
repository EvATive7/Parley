package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.mock.MockAsrFault
import io.github.evative7.parley.mock.MockSpeed
import io.github.evative7.parley.mock.MockTranslationFault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigFileTest {
    private val real = TranslationPreset("a", "Work", "https://api.example.com/v1", "gpt-4o-mini", "sk-1")
    private val mock = TranslationPreset(
        "b", "Simulated", "", "", "", mock = true,
        mockSpeed = MockSpeed.Slow, mockFault = MockTranslationFault.RateLimit,
    )
    private val settings = ServiceSettings(
        asrService = AsrService.DoubaoSeedAsr20Stream,
        dashScopeRegion = DashScopeRegion.Singapore,
        dashScopeKey = "ds",
        openAiAsrKey = "oa",
        volcAppId = "app",
        volcAccessToken = "token",
        mockAsrSpeed = MockSpeed.Fast,
        mockAsrFault = MockAsrFault.NoSpeech,
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
        assertEquals(AsrService.Default, decoded.asrService)
    }
}
