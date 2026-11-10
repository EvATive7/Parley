package io.github.evative7.parley.tts

import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** Selects the device engine or the configured cloud synthesizer for a resolved speech key. */
@Singleton
class RoutingTextToSpeaker @Inject constructor(
    private val system: SystemTextToSpeaker,
    mock: MockSynthesizer,
    player: PcmPlayer,
    cache: SpeechCache,
    client: OkHttpClient,
    settings: ServiceSettingsSource,
) : TextToSpeaker {
    private val cloud = mapOf(
        TtsService.CosyVoice35Flash to CloudTextToSpeaker(
            CosyVoiceSynthesizer(client, settings, TtsService.CosyVoice35Flash), player, cache,
        ),
        TtsService.CosyVoice35Plus to CloudTextToSpeaker(
            CosyVoiceSynthesizer(client, settings, TtsService.CosyVoice35Plus), player, cache,
        ),
        TtsService.Mock to CloudTextToSpeaker(mock, player, cache),
    )

    override fun speak(key: SpeechKey, text: String, lang: Lang): Flow<PlaybackState> = when (key.service) {
        TtsService.System -> system.speak(key, text, lang)
        else -> cloud[key.service]?.speak(key, text, lang)
            ?: flowOf(PlaybackState.Failed("Speech service is not available"))
    }

    override fun stop() {
        system.stop()
        cloud.values.forEach(TextToSpeaker::stop)
    }
}
