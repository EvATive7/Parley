package io.github.evative7.parley.model

import java.util.Locale

/** A language identified by its BCP 47 tag, e.g. `zh`, `en`, `pt-BR`. */
@JvmInline
value class Lang(val tag: String) {

    /** Base language without region, used to compare `zh-CN` with `zh`. */
    val base: String get() = tag.substringBefore('-').lowercase()

    val locale: Locale get() = Locale.forLanguageTag(tag)

    /** Name of the language written in [display]'s language, capitalized. */
    fun displayName(display: Locale = Locale.getDefault()): String =
        locale.getDisplayName(display)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(display) else it.toString() }

    /** Name of the language written in itself, e.g. `日本語`. */
    fun nativeName(): String = displayName(locale)

    companion object {
        /** Languages offered in the language bar. Any language the LLM can write works. */
        val targets: List<Lang> = listOf(
            "zh-CN", "zh-TW", "en", "ja", "ko", "fr", "de", "es", "it", "pt", "ru", "ar", "th", "vi", "id",
        ).map(::Lang)

        /** The device's own language, as the closest entry in [targets]. */
        fun defaultFirst(locale: Locale = Locale.getDefault()): Lang = when {
            locale.language == "zh" ->
                if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) Lang("zh-TW")
                else Lang("zh-CN")
            // Java still reports Indonesian as the legacy code `in` on some versions.
            locale.language == "in" -> Lang("id")
            else -> targets.firstOrNull { it.base == locale.language } ?: Lang("en")
        }

        /** The other party's language when nothing has been picked yet. */
        fun defaultSecond(first: Lang): Lang = if (first.base == "zh") Lang("en") else Lang("zh-CN")
    }
}
