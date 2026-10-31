package io.github.evative7.parley.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
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

private object SettingsKeys {
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

    // M2 keys, removed after migration.
    val legacyAsrUrl = stringPreferencesKey("asr_url")
    val legacyAsrModel = stringPreferencesKey("asr_model")
    val legacyAsrKey = stringPreferencesKey("asr_key_encrypted")
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

    private fun decode(p: androidx.datastore.preferences.core.Preferences): ServiceSettings {
        val defaults = ServiceSettings()
        var unreadable = false
        fun decrypt(value: String, field: String): String = try {
            vault.decrypt(value, field)
        } catch (_: Exception) {
            unreadable = true
            ""
        }
        val translationUrl = p[SettingsKeys.llmUrl] ?: defaults.translationUrl
        val translationModel = p[SettingsKeys.llmModel] ?: defaults.translationModel
        val translationKey = decrypt(p[SettingsKeys.llmKey].orEmpty(), SettingsKeys.llmKey.name)
        val storedPresets = p[SettingsKeys.translationPresets]
        val (presets, presetId) = when {
            storedPresets != null -> try {
                decodePresets(storedPresets, ::decrypt) to p[SettingsKeys.translationPresetId]
            } catch (_: Exception) {
                unreadable = true
                emptyList<TranslationPreset>() to null
            }
            // Saved before presets existed: the one configured service becomes the first preset.
            p[SettingsKeys.llmUrl] != null -> listOf(
                TranslationPreset(MIGRATED_PRESET_ID, translationModel, translationUrl, translationModel, translationKey),
            ) to MIGRATED_PRESET_ID
            else -> emptyList<TranslationPreset>() to null
        }
        return ServiceSettings(
            asrService = AsrService.fromId(p[SettingsKeys.asrService]),
            dashScopeRegion = DashScopeRegion.fromName(p[SettingsKeys.dashScopeRegion]),
            dashScopeKey = decrypt(p[SettingsKeys.dashScopeKey].orEmpty(), SettingsKeys.dashScopeKey.name),
            openAiAsrKey = decrypt(p[SettingsKeys.openAiAsrKey].orEmpty(), SettingsKeys.openAiAsrKey.name),
            volcAppId = p[SettingsKeys.volcAppId].orEmpty(),
            volcAccessToken = decrypt(p[SettingsKeys.volcAccessToken].orEmpty(), SettingsKeys.volcAccessToken.name),
            mockAsrSpeed = MockSpeed.fromName(p[SettingsKeys.mockAsrSpeed]),
            mockAsrFault = MockAsrFault.fromName(p[SettingsKeys.mockAsrFault]),
            translationUrl = translationUrl,
            translationModel = translationModel,
            translationKey = translationKey,
            translationPresets = presets,
            translationPresetId = presetId?.takeIf { id -> presets.any { it.id == id } },
            storageError = unreadable,
        )
    }

    private suspend fun migrateLegacySettings() {
        store.edit { p ->
            if (p[SettingsKeys.asrService] != null) return@edit
            val migrated = migrateLegacyAsr(
                p[SettingsKeys.legacyAsrUrl].orEmpty(),
                p[SettingsKeys.legacyAsrKey].orEmpty(),
            ) { ciphertext, field -> vault.decrypt(ciphertext, field) }
            p[SettingsKeys.asrService] = AsrService.Default.id
            p[SettingsKeys.dashScopeRegion] = migrated.region.name
            p[SettingsKeys.dashScopeKey] = vault.encrypt(migrated.key, SettingsKeys.dashScopeKey.name)
            p.remove(SettingsKeys.legacyAsrUrl)
            p.remove(SettingsKeys.legacyAsrModel)
            p.remove(SettingsKeys.legacyAsrKey)
        }
    }

    suspend fun save(value: ServiceSettings) = withContext(Dispatchers.IO) {
        require(value.validationError() == null)
        val encryptedDashScope = vault.encrypt(value.dashScopeKey, SettingsKeys.dashScopeKey.name)
        val encryptedOpenAi = vault.encrypt(value.openAiAsrKey, SettingsKeys.openAiAsrKey.name)
        val encryptedVolc = vault.encrypt(value.volcAccessToken, SettingsKeys.volcAccessToken.name)
        val encryptedLlm = vault.encrypt(value.translationKey, SettingsKeys.llmKey.name)
        val encodedPresets = encodePresets(value.translationPresets, vault::encrypt)
        store.edit { p ->
            p[SettingsKeys.asrService] = value.asrService.id
            p[SettingsKeys.dashScopeRegion] = value.dashScopeRegion.name
            p[SettingsKeys.dashScopeKey] = encryptedDashScope
            p[SettingsKeys.openAiAsrKey] = encryptedOpenAi
            p[SettingsKeys.volcAppId] = value.volcAppId
            p[SettingsKeys.volcAccessToken] = encryptedVolc
            p[SettingsKeys.mockAsrSpeed] = value.mockAsrSpeed.name
            p[SettingsKeys.mockAsrFault] = value.mockAsrFault.name
            p[SettingsKeys.llmUrl] = value.translationUrl.trimEnd('/')
            p[SettingsKeys.llmModel] = value.translationModel
            p[SettingsKeys.llmKey] = encryptedLlm
            p[SettingsKeys.translationPresets] = encodedPresets
            val presetId = value.translationPresetId
            if (presetId != null) p[SettingsKeys.translationPresetId] = presetId else p.remove(SettingsKeys.translationPresetId)
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
