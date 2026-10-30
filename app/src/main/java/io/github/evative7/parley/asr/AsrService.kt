package io.github.evative7.parley.asr

enum class AsrProvider { DashScope, OpenAi, Volcengine }

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
    );

    companion object {
        val Default = FunAsrRealtimeStream

        fun fromId(id: String?): AsrService = entries.firstOrNull { it.id == id } ?: Default
    }
}

enum class DashScopeRegion(val host: String) {
    Beijing("dashscope.aliyuncs.com"),
    Singapore("dashscope-intl.aliyuncs.com");

    companion object {
        fun fromName(value: String?): DashScopeRegion = entries.firstOrNull { it.name == value } ?: Beijing
    }
}
