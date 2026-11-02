package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.mock.MockAsrFault
import io.github.evative7.parley.mock.MockSpeed
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Keys are plaintext only in memory; persistence is handled by KeyVault. */
data class ServiceSettings(
    /**
     * Every recognizer that hears each press, all at once. The fastest answer goes on the card; the others
     * help the translator. The first streaming one shows the words while the user speaks.
     */
    val asrServices: List<AsrService> = listOf(AsrService.Default),
    /**
     * With several services, how long the others may still answer once the first has, for the translator to
     * compare; later answers are only shown.
     */
    val asrGraceMs: Long = DEFAULT_ASR_GRACE_MS,
    val dashScopeRegion: DashScopeRegion = DashScopeRegion.Beijing,
    val dashScopeKey: String = "",
    val openAiAsrKey: String = "",
    val volcAppId: String = "",
    val volcAccessToken: String = "",
    /** Used by the mock recognizer only. */
    val mockAsrSpeed: MockSpeed = MockSpeed.Normal,
    val mockAsrFault: MockAsrFault = MockAsrFault.None,
    // The active translation service, copied from the selected preset. The translator reads only these.
    val translationUrl: String = "https://api.openai.com/v1",
    val translationModel: String = "gpt-4o-mini",
    val translationKey: String = "",
    /** Saved translation services, in the order they were added. */
    val translationPresets: List<TranslationPreset> = emptyList(),
    val translationPresetId: String? = null,
    val storageError: Boolean = false,
) {
    val isReady: Boolean get() = !storageError && validationError() == null

    val selectedTranslation: TranslationPreset?
        get() = translationPresets.firstOrNull { it.id == translationPresetId }

    /** Makes [preset] the active translation service; `null` leaves none active. */
    fun selectTranslation(preset: TranslationPreset?) = copy(
        translationPresetId = preset?.id,
        translationUrl = preset?.url ?: ServiceSettings().translationUrl,
        translationModel = preset?.model ?: "",
        translationKey = preset?.key ?: "",
    )

    /** Adds [preset] or replaces the one with its id. A new one, or an edit of the active one, becomes active. */
    fun putTranslationPreset(preset: TranslationPreset): ServiceSettings {
        val exists = translationPresets.any { it.id == preset.id }
        val next = copy(
            translationPresets = if (exists) {
                translationPresets.map { if (it.id == preset.id) preset else it }
            } else translationPresets + preset,
        )
        return if (!exists || preset.id == translationPresetId) next.selectTranslation(preset) else next
    }

    /** Removing the active service activates the first remaining one, if any. */
    fun removeTranslationPreset(id: String): ServiceSettings {
        val next = copy(translationPresets = translationPresets.filterNot { it.id == id })
        return if (id == translationPresetId) next.selectTranslation(next.translationPresets.firstOrNull()) else next
    }

    /** The providers of [asrServices], each once, in the order they are listed in settings. */
    val asrProviders: List<AsrProvider>
        get() = AsrProvider.entries.filter { p -> asrServices.any { it.provider == p } }

    /** Every invalid field, in screen order. Only the selected ASR providers are required. */
    fun validationErrors(): List<SettingsError> = buildList {
        if (asrServices.isEmpty()) add(SettingsError.AsrNone)
        for (provider in asrProviders) addAll(asrErrors(provider))
        // The mock translator needs no endpoint.
        if (selectedTranslation?.mock == true) return@buildList
        if (!secureUrl(translationUrl)) add(SettingsError.TranslationUrl)
        if (translationModel.isBlank()) add(SettingsError.TranslationModel)
        if (!validSecret(translationKey)) add(SettingsError.TranslationKey)
    }

    fun validationError(): SettingsError? = validationErrors().firstOrNull()

    /** The invalid credentials of one ASR provider, in screen order. */
    fun asrErrors(provider: AsrProvider): List<SettingsError> = buildList {
        when (provider) {
            AsrProvider.DashScope -> if (!validSecret(dashScopeKey)) add(SettingsError.AsrKey)
            AsrProvider.OpenAi -> if (!validSecret(openAiAsrKey)) add(SettingsError.AsrKey)
            AsrProvider.Volcengine -> {
                if (!validValue(volcAppId)) add(SettingsError.AsrAppId)
                if (!validSecret(volcAccessToken)) add(SettingsError.AsrKey)
            }
            AsrProvider.Mock -> Unit
        }
    }

    private fun validValue(value: String) = value.isNotBlank() && value.none { it.isISOControl() }
    private fun validSecret(value: String) = validValue(value)

    // Avoid leaking credentials if a settings object is accidentally logged.
    override fun toString() = "ServiceSettings(asrServices=${asrServices.joinToString { it.id }}, dashScopeRegion=$dashScopeRegion, credentials=<redacted>)"

    companion object {
        const val DEFAULT_ASR_GRACE_MS = 1_500L
        const val MAX_ASR_GRACE_MS = 5_000L

        internal fun secureUrl(value: String): Boolean {
            if (!value.startsWith("https://")) return false
            val url = value.toHttpUrlOrNull() ?: return false
            return url.username.isEmpty() && url.password.isEmpty() &&
                url.query == null && url.fragment == null
        }
    }
}

enum class SettingsError { AsrNone, AsrKey, AsrAppId, TranslationUrl, TranslationModel, TranslationKey, PresetName }

interface ServiceSettingsSource {
    val current: ServiceSettings?
}
