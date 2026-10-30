package io.github.evative7.parley.model

import java.util.Locale

/** A language identified by its BCP 47 tag, e.g. `zh`, `en`, `pt-BR`. */
@JvmInline
value class Lang(val tag: String) {

    /** Base language without region, used to compare `zh-CN` with `zh`. */
    val base: String get() = tag.substringBefore('-').lowercase()

    /** Name of the language written in [display]'s language, capitalized. */
    fun displayName(display: Locale = Locale.getDefault()): String =
        Locale.forLanguageTag(tag).getDisplayName(display)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(display) else it.toString() }

    companion object {
        /** Target languages offered in the language bar. Any language the LLM can write works. */
        val targets: List<Lang> = listOf(
            "zh-CN", "zh-TW", "en", "ja", "ko", "fr", "de", "es", "it", "pt", "ru", "ar", "th", "vi", "id",
        ).map(::Lang)

        fun defaultTarget(locale: Locale = Locale.getDefault()): Lang =
            if (locale.language == "zh") Lang("en") else Lang("zh-CN")
    }
}
