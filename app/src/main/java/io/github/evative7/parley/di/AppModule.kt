package io.github.evative7.parley.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.evative7.parley.asr.FakeRecognizer
import io.github.evative7.parley.asr.SpeechRecognizer
import io.github.evative7.parley.translate.FakeTranslator
import io.github.evative7.parley.translate.Translator
import javax.inject.Singleton

/** M1 wires fake services; M2 swaps in a real streaming ASR and an OpenAI-compatible translator. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun recognizer(impl: FakeRecognizer): SpeechRecognizer

    @Binds
    @Singleton
    abstract fun translator(impl: FakeTranslator): Translator
}
