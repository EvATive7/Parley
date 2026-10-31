package io.github.evative7.parley.data

import kotlinx.serialization.json.Json
import io.github.evative7.parley.mock.MockSpeed
import io.github.evative7.parley.mock.MockTranslationFault
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** A saved translation endpoint. The key is plaintext only in memory, like [ServiceSettings]. */
data class TranslationPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val model: String,
    val key: String,
    /** Answered offline by the mock translator; only offered in debug builds. */
    val mock: Boolean = false,
    val mockSpeed: MockSpeed = MockSpeed.Normal,
    val mockFault: MockTranslationFault = MockTranslationFault.None,
) {
    val host: String get() = url.toHttpUrlOrNull()?.host ?: url

    /** Every invalid field, in dialog order. */
    fun validationErrors(): List<SettingsError> = buildList {
        if (name.isBlank()) add(SettingsError.PresetName)
        if (mock) return@buildList
        if (!ServiceSettings.secureUrl(url)) add(SettingsError.TranslationUrl)
        if (model.isBlank() || model.any { it.isISOControl() }) add(SettingsError.TranslationModel)
        if (key.isBlank() || key.any { it.isISOControl() }) add(SettingsError.TranslationKey)
    }

    override fun toString() = "TranslationPreset(id=$id, name=$name, host=$host, model=$model, key=<redacted>)"
}

/** Ciphertext is bound to the preset id, so keys cannot be swapped between presets on disk. */
private fun keyField(id: String) = "translation_preset_key:$id"

internal fun encodePresets(
    presets: List<TranslationPreset>,
    encrypt: (value: String, field: String) -> String,
): String = buildJsonArray {
    presets.forEach { preset ->
        add(buildJsonObject {
            put("id", preset.id)
            put("name", preset.name)
            put("url", preset.url)
            put("model", preset.model)
            put("key_encrypted", encrypt(preset.key, keyField(preset.id)))
            if (preset.mock) {
                put("mock", true)
                put("mock_speed", preset.mockSpeed.name)
                put("mock_fault", preset.mockFault.name)
            }
        })
    }
}.toString()

/** A preset whose key cannot be decrypted (e.g. Keystore reset) keeps its other fields with an empty key. */
internal fun decodePresets(
    json: String,
    decrypt: (ciphertext: String, field: String) -> String,
): List<TranslationPreset> {
    if (json.isBlank()) return emptyList()
    val array: JsonArray = Json.parseToJsonElement(json).jsonArray
    return array.mapNotNull { element ->
        val o = element.jsonObject
        val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        TranslationPreset(
            id = id,
            name = o["name"]?.jsonPrimitive?.content.orEmpty(),
            url = o["url"]?.jsonPrimitive?.content.orEmpty(),
            model = o["model"]?.jsonPrimitive?.content.orEmpty(),
            key = runCatching { decrypt(o["key_encrypted"]?.jsonPrimitive?.content.orEmpty(), keyField(id)) }.getOrDefault(""),
            mock = o["mock"]?.jsonPrimitive?.booleanOrNull == true,
            mockSpeed = MockSpeed.fromName(o["mock_speed"]?.jsonPrimitive?.content),
            mockFault = MockTranslationFault.fromName(o["mock_fault"]?.jsonPrimitive?.content),
        )
    }
}
