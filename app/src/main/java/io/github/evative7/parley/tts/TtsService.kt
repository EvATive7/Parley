package io.github.evative7.parley.tts

import io.github.evative7.parley.BuildConfig
import io.github.evative7.parley.asr.AsrProvider
import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.model.Lang

enum class TtsService(
    val id: String,
    val provider: AsrProvider?,
    /** Switched off for now: not offered, and dropped from saved settings. */
    val disabled: Boolean = false,
) {
    System("system", null),
    QwenAudioTtsFlash("qwen-audio-3.1-tts-flash", AsrProvider.DashScope),
    CosyVoiceV3Flash("cosyvoice-v3-flash", AsrProvider.DashScope),
    DoubaoSeedTts20("doubao-seed-tts-2.0", AsrProvider.Volcengine),
    MiniMaxSpeech28Turbo("minimax-speech-2.8-turbo", AsrProvider.MiniMax),
    MiniMaxSpeech28Hd("minimax-speech-2.8-hd", AsrProvider.MiniMax),
    Mock("mock", AsrProvider.Mock);

    /**
     * The built-in voice for [lang], or null if this service has none for it; [region] matters where a
     * provider offers some voices in only one region. Voices were checked against each provider's voice list.
     */
    fun defaultVoice(lang: Lang, region: DashScopeRegion = DashScopeRegion.Beijing): String? = when (this) {
        System -> "system"
        // One voice speaks every language Parley offers, in both regions.
        QwenAudioTtsFlash -> if (lang in Lang.targets) "longanhuan_v3.1" else null
        CosyVoiceV3Flash -> when (lang.tag) {
            "zh-CN", "zh-TW", "en" -> "longanyang"
            "id" -> "loongindah_v3"
            "ja" -> "loongtomoka_v3".takeIf { region == DashScopeRegion.Beijing }
            "ko" -> "loongkyong_v3".takeIf { region == DashScopeRegion.Beijing }
            else -> null
        }
        DoubaoSeedTts20 -> when (lang.tag) {
            // Vivi reads Mandarin for either script.
            "zh-CN", "zh-TW" -> "zh_female_vv_uranus_bigtts"
            "en" -> "en_female_wenrouzhishijieshuonv_uranus_bigtts"
            "ja" -> "ja_female_bv521_uranus_bigtts"
            "ko" -> "ko_female_bv546_uranus_bigtts"
            "fr" -> "fr_female_fr_f47_uranus_bigtts"
            "de" -> "de_female_bv081_uranus_bigtts"
            "es" -> "es_female_romina_uranus_bigtts"
            // No Italian voice takes the streaming API; Vivi reads it.
            "it" -> "zh_female_vv_uranus_bigtts"
            "pt" -> "pt_female_bv530_uranus_bigtts"
            "ru" -> "ru_female_sophie_uranus_bigtts"
            "ar" -> "ar_female_dina_uranus_bigtts"
            "th" -> "th_female_elle_uranus_bigtts"
            "vi" -> "vi_female_linh_uranus_bigtts"
            "id" -> "id_female_cesna_uranus_bigtts"
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
        val Default = QwenAudioTtsFlash

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
