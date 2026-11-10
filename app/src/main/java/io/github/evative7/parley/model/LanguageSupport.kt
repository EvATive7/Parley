package io.github.evative7.parley.model

/** Official language coverage outside the app's own target list, plus optional dialect notes. */
data class LanguageSupport(
    val languages: Set<String>,
    val dialects: List<String> = emptyList(),
)

data class SupportTextTemplates(
    val supports: (String) -> String,
    val unsupported: (String) -> String,
    val extras: (String) -> String,
    val extrasRemaining: (String, Int) -> String,
    val dialects: (String) -> String,
)

data class SupportSummary(
    val supported: List<Lang>,
    val unsupported: List<Lang>,
    val extras: List<String>,
    val extraRemaining: Int,
    val dialects: List<String>,
)

/** Builds the bounded language lists used by settings descriptions. */
fun LanguageSupport.summarize(project: List<Lang> = Lang.targets, extraLimit: Int = 6): SupportSummary {
    val projectBases = project.map { it.base }.toSet()
    val extras = languages.filterNot { it in projectBases }.sorted()
    return SupportSummary(
        supported = project.filter { it.base in languages },
        unsupported = project.filterNot { it.base in languages },
        extras = extras.take(extraLimit),
        extraRemaining = (extras.size - extraLimit).coerceAtLeast(0),
        dialects = dialects,
    )
}

fun SupportSummary.render(
    languageName: (String) -> String,
    projectName: (Lang) -> String,
    templates: SupportTextTemplates,
): String = buildList {
    if (supported.isNotEmpty()) add(templates.supports(supported.joinToString(", ", transform = projectName)))
    if (unsupported.isNotEmpty()) add(templates.unsupported(unsupported.joinToString(", ", transform = projectName)))
    if (extras.isNotEmpty()) {
        val names = extras.joinToString(", ", transform = languageName)
        add(if (extraRemaining == 0) templates.extras(names) else templates.extrasRemaining(names, extraRemaining))
    }
    if (dialects.isNotEmpty()) add(templates.dialects(dialects.joinToString(", ")))
}.joinToString(" ")
