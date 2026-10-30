package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.asr.DashScopeRegion
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Keys are plaintext only in memory; persistence is handled by KeyVault. */
data class ServiceSettings(
    val asrService: AsrService = AsrService.Default,
    val dashScopeRegion: DashScopeRegion = DashScopeRegion.Beijing,
    val dashScopeKey: String = "",
    val openAiAsrKey: String = "",
    val volcAppId: String = "",
    val volcAccessToken: String = "",
    val translationUrl: String = "https://api.openai.com/v1",
    val translationModel: String = "gpt-4o-mini",
    val translationKey: String = "",
    val storageError: Boolean = false,
) {
    val isReady: Boolean get() = !storageError && validationError() == null

    /** Every invalid field, in screen order. Only the selected ASR provider is required. */
    fun validationErrors(): List<SettingsError> = buildList {
        when (asrService.provider) {
            AsrProvider.DashScope -> if (!validSecret(dashScopeKey)) add(SettingsError.AsrKey)
            AsrProvider.OpenAi -> if (!validSecret(openAiAsrKey)) add(SettingsError.AsrKey)
            AsrProvider.Volcengine -> {
                if (!validValue(volcAppId)) add(SettingsError.AsrAppId)
                if (!validSecret(volcAccessToken)) add(SettingsError.AsrKey)
            }
        }
        if (!secureUrl(translationUrl)) add(SettingsError.TranslationUrl)
        if (translationModel.isBlank()) add(SettingsError.TranslationModel)
        if (!validSecret(translationKey)) add(SettingsError.TranslationKey)
    }

    fun validationError(): SettingsError? = validationErrors().firstOrNull()

    private fun validValue(value: String) = value.isNotBlank() && value.none { it.isISOControl() }
    private fun validSecret(value: String) = validValue(value)

    // Avoid leaking credentials if a settings object is accidentally logged.
    override fun toString() = "ServiceSettings(asrService=${asrService.id}, dashScopeRegion=$dashScopeRegion, credentials=<redacted>)"

    companion object {
        private fun secureUrl(value: String): Boolean {
            if (!value.startsWith("https://")) return false
            val url = value.toHttpUrlOrNull() ?: return false
            return url.username.isEmpty() && url.password.isEmpty() &&
                url.query == null && url.fragment == null
        }
    }
}

enum class SettingsError { AsrKey, AsrAppId, TranslationUrl, TranslationModel, TranslationKey }

interface ServiceSettingsSource {
    val current: ServiceSettings?
}
