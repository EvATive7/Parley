package io.github.evative7.parley.data

import org.junit.Assert.*
import org.junit.Test

class ServiceSettingsTest {
    private val live = ServiceSettings(asrKey = "test-asr", translationKey = "test-llm")

    @Test fun `services require both keys`() {
        assertFalse(ServiceSettings().isReady)
        assertTrue(live.isReady)
        assertFalse(live.copy(storageError = true).isReady)
    }

    @Test fun `rejects unsafe URLs and header control characters`() {
        for (url in listOf("ws://example.com", "https://example.com", "wss://u:p@example.com", "wss://example.com?key=x", "wss://example.com#fragment")) {
            assertEquals(url, SettingsError.AsrUrl, live.copy(asrUrl = url).validationError())
        }
        for (url in listOf("http://example.com/v1", "https://user@example.com/v1", "https://example.com?key=x")) {
            assertEquals(url, SettingsError.TranslationUrl, live.copy(translationUrl = url).validationError())
        }
        assertEquals(SettingsError.AsrKey, live.copy(asrKey = "secret\r\ninjected: header").validationError())
        assertEquals(SettingsError.TranslationModel, live.copy(translationModel = " ").validationError())
    }

    @Test fun `reports every invalid field so each can be marked`() {
        assertEquals(
            listOf(SettingsError.AsrKey, SettingsError.TranslationModel, SettingsError.TranslationKey),
            ServiceSettings(translationModel = "").validationErrors(),
        )
    }

    @Test fun `settings toString does not expose keys`() {
        assertFalse(live.toString().contains("test-asr"))
        assertFalse(live.toString().contains("test-llm"))
    }
}
