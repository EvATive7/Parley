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
        localizedName(display)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(display) else it.toString() }

    private fun localizedName(display: Locale): String = when {
        tag.equals("zh-CN", ignoreCase = true) && display.language == "zh" ->
            if (display.usesTraditionalChinese()) "簡體中文" else "简体中文"
        tag.equals("zh-TW", ignoreCase = true) && display.language == "zh" ->
            if (display.usesTraditionalChinese()) "繁體中文" else "繁体中文"
        tag.equals("zh-CN", ignoreCase = true) -> Locale.forLanguageTag("zh-Hans").getDisplayName(display)
        tag.equals("zh-TW", ignoreCase = true) -> Locale.forLanguageTag("zh-Hant").getDisplayName(display)
        else -> locale.getDisplayName(display)
    }

    /** Name of the language written in itself, e.g. `日本語`. */
    fun nativeName(): String = displayName(locale)

    companion object {
        /** Languages offered in the language bar. Any language the LLM can write works. */
        val targets: List<Lang> = listOf(
            "en", "zh-CN", "ja", "ko", "zh-TW", "fr", "de", "es", "it", "pt", "ru", "ar", "th", "vi", "id",
        ).map(::Lang)

        /** The device's own language, as the closest entry in [targets]. */
        fun defaultFirst(locale: Locale = Locale.getDefault()): Lang = closest(locale) ?: Lang("en")

        /** The entry in [targets] that [locale] would read, or null if none is close. */
        fun closest(locale: Locale): Lang? = when {
            locale.language == "zh" ->
                if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) Lang("zh-TW")
                else Lang("zh-CN")
            // Java still reports Indonesian as the legacy code `in` on some versions.
            locale.language == "in" -> Lang("id")
            else -> targets.firstOrNull { it.base == locale.language }
        }

        /** The other party's language when nothing has been picked yet. */
        fun defaultSecond(first: Lang): Lang = if (first.base == "zh") Lang("en") else Lang("zh-CN")
    }
}

private fun Locale.usesTraditionalChinese(): Boolean =
    script == "Hant" || country in setOf("TW", "HK", "MO")
