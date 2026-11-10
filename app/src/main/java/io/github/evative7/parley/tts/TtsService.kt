package io.github.evative7.parley.tts

import io.github.evative7.parley.BuildConfig
import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.model.Lang

enum class TtsService(
    val id: String,
    val provider: AsrProvider?,
    /** Switched off for now: not offered, and dropped from saved settings. */
    val disabled: Boolean = false,
) {
    System("system", null),
    CosyVoice35Flash("cosyvoice-v3.5-flash", AsrProvider.DashScope),
    CosyVoice35Plus("cosyvoice-v3.5-plus", AsrProvider.DashScope),
    DoubaoSeedTts20("doubao-seed-tts-2.0", AsrProvider.Volcengine),
    MiniMaxSpeech28Turbo("minimax-speech-2.8-turbo", AsrProvider.MiniMax),
    MiniMaxSpeech28Hd("minimax-speech-2.8-hd", AsrProvider.MiniMax),
    Mock("mock", AsrProvider.Mock);

    fun defaultVoice(lang: Lang): String? = when (this) {
        System -> "system"
        CosyVoice35Flash, CosyVoice35Plus -> when (lang.tag) {
            "en", "zh-CN" -> "longanyang"
            else -> null
        }
        DoubaoSeedTts20 -> when (lang.tag) {
            "en", "zh-CN" -> "zh_female_gaolengyujie_uranus_bigtts"
            else -> null
        }
        MiniMaxSpeech28Turbo, MiniMaxSpeech28Hd -> when (lang.tag) {
            "en" -> "English_expressive_narrator"
            "zh-CN", "zh-TW" -> "Chinese (Mandarin)_Reliable_Executive"
            "ja" -> "Japanese_IntellectualSenior"
            "ko" -> "Korean_CalmGentleman"
            "fr" -> "French_Male_Speech_New"
            "de" -> "German_FriendlyMan"
            "es" -> "Spanish_Narrator"
            "it" -> "Italian_Narrator"
            "pt" -> "Portuguese_Narrator"
            "ru" -> "Russian_ReliableMan"
            "ar" -> "Arabic_FriendlyGuy"
            "th" -> "Thai_male_1_sample8"
            "vi" -> "Vietnamese_kindhearted_girl"
            "id" -> "Indonesian_CaringMan"
            else -> null
        }
        Mock -> "mock"
    }

    companion object {
        val Default = CosyVoice35Flash

        /** What can be picked in this build. */
        val available: List<TtsService> = entries.filter { (it != Mock || BuildConfig.DEBUG) && !it.disabled }

        /** The service with [id] if this build can run it. */
        fun find(id: String?): TtsService? = available.firstOrNull { it.id == id }
    }
}

enum class MiniMaxRegion(val host: String) {
    China("api.minimaxi.com"),
    International("api.minimax.io");

    companion object {
        fun fromName(value: String?): MiniMaxRegion = entries.firstOrNull { it.name == value } ?: China
    }
}
