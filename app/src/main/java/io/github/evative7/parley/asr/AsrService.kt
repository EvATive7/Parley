package io.github.evative7.parley.asr

import io.github.evative7.parley.BuildConfig

enum class AsrProvider {
    DashScope, OpenAi, Volcengine,

    /** Offline stand-in for trying the app without keys; debug builds only. */
    Mock;

    companion object {
        val available: List<AsrProvider> = entries.filter { it != Mock || BuildConfig.DEBUG }
    }
}

enum class AsrService(
    val id: String,
    val provider: AsrProvider,
    val streaming: Boolean,
    val experimental: Boolean = false,
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

    companion object {
        val Default = FunAsrRealtimeStream

        /** What can be picked in this build. */
        val available: List<AsrService> = entries.filter { it.provider in AsrProvider.available }

        fun fromId(id: String?): AsrService = available.firstOrNull { it.id == id } ?: Default
    }
}

enum class DashScopeRegion(val host: String) {
    Beijing("dashscope.aliyuncs.com"),
    Singapore("dashscope-intl.aliyuncs.com");

    companion object {
        fun fromName(value: String?): DashScopeRegion = entries.firstOrNull { it.name == value } ?: Beijing
    }
}
