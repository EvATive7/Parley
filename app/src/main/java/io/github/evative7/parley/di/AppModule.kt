package io.github.evative7.parley.di

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.evative7.parley.asr.RoutingRecognizer
import io.github.evative7.parley.asr.SpeechRecognizer
import io.github.evative7.parley.audio.AudioRecorder
import io.github.evative7.parley.audio.AudioSource
import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.data.SettingsRepository
import io.github.evative7.parley.model.LanguageDetector
import io.github.evative7.parley.model.OfflineLanguageDetector
import io.github.evative7.parley.state.MonotonicClock
import io.github.evative7.parley.translate.OpenAiCompatibleTranslator
import io.github.evative7.parley.translate.Translator
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds @Singleton abstract fun recognizer(impl: RoutingRecognizer): SpeechRecognizer
    @Binds @Singleton abstract fun translator(impl: OpenAiCompatibleTranslator): Translator
    @Binds @Singleton abstract fun settings(impl: SettingsRepository): ServiceSettingsSource
    @Binds @Singleton abstract fun detector(impl: OfflineLanguageDetector): LanguageDetector
    @Binds abstract fun recorder(impl: AudioRecorder): AudioSource
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        // DashScope tasks do not require client heartbeats. A 5 s ping watchdog can
        // abort an otherwise active speech task when the provider does not send a pong.
        // Recording queues and the caller's finalization deadline bound liveness instead.
        .pingInterval(0, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        // No request/response logger: Authorization, speech and translations are private.
        .build()

    @Provides fun clock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
}
