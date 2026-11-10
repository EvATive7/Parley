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

    /** What the provider documents; Parley sends no language hint, so this is what auto-detection covers. */
    val languageSupport: LanguageSupport get() = when (this) {
        // https://www.alibabacloud.com/help/en/model-studio/fun-asr-client-events
        // Assumed: the undated alias, not the 2026-02-28 snapshot limited to zh/en/ja. Dialects per the model
        // overview ("multi-region dialect coverage").
        FunAsrRealtimeStream, FunAsrRealtimeNonStream -> LanguageSupport.of(
            "zh en ja ko vi th id ms tl hi ar fr de es pt ru it yue wuu nan hak gan hsn nl sv da fi no el pl cs hu ro bg hr sk",
        )
        // https://developers.openai.com/api/docs/guides/speech-to-text
        // Assumed for all three: Whisper's officially listed languages.
        GptRealtimeWhisper, Gpt4oTranscribeNonStream, Gpt4oMiniTranscribeNonStream -> LanguageSupport.of(
            "en zh ja ko fr de es it pt ru ar th vi id af hy az be bs bg ca hr cs da nl et fi gl el he hi hu is " +
                "kn kk lv lt mk ms mr mi ne no fa pl ro sr sk sl sw sv tl ta tr uk ur cy",
        )
        // https://docs.volcengine.com/docs/6561/1354869: without a `language`, which Parley never sends, only
        // Chinese, English and Chinese dialects.
        DoubaoSeedAsr20Stream -> LanguageSupport.of("zh en yue wuu nan")
        // https://docs.volcengine.com/docs/6561/1354868 (Turbo assumed identical).
        DoubaoSeedAsr20NonStream, DoubaoSeedAsr20NonStreamTurbo -> LanguageSupport.of(
            "zh en ja ko fr de es it pt ru ar th vi id yue fil ms bn el nl tr pl ro ne uk",
        )
        MockStream, MockNonStream -> LanguageSupport(Lang.targets.map { it.base }.distinct())
    }

    fun supports(lang: Lang): Boolean = languageSupport.supports(lang)

    companion object {
        val Default = FunAsrRealtimeStream

        /** What can be picked in this build. */
        val available: List<AsrService> = entries.filter { it.provider in AsrProvider.available && !it.disabled }

        fun fromId(id: String?): AsrService = find(id) ?: Default

        /** The service with [id] if this build can run it. */
        fun find(id: String?): AsrService? = available.firstOrNull { it.id == id }

    }
}

enum class DashScopeRegion(val host: String) {
    Beijing("dashscope.aliyuncs.com"),
    Singapore("dashscope-intl.aliyuncs.com");

    companion object {
        fun fromName(value: String?): DashScopeRegion = entries.firstOrNull { it.name == value } ?: Beijing
    }
}
