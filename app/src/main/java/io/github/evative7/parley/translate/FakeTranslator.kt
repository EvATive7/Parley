package io.github.evative7.parley.translate

import io.github.evative7.parley.model.FakeSamples
import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.random.Random

/** M1 stand-in for an OpenAI-compatible streaming translator. */
class FakeTranslator @Inject constructor() : Translator {
    override fun translate(text: String, from: Lang?, to: Lang): Flow<String> = flow {
        delay(450) // time to first token
        for (chunk in FakeSamples.chunks(FakeSamples.translate(text, to), cjkSize = 1)) {
            emit(chunk)
            delay(Random.nextLong(30, 70))
        }
    }
}
