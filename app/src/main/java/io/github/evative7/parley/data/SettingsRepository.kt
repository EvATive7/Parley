package io.github.evative7.parley.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.evative7.parley.BuildConfig
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.mock.MockAsrFault
import io.github.evative7.parley.mock.MockSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serviceDataStore by preferencesDataStore("services")

/** The settings as one encrypted [ConfigFile] document. */
private val ConfigKey = stringPreferencesKey("config_encrypted")
private const val CONFIG_FIELD = "config"

/** Where settings lived before they were one [ConfigFile] document; read once to migrate, then removed. */
private object LegacyKeys {
    val asrService = stringPreferencesKey("asr_service")
    val dashScopeRegion = stringPreferencesKey("dashscope_region")
    val dashScopeKey = stringPreferencesKey("dashscope_key_encrypted")
    val openAiAsrKey = stringPreferencesKey("openai_asr_key_encrypted")
    val volcAppId = stringPreferencesKey("volc_app_id")
    val volcAccessToken = stringPreferencesKey("volc_access_token_encrypted")
    val mockAsrSpeed = stringPreferencesKey("mock_asr_speed")
    val mockAsrFault = stringPreferencesKey("mock_asr_fault")
    val llmUrl = stringPreferencesKey("llm_url")
    val llmModel = stringPreferencesKey("llm_model")
    val llmKey = stringPreferencesKey("llm_key_encrypted")
    val translationPresets = stringPreferencesKey("translation_presets")
    val translationPresetId = stringPreferencesKey("translation_preset_id")

    // M2 keys: a single DashScope endpoint, from before ASR services could be picked.
    val m2AsrUrl = stringPreferencesKey("asr_url")
    val m2AsrModel = stringPreferencesKey("asr_model")
    val m2AsrKey = stringPreferencesKey("asr_key_encrypted")

    val all = listOf(
        asrService, dashScopeRegion, dashScopeKey, openAiAsrKey, volcAppId, volcAccessToken, mockAsrSpeed,
        mockAsrFault, llmUrl, llmModel, llmKey, translationPresets, translationPresetId, m2AsrUrl, m2AsrModel, m2AsrKey,
    )
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val vault: KeyVault,
) : ServiceSettingsSource {
    private val store = context.serviceDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings = flow {
        migrateLegacySettings()
        emitAll(store.data.map(::decode))
    }.retryWhen { _, _ ->
        // Keep observing after transient disk/keystore errors; never silently reset credentials.
        emit(ServiceSettings(storageError = true))
        delay(1_000)
        true
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val current: ServiceSettings? get() = settings.value

    private fun decode(p: Preferences): ServiceSettings {
        val stored = p[ConfigKey]
        return when {
            stored != null -> try {
                ConfigFile.decode(vault.decrypt(stored, CONFIG_FIELD))
            } catch (_: Exception) {
                ServiceSettings(storageError = true)
            }
            // Not migrated because a key couldn't be decrypted: shown as far as readable until the next save.
            LegacyKeys.all.any { it in p } -> readLegacy(p)
            else -> ServiceSettings()
        }
    }

    /** Moves settings saved by earlier versions into one document, unless some key of theirs is unreadable. */
    private suspend fun migrateLegacySettings() {
        store.edit { p ->
            if (ConfigKey in p || LegacyKeys.all.none { it in p }) return@edit
            val legacy = readLegacy(p)
            if (legacy.storageError) return@edit
            p[ConfigKey] = vault.encrypt(ConfigFile.encode(legacy, BuildConfig.VERSION_NAME), CONFIG_FIELD)
            LegacyKeys.all.forEach { p.remove(it) }
        }
    }

    private fun readLegacy(p: Preferences): ServiceSettings {
        val defaults = ServiceSettings()
        var unreadable = false
        fun decrypt(value: String, field: String): String = try {
            vault.decrypt(value, field)
        } catch (_: Exception) {
            unreadable = true
            ""
        }
        val translationUrl = p[LegacyKeys.llmUrl] ?: defaults.translationUrl
        val translationModel = p[LegacyKeys.llmModel] ?: defaults.translationModel
        val translationKey = decrypt(p[LegacyKeys.llmKey].orEmpty(), LegacyKeys.llmKey.name)
        val storedPresets = p[LegacyKeys.translationPresets]
        val (presets, presetId) = when {
            storedPresets != null -> try {
                decodePresets(storedPresets, ::decrypt) to p[LegacyKeys.translationPresetId]
            } catch (_: Exception) {
                unreadable = true
                emptyList<TranslationPreset>() to null
            }
            // Saved before presets existed: the one configured service becomes the first preset.
            p[LegacyKeys.llmUrl] != null -> listOf(
                TranslationPreset(MIGRATED_PRESET_ID, translationModel, translationUrl, translationModel, translationKey),
            ) to MIGRATED_PRESET_ID
            else -> emptyList<TranslationPreset>() to null
        }
        // Saved before ASR services could be picked: the one DashScope endpoint.
        val m2 = if (p[LegacyKeys.asrService] == null) {
            migrateLegacyAsr(p[LegacyKeys.m2AsrUrl].orEmpty(), p[LegacyKeys.m2AsrKey].orEmpty(), ::decrypt)
        } else null
        return ServiceSettings(
            asrServices = listOf(AsrService.fromId(p[LegacyKeys.asrService])),
            dashScopeRegion = m2?.region ?: DashScopeRegion.fromName(p[LegacyKeys.dashScopeRegion]),
            dashScopeKey = m2?.key ?: decrypt(p[LegacyKeys.dashScopeKey].orEmpty(), LegacyKeys.dashScopeKey.name),
            openAiKey = decrypt(p[LegacyKeys.openAiAsrKey].orEmpty(), LegacyKeys.openAiAsrKey.name),
            volcengineAppId = p[LegacyKeys.volcAppId].orEmpty(),
            volcengineAccessToken = decrypt(p[LegacyKeys.volcAccessToken].orEmpty(), LegacyKeys.volcAccessToken.name),
            mockAsrSpeed = MockSpeed.fromName(p[LegacyKeys.mockAsrSpeed]),
            mockAsrFault = MockAsrFault.fromName(p[LegacyKeys.mockAsrFault]),
            translationUrl = translationUrl,
            translationModel = translationModel,
            translationKey = translationKey,
            translationPresets = presets,
            translationPresetId = presetId?.takeIf { id -> presets.any { it.id == id } },
            storageError = unreadable,
        ).let { it.selectTranslation(it.selectedTranslation ?: presets.firstOrNull()) }
    }

    suspend fun save(value: ServiceSettings) = withContext(Dispatchers.IO) {
        require(value.validationError() == null)
        val encrypted = vault.encrypt(ConfigFile.encode(value, BuildConfig.VERSION_NAME), CONFIG_FIELD)
        store.edit { p ->
            p[ConfigKey] = encrypted
            LegacyKeys.all.forEach { p.remove(it) }
        }
    }
}

private const val MIGRATED_PRESET_ID = "migrated"

internal data class LegacyAsrMigration(val region: DashScopeRegion, val key: String)

internal fun migrateLegacyAsr(
    oldUrl: String,
    oldCiphertext: String,
    decrypt: (ciphertext: String, field: String) -> String,
): LegacyAsrMigration = LegacyAsrMigration(
    region = if ("-intl" in oldUrl) DashScopeRegion.Singapore else DashScopeRegion.Beijing,
    key = decrypt(oldCiphertext, "asr_key_encrypted"),
)
