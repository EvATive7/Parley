package io.github.evative7.parley.model

/**
 * The languages a service officially handles, as ISO 639 codes in the order to mention them; Chinese
 * varieties such as `yue` (Cantonese) or `wuu` (Wu) are listed like any other language.
 */
data class LanguageSupport(val languages: List<String>) {
    fun supports(lang: Lang): Boolean = lang.base in languages

    companion object {
        fun of(codes: String) = LanguageSupport(codes.split(' ').filter(String::isNotEmpty))
    }
}

/** What a service description says about languages: Parley's own first, then up to a few others. */
data class SupportSummary(
    val supported: List<Lang>,
    val unsupported: List<Lang>,
    /** Codes of languages Parley doesn't offer, at most the limit given to [summarize]. */
    val extras: List<String>,
    /** How many such languages there are in all, named or not. */
    val extraTotal: Int,
)

fun LanguageSupport.summarize(project: List<Lang> = Lang.targets, extraLimit: Int = 6): SupportSummary {
    val projectBases = project.map { it.base }.toSet()
    val extras = languages.filterNot { it in projectBases }.distinct()
    return SupportSummary(
        supported = project.filter(::supports),
        unsupported = project.filterNot(::supports),
        extras = extras.take(extraLimit),
        extraTotal = extras.size,
    )
}

/** The sentences of a support line, worded and joined the way the display language does it. */
class SupportWording(
    val supports: (names: String) -> String,
    val unsupported: (names: String) -> String,
    val extras: (names: String) -> String,
    /** For a list cut short: the names shown, how many more there are, and how many in all. */
    val extrasMore: (names: String, more: Int, total: Int) -> String,
    /** Joins a whole list, e.g. "A, B and C". */
    val list: (List<String>) -> String,
    /** Joins the start of a longer list, e.g. "A, B, C". */
    val series: (List<String>) -> String,
    /** Between sentences: a space in most languages, nothing in Chinese or Japanese. */
    val sentenceGap: String,
)

fun SupportSummary.render(
    languageName: (String) -> String,
    projectName: (Lang) -> String,
    wording: SupportWording,
): String = buildList {
    if (supported.isNotEmpty()) add(wording.supports(wording.list(supported.map(projectName))))
    if (unsupported.isNotEmpty()) add(wording.unsupported(wording.list(unsupported.map(projectName))))
    if (extras.isNotEmpty()) {
        val names = extras.map(languageName)
        add(
            if (extraTotal == extras.size) wording.extras(wording.list(names))
            else wording.extrasMore(wording.series(names), extraTotal - extras.size, extraTotal),
        )
    }
}.joinToString(wording.sentenceGap)

/**
 * Puts [support] on its own line before the last sentence of [description], which says what sets this variant
 * apart (e.g. stream input). Without a sentence break to split at, the line goes at the end.
 */
fun withSupportLine(description: String, support: String): String {
    val text = description.trim()
    val breaks = Regex("[。！？]|[.!?]\\s+").findAll(text).filter { it.range.last < text.length - 1 }.toList()
    val cut = breaks.lastOrNull()?.range?.last?.plus(1)
        ?: return listOf(text, support).filter(String::isNotEmpty).joinToString("\n")
    return listOf(text.substring(0, cut).trim(), support, text.substring(cut).trim())
        .filter(String::isNotEmpty).joinToString("\n")
}
