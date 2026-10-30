package io.github.evative7.parley.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPresetTest {
    private val preset = TranslationPreset(
        id = "p1", name = "Qwen", url = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model = "qwen3.7-flash", key = "sk-secret",
    )

    @Test fun `round trip keeps fields and binds each key to its preset id`() {
        val fields = mutableListOf<String>()
        val json = encodePresets(listOf(preset)) { value, field -> fields += field; "enc($value)" }
        assertFalse("plaintext key must not be stored", "sk-secret\"" in json)
        assertEquals(listOf("translation_preset_key:p1"), fields)
        val decoded = decodePresets(json) { ciphertext, field ->
            assertEquals("translation_preset_key:p1", field)
            ciphertext.removePrefix("enc(").removeSuffix(")")
        }
        assertEquals(listOf(preset), decoded)
    }

    @Test fun `an undecryptable key leaves the rest of the preset`() {
        val json = encodePresets(listOf(preset)) { _, _ -> "garbage" }
        val decoded = decodePresets(json) { _, _ -> error("keystore reset") }
        assertEquals(preset.copy(key = ""), decoded.single())
    }

    @Test fun `empty storage decodes to no presets`() {
        assertTrue(decodePresets("") { _, _ -> "" }.isEmpty())
    }

    @Test fun `validation lists every invalid field in dialog order`() {
        assertTrue(preset.validationErrors().isEmpty())
        val invalid = TranslationPreset(name = " ", url = "http://insecure.example/v1", model = "", key = "")
        assertEquals(
            listOf(SettingsError.PresetName, SettingsError.TranslationUrl, SettingsError.TranslationModel, SettingsError.TranslationKey),
            invalid.validationErrors(),
        )
    }

    @Test fun `a new service becomes active and its fields drive the translator`() {
        val settings = ServiceSettings(dashScopeKey = "asr").putTranslationPreset(preset)
        assertEquals("p1", settings.translationPresetId)
        assertEquals(preset.url, settings.translationUrl)
        assertEquals(preset.model, settings.translationModel)
        assertEquals(preset.key, settings.translationKey)
        assertTrue(settings.isReady)
    }

    @Test fun `editing the active service updates the active fields, editing another does not`() {
        val other = preset.copy(id = "p2", name = "OpenAI", url = "https://api.openai.com/v1", model = "gpt-4o-mini")
        val settings = ServiceSettings().putTranslationPreset(preset).putTranslationPreset(other).selectTranslation(preset)
        assertEquals("p1", settings.translationPresetId)
        assertEquals("qwen-plus", settings.putTranslationPreset(preset.copy(model = "qwen-plus")).translationModel)
        assertEquals("qwen3.7-flash", settings.putTranslationPreset(other.copy(model = "gpt-5")).translationModel)
    }

    @Test fun `deleting the active service activates the next one, deleting the last leaves none`() {
        val other = preset.copy(id = "p2", model = "other-model")
        val settings = ServiceSettings().putTranslationPreset(preset).putTranslationPreset(other).selectTranslation(preset)
        val afterDelete = settings.removeTranslationPreset("p1")
        assertEquals("p2", afterDelete.translationPresetId)
        assertEquals("other-model", afterDelete.translationModel)
        val empty = afterDelete.removeTranslationPreset("p2")
        assertEquals(null, empty.translationPresetId)
        assertTrue(SettingsError.TranslationKey in empty.validationErrors())
    }

    @Test fun `toString never contains the key`() {
        assertFalse("sk-secret" in preset.toString())
    }
}
