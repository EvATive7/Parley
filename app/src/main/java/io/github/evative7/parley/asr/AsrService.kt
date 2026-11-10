package io.github.evative7.parley.asr

import io.github.evative7.parley.BuildConfig
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.model.LanguageSupport

enum class AsrProvider {
    DashScope, OpenAi, Volcengine, MiniMax,

    /** Offline stand-in for trying the app without keys; debug builds only. */
    Mock;

    companion object {
        /** Providers with at least one ASR service in this build. */
        val available: List<AsrProvider> = entries.filter {
            it != MiniMax && (it != Mock || BuildConfig.DEBUG)
        }
    }
}

enum class AsrService(
    val id: String,
    val provider: AsrProvider,
    val streaming: Boolean,
    val experimental: Boolean = false,
    /** Switched off for now: not offered, and dropped from saved settings. */
    val disabled: Boolean = false,
) {
    FunAsrRealtimeStream(
        "fun-asr-realtime-stream-input",
        AsrProvider.DashScope,
        streaming = true,
    ),
    FunAsrRealtimeNonStream(
        "fun-asr-realtime-non-stream-input",
        AsrProvider.DashScope,
        streaming = false,
    ),
    GptRealtimeWhisper(
        "gpt-realtime-whisper",
        AsrProvider.OpenAi,
        streaming = true,
        experimental = true,
        // Temporarily off.
        disabled = true,
    ),
    Gpt4oTranscribeNonStream(
        "gpt-4o-transcribe-non-stream-input",
        AsrProvider.OpenAi,
        streaming = false,
    ),
    Gpt4oMiniTranscribeNonStream(
        "gpt-4o-mini-transcribe-non-stream-input",
        AsrProvider.OpenAi,
        streaming = false,
    ),
    DoubaoSeedAsr20Stream(
        "doubao-seed-asr-2-0-stream-input",
        AsrProvider.Volcengine,
        streaming = true,
    ),
    DoubaoSeedAsr20NonStream(
        "doubao-seed-asr-2-0-non-stream-input",
        AsrProvider.Volcengine,
        streaming = false,
    ),
    DoubaoSeedAsr20NonStreamTurbo(
        "doubao-seed-asr-2-0-non-stream-input-turbo",
        AsrProvider.Volcengine,
        streaming = false,
    ),
    MockStream("mock-stream", AsrProvider.Mock, streaming = true),
    MockNonStream("mock-non-stream", AsrProvider.Mock, streaming = false);

    val languageSupport: LanguageSupport get() = when (this) {
        // https://www.alibabacloud.com/help/en/model-studio/fun-asr-client-events
        // Assumed: the undated alias, not the 2026-02-28 snapshot limited to zh/en/ja.
        FunAsrRealtimeStream, FunAsrRealtimeNonStream -> LanguageSupport(
            codes("zh en ja ko vi th id ms tl hi ar fr de es pt ru it nl sv da fi no el pl cs hu ro bg hr sk"),
            listOf("Cantonese", "Sichuanese", "other Chinese dialects"),
        )
        // https://developers.openai.com/api/docs/guides/speech-to-text
        // Assumed for all three: Whisper's officially listed languages.
        GptRealtimeWhisper, Gpt4oTranscribeNonStream, Gpt4oMiniTranscribeNonStream -> LanguageSupport(
            codes("af ar hy az be bs bg ca zh hr cs da nl en et fi fr gl de el he hi hu is id it ja kn kk ko lv lt mk ms mr mi ne no fa pl pt ro ru sr sk sl es sw sv tl ta th tr uk ur vi cy"),
        )
        // https://docs.volcengine.com/docs/6561/1354869
        DoubaoSeedAsr20Stream -> LanguageSupport(
            codes("zh en"),
            listOf("Shanghainese", "Minnan", "Sichuanese", "Shaanxi", "Cantonese", "other Chinese dialects"),
        )
        // https://docs.volcengine.com/docs/6561/1354868 (Turbo assumed identical).
        DoubaoSeedAsr20NonStream, DoubaoSeedAsr20NonStreamTurbo -> LanguageSupport(
            codes("zh en ja id es pt de fr ko fil ms th ar it bn el nl ru tr vi pl ro ne uk"),
            listOf("Cantonese"),
        )
        MockStream, MockNonStream -> LanguageSupport(Lang.targets.map { it.base }.toSet())
    }

    fun supports(lang: Lang): Boolean = lang.base in languageSupport.languages

    companion object {
        val Default = FunAsrRealtimeStream

        /** What can be picked in this build. */
        val available: List<AsrService> = entries.filter { it.provider in AsrProvider.available && !it.disabled }

        fun fromId(id: String?): AsrService = find(id) ?: Default

        /** The service with [id] if this build can run it. */
        fun find(id: String?): AsrService? = available.firstOrNull { it.id == id }

        private fun codes(value: String): Set<String> = value.split(' ').toSet()
    }
}

enum class DashScopeRegion(val host: String) {
    Beijing("dashscope.aliyuncs.com"),
    Singapore("dashscope-intl.aliyuncs.com");

    companion object {
        fun fromName(value: String?): DashScopeRegion = entries.firstOrNull { it.name == value } ?: Beijing
    }
}
