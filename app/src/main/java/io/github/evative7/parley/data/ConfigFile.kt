package io.github.evative7.parley.data

import io.github.evative7.parley.BuildConfig
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.mock.MockAsrFault
import io.github.evative7.parley.mock.MockSpeed
import io.github.evative7.parley.mock.MockTranslationFault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The service settings as a portable JSON file, for moving them to another device. Keys are written in
 * plain text: the file is only as private as the place it is kept.
 *
 * [VERSION] goes up whenever the layout changes in a way an older reader can't follow; a reader accepts
 * its own version and older ones, and refuses newer files instead of guessing.
 */
object ConfigFile {
    const val FORMAT = "parley-config"
    const val VERSION = 1

    private val json = Json { prettyPrint = true }

    fun encode(settings: ServiceSettings, appVersion: String): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("app_version", appVersion)
            putJsonObject("asr") {
                put("service", settings.asrService.id)
                putJsonObject("dashscope") {
                    put("region", settings.dashScopeRegion.name)
                    put("key", settings.dashScopeKey)
                }
                putJsonObject("openai") {
                    put("key", settings.openAiAsrKey)
                }
                putJsonObject("volcengine") {
                    put("app_id", settings.volcAppId)
                    put("access_token", settings.volcAccessToken)
                }
                putJsonObject("mock") {
                    put("speed", settings.mockAsrSpeed.name)
                    put("fault", settings.mockAsrFault.name)
                }
            }
            putJsonObject("translation") {
                settings.translationPresetId?.let { put("selected", it) }
                put("services", buildJsonArray {
                    settings.translationPresets.forEach { preset ->
                        add(buildJsonObject {
                            put("id", preset.id)
                            put("name", preset.name)
                            put("type", if (preset.mock) TYPE_MOCK else TYPE_OPENAI_COMPATIBLE)
                            if (preset.mock) {
                                put("speed", preset.mockSpeed.name)
                                put("fault", preset.mockFault.name)
                            } else {
                                put("url", preset.url)
                                put("model", preset.model)
                                put("key", preset.key)
                            }
                        })
                    }
                })
            }
        },
    )

    /** Reads a file written by [encode]. Mock services are dropped in builds that can't run them. */
    fun decode(text: String): ServiceSettings {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            throw ConfigFileException(ConfigFileException.Reason.NotAConfig)
        }
        if (root.string("format") != FORMAT) throw ConfigFileException(ConfigFileException.Reason.NotAConfig)
        val version = root["version"]?.jsonPrimitive?.intOrNull
            ?: throw ConfigFileException(ConfigFileException.Reason.NotAConfig)
        if (version > VERSION) throw ConfigFileException(ConfigFileException.Reason.TooNew)

        return try {
            val asr = root.obj("asr")
            val dashScope = asr?.obj("dashscope")
            val volc = asr?.obj("volcengine")
            val mockAsr = asr?.obj("mock")
            val presets = root.obj("translation")?.get("services")?.jsonArray.orEmpty().mapNotNull { element ->
                val o = element.jsonObject
                val id = o.string("id") ?: return@mapNotNull null
                val mock = o.string("type") == TYPE_MOCK
                if (mock && !BuildConfig.DEBUG) return@mapNotNull null
                TranslationPreset(
                    id = id,
                    name = o.string("name").orEmpty(),
                    url = o.string("url").orEmpty(),
                    model = o.string("model").orEmpty(),
                    key = o.string("key").orEmpty(),
                    mock = mock,
                    mockSpeed = MockSpeed.fromName(o.string("speed")),
                    mockFault = MockTranslationFault.fromName(o.string("fault")),
                )
            }
            val selectedId = root.obj("translation")?.string("selected")
            ServiceSettings(
                asrService = AsrService.fromId(asr?.string("service")),
                dashScopeRegion = DashScopeRegion.fromName(dashScope?.string("region")),
                dashScopeKey = dashScope?.string("key").orEmpty(),
                openAiAsrKey = asr?.obj("openai")?.string("key").orEmpty(),
                volcAppId = volc?.string("app_id").orEmpty(),
                volcAccessToken = volc?.string("access_token").orEmpty(),
                mockAsrSpeed = MockSpeed.fromName(mockAsr?.string("speed")),
                mockAsrFault = MockAsrFault.fromName(mockAsr?.string("fault")),
                translationPresets = presets,
            ).selectTranslation(presets.firstOrNull { it.id == selectedId } ?: presets.firstOrNull())
        } catch (e: ConfigFileException) {
            throw e
        } catch (_: Exception) {
            throw ConfigFileException(ConfigFileException.Reason.NotAConfig)
        }
    }

    private const val TYPE_OPENAI_COMPATIBLE = "openai-compatible"
    private const val TYPE_MOCK = "mock"

    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

class ConfigFileException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NotAConfig, TooNew }
}
