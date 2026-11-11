package io.github.evative7.parley.data

import io.github.evative7.parley.BuildConfig
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.mock.MockAsrFault
import io.github.evative7.parley.mock.MockSpeed
import io.github.evative7.parley.mock.MockTranslationFault
import io.github.evative7.parley.mock.MockTtsFault
import io.github.evative7.parley.tts.MiniMaxRegion
import io.github.evative7.parley.tts.TtsService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
    const val VERSION = 5

    private val json = Json { prettyPrint = true }

    fun encode(settings: ServiceSettings, appVersion: String): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("app_version", appVersion)
            putJsonObject("providers") {
                putJsonObject("dashscope") {
                    put("region", settings.dashScopeRegion.name)
                    put("key", settings.dashScopeKey)
                }
                putJsonObject("openai") {
                    put("key", settings.openAiKey)
                }
                putJsonObject("volcengine") {
                    put("app_id", settings.volcengineAppId)
                    put("access_token", settings.volcengineAccessToken)
                }
                putJsonObject("minimax") {
                    put("region", settings.miniMaxRegion.name)
                    put("key", settings.miniMaxKey)
                }
            }
            putJsonObject("asr") {
                put("services", buildJsonArray { settings.asrServices.forEach { add(JsonPrimitive(it.id)) } })
                put("grace_ms", settings.asrGraceMs)
                putJsonObject("mock") {
                    put("speed", settings.mockAsrSpeed.name)
                    put("fault", settings.mockAsrFault.name)
                }
            }
            putJsonObject("tts") {
                put("service", settings.ttsService?.let { JsonPrimitive(it.id) } ?: JsonNull)
                put("auto_generate", settings.ttsAutoGenerate)
                put("auto_play_portrait", settings.ttsAutoPlayPortrait)
                put("auto_play_face_to_face", settings.ttsAutoPlayFaceToFace)
                put("voices", JsonObject(settings.ttsVoices.mapValues { (_, voices) ->
                    JsonObject(voices.mapValues { JsonPrimitive(it.value) })
                }))
                putJsonObject("mock") {
                    put("speed", settings.mockTtsSpeed.name)
                    put("fault", settings.mockTtsFault.name)
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
        // 2 → 3: provider credentials moved out of `asr` to be shared with TTS.
        { root ->
            val asr = root.obj("asr") ?: JsonObject(emptyMap())
            val providers = buildJsonObject {
                asr["dashscope"]?.let { put("dashscope", it) }
                asr["openai"]?.let { put("openai", it) }
                asr["volcengine"]?.let { put("volcengine", it) }
            }
            val migratedAsr = JsonObject(asr - "dashscope" - "openai" - "volcengine")
            // No `tts` or MiniMax entry: the reader's defaults apply, and this step stays fixed even if they change.
            JsonObject(root + ("providers" to providers) + ("asr" to migratedAsr))
        },
        // 3 → 4: `auto_play` split into generating speech and playing it in each layout. It used to cover both
        // layouts, with `auto_play_face_to_face` as an extra condition for face-to-face mode.
        { root ->
            val tts = root.obj("tts")
            if (tts == null) root else {
                val autoPlay = tts.boolean("auto_play") ?: false
                val faceToFace = tts.boolean("auto_play_face_to_face") ?: false
                val migrated = JsonObject(
                    tts - "auto_play" +
                        ("auto_generate" to JsonPrimitive(autoPlay)) +
                        ("auto_play_portrait" to JsonPrimitive(autoPlay)) +
                        ("auto_play_face_to_face" to JsonPrimitive(autoPlay && faceToFace)),
                )
                JsonObject(root + ("tts" to migrated))
            }
        },
        // 4 → 5: one custom voice per service became one per service and language. Copying the old voice to
        // every language offered in version 4 preserves the old behaviour while making each value editable.
        { root ->
            val tts = root.obj("tts")
            if (tts == null) root else {
                val version4Languages = listOf(
                    "en", "zh-CN", "ja", "ko", "zh-TW", "fr", "de", "es", "it", "pt", "ru", "ar", "th", "vi", "id",
                )
                val migratedVoices = buildJsonObject {
                    tts.obj("voices").orEmpty().forEach { (service, value) ->
                        val voice = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                        if (voice != null) putJsonObject(service) {
                            version4Languages.forEach { language -> put(language, voice) }
                        }
                    }
                }
                JsonObject(root + ("tts" to JsonObject(tts + ("voices" to migratedVoices))))
            }
        },
    )

    init {
        check(MIGRATIONS.size == VERSION - 1) { "Each version needs its migration step" }
    }

    /** Reads a document in the current layout. */
    private fun read(root: JsonObject): ServiceSettings {
        val providers = root.obj("providers")
        val dashScope = providers?.obj("dashscope")
        val volcengine = providers?.obj("volcengine")
        val miniMax = providers?.obj("minimax")
        val asr = root.obj("asr")
        val mockAsr = asr?.obj("mock")
        val tts = root.obj("tts")
        val mockTts = tts?.obj("mock")
        val serviceIds = asr?.get("services")?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val graceMs = (asr?.get("grace_ms") as? JsonPrimitive)?.longOrNull
            ?.coerceIn(0, ServiceSettings.MAX_ASR_GRACE_MS) ?: ServiceSettings.DEFAULT_ASR_GRACE_MS
        // Services this build doesn't know or can't run are left out; none left means the default.
        val services = serviceIds.mapNotNull { AsrService.find(it) }.distinct().ifEmpty { listOf(AsrService.Default) }
        val ttsService = when (val selected = tts?.get("service")) {
            null -> TtsService.Default
            JsonNull -> null
            is JsonPrimitive -> TtsService.find(selected.content) ?: TtsService.Default
            else -> TtsService.Default
        }
        val voices = tts?.obj("voices").orEmpty().mapNotNull { (service, value) ->
            (value as? JsonObject)?.mapNotNull { (language, voice) ->
                (voice as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { language to it }
            }?.toMap()?.let { service to it }
        }.toMap()
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
            openAiKey = providers?.obj("openai")?.string("key").orEmpty(),
            volcengineAppId = volcengine?.string("app_id").orEmpty(),
            volcengineAccessToken = volcengine?.string("access_token").orEmpty(),
            miniMaxRegion = MiniMaxRegion.fromName(miniMax?.string("region")),
            miniMaxKey = miniMax?.string("key").orEmpty(),
            mockAsrSpeed = MockSpeed.fromName(mockAsr?.string("speed")),
            mockAsrFault = MockAsrFault.fromName(mockAsr?.string("fault")),
            ttsService = ttsService,
            ttsAutoGenerate = tts?.boolean("auto_generate") ?: false,
            ttsAutoPlayPortrait = tts?.boolean("auto_play_portrait") ?: false,
            ttsAutoPlayFaceToFace = tts?.boolean("auto_play_face_to_face") ?: false,
            ttsVoices = voices,
            mockTtsSpeed = MockSpeed.fromName(mockTts?.string("speed")),
            mockTtsFault = MockTtsFault.fromName(mockTts?.string("fault")),
            translationPresets = presets,
        ).selectTranslation(presets.firstOrNull { it.id == selectedId } ?: presets.firstOrNull())
    }

    private const val TYPE_OPENAI_COMPATIBLE = "openai-compatible"
    private const val TYPE_MOCK = "mock"

    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
}

class ConfigFileException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NotAConfig, TooNew }
}
