package io.github.evative7.parley.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serviceDataStore by preferencesDataStore("services")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val vault: KeyVault,
) : ServiceSettingsSource {
    private val store = context.serviceDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val asrUrl = stringPreferencesKey("asr_url")
    private val asrModel = stringPreferencesKey("asr_model")
    private val asrKey = stringPreferencesKey("asr_key_encrypted")
    private val llmUrl = stringPreferencesKey("llm_url")
    private val llmModel = stringPreferencesKey("llm_model")
    private val llmKey = stringPreferencesKey("llm_key_encrypted")

    val settings = store.data.map { p ->
        val defaults = ServiceSettings()
        var unreadable = false
        fun decrypt(value: String, field: String): String = try {
            vault.decrypt(value, field)
        } catch (_: Exception) {
            unreadable = true
            ""
        }
        val speechKey = decrypt(p[asrKey].orEmpty(), asrKey.name)
        val translationKey = decrypt(p[llmKey].orEmpty(), llmKey.name)
        ServiceSettings(
            asrUrl = p[asrUrl] ?: defaults.asrUrl,
            asrModel = p[asrModel] ?: defaults.asrModel,
            asrKey = speechKey,
            translationUrl = p[llmUrl] ?: defaults.translationUrl,
            translationModel = p[llmModel] ?: defaults.translationModel,
            translationKey = translationKey,
            storageError = unreadable,
        )
    }.retryWhen { _, _ ->
        // Keep observing after transient disk errors; never silently reset stored settings.
        emit(ServiceSettings(storageError = true))
        delay(1_000)
        true
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val current: ServiceSettings? get() = settings.value

    suspend fun save(value: ServiceSettings) = withContext(Dispatchers.IO) {
        require(value.validationError() == null)
        val encryptedAsr = vault.encrypt(value.asrKey, asrKey.name)
        val encryptedLlm = vault.encrypt(value.translationKey, llmKey.name)
        store.edit { p ->
            p[asrUrl] = value.asrUrl
            p[asrModel] = value.asrModel
            p[asrKey] = encryptedAsr
            p[llmUrl] = value.translationUrl.trimEnd('/')
            p[llmModel] = value.translationModel
            p[llmKey] = encryptedLlm
        }
    }
}
