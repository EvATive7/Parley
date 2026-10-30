package io.github.evative7.parley.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Keys are plaintext only in memory; persistence is handled by KeyVault. */
data class ServiceSettings(
    val demoMode: Boolean = false,
    val asrUrl: String = "wss://dashscope.aliyuncs.com/api-ws/v1/inference",
    val asrModel: String = "fun-asr-realtime",
    val asrKey: String = "",
    val translationUrl: String = "https://api.openai.com/v1",
    val translationModel: String = "gpt-4o-mini",
    val translationKey: String = "",
    val storageError: Boolean = false,
) {
    val isReady: Boolean get() = !storageError && (demoMode || validationError() == null)

    /** Every invalid field, in screen order. */
    fun validationErrors(): List<SettingsError> = buildList {
        if (!secureUrl(asrUrl, websocket = true)) add(SettingsError.AsrUrl)
        if (asrModel.isBlank()) add(SettingsError.AsrModel)
        if (!validKey(asrKey)) add(SettingsError.AsrKey)
        if (!secureUrl(translationUrl, websocket = false)) add(SettingsError.TranslationUrl)
        if (translationModel.isBlank()) add(SettingsError.TranslationModel)
        if (!validKey(translationKey)) add(SettingsError.TranslationKey)
    }

    fun validationError(): SettingsError? = validationErrors().firstOrNull()

    private fun validKey(key: String) = (demoMode || key.isNotBlank()) && key.none { it.isISOControl() }

    // Avoid leaking secrets if a settings object is accidentally logged.
    override fun toString() = "ServiceSettings(demoMode=$demoMode, keys=<redacted>)"

    companion object {
        private fun secureUrl(value: String, websocket: Boolean): Boolean {
            val prefix = if (websocket) "wss://" else "https://"
            if (!value.startsWith(prefix)) return false
            val url = value.replaceFirst("wss://", "https://").toHttpUrlOrNull() ?: return false
            return url.username.isEmpty() && url.password.isEmpty() &&
                url.query == null && url.fragment == null
        }
    }
}

enum class SettingsError { AsrUrl, AsrModel, AsrKey, TranslationUrl, TranslationModel, TranslationKey }

interface ServiceSettingsSource {
    val current: ServiceSettings?
}
