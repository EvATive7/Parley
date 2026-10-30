package io.github.evative7.parley.translate

import io.github.evative7.parley.model.Lang
import kotlinx.coroutines.flow.Flow

interface Translator {
    /** The screen is visible: connect ahead of the first translation. Sends no text. */
    fun prepare() {}

    /** Streams the translation of [text] as it is generated; each emission is a new fragment. */
    fun translate(text: String, from: Lang?, to: Lang): Flow<String>
}
