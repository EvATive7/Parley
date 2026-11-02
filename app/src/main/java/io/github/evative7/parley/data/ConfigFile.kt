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
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The service settings as one JSON document: the portable file for moving them to another device, and,
 * encrypted whole, what the app itself stores. Keys are written in plain text: an exported file is only as
 * private as the place it is kept.
 *
 * [VERSION] goes up by one with every change to the layout. Only the current layout has a reader: an older
 * file is first upgraded one version at a time along [MIGRATIONS] (1 → 2 → … → [VERSION]). Newer files are
 * refused instead of guessed at. See AGENTS.md.
 */
object ConfigFile {
    const val FORMAT = "parley-config"
    const val VERSION = 2

    private val json = Json { prettyPrint = true }

    fun encode(settings: ServiceSettings, appVersion: String): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("app_version", appVersion)
            putJsonObject("asr") {
                put("services", buildJsonArray { settings.asrServices.forEach { add(JsonPrimitive(it.id)) } })
                put("grace_ms", settings.asrGraceMs)
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
        if (version < 1) throw ConfigFileException(ConfigFileException.Reason.NotAConfig)
        if (version > VERSION) throw ConfigFileException(ConfigFileException.Reason.TooNew)

        return try {
            val current = (version until VERSION).fold(root) { o, from -> MIGRATIONS[from - 1](o) }
            read(current)
        } catch (e: ConfigFileException) {
            throw e
        } catch (_: Exception) {
            throw ConfigFileException(ConfigFileException.Reason.NotAConfig)
        }
    }

    /**
     * One step per version: entry `n - 1` turns a version `n` document into version `n + 1`. Steps are never
     * edited once released; a layout change adds a step and bumps [VERSION].
     */
    private val MIGRATIONS: List<(JsonObject) -> JsonObject> = listOf(
        // 1 → 2: the one ASR service became a list of them.
        { root ->
            val asr = root.obj("asr")
            if (asr == null) root else {
                val service = asr.string("service")
                val services = buildJsonArray { if (service != null) add(JsonPrimitive(service)) }
                JsonObject(root + ("asr" to JsonObject(asr - "service" + ("services" to services))))
            }
        },
    )

    init {
        check(MIGRATIONS.size == VERSION - 1) { "Each version needs its migration step" }
    }

    /** Reads a document in the current layout. */
    private fun read(root: JsonObject): ServiceSettings {
        val asr = root.obj("asr")
        val dashScope = asr?.obj("dashscope")
        val volc = asr?.obj("volcengine")
        val mockAsr = asr?.obj("mock")
        val serviceIds = asr?.get("services")?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val graceMs = (asr?.get("grace_ms") as? JsonPrimitive)?.longOrNull
            ?.coerceIn(0, ServiceSettings.MAX_ASR_GRACE_MS) ?: ServiceSettings.DEFAULT_ASR_GRACE_MS
        // Services this build doesn't know or can't run are left out; none left means the default.
        val services = serviceIds.mapNotNull { AsrService.find(it) }.distinct().ifEmpty { listOf(AsrService.Default) }
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
        return ServiceSettings(
            asrServices = services,
            asrGraceMs = graceMs,
            dashScopeRegion = DashScopeRegion.fromName(dashScope?.string("region")),
            dashScopeKey = dashScope?.string("key").orEmpty(),
            openAiAsrKey = asr?.obj("openai")?.string("key").orEmpty(),
            volcAppId = volc?.string("app_id").orEmpty(),
            volcAccessToken = volc?.string("access_token").orEmpty(),
            mockAsrSpeed = MockSpeed.fromName(mockAsr?.string("speed")),
            mockAsrFault = MockAsrFault.fromName(mockAsr?.string("fault")),
            translationPresets = presets,
        ).selectTranslation(presets.firstOrNull { it.id == selectedId } ?: presets.firstOrNull())
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
