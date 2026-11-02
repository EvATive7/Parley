package io.github.evative7.parley.translate

import io.github.evative7.parley.data.ServiceSettingsSource
import io.github.evative7.parley.mock.MockTranslator
import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Sends each translation to the service the selected preset names: the mock one or an OpenAI-compatible one. */
@Singleton
class RoutingTranslator @Inject constructor(
    private val settings: ServiceSettingsSource,
    private val compatible: OpenAiCompatibleTranslator,
    private val mock: MockTranslator,
) : Translator {
    private val active: Translator
        get() = if (settings.current?.selectedTranslation?.mock == true) mock else compatible

    override fun prepare() = active.prepare()

    override fun translate(text: String, from: Lang?, to: Lang, references: List<String>): Flow<String> =
        active.translate(text, from, to, references)
}
