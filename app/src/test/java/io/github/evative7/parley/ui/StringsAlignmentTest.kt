package io.github.evative7.parley.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the localization files: every `values-*` locale must declare exactly the same
 * `<string>` and `<plurals>` keys as `values`, and must keep the same printf placeholders.
 *
 * `values-night` is skipped: it holds colors only and has no `strings.xml`.
 */
class StringsAlignmentTest {

    private data class Entry(val type: String, val name: String, val placeholders: List<String>) {
        val key: String get() = "$type:$name"
    }

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull { File(it, "values/strings.xml").isFile }
        ?: error("Cannot find values/strings.xml; run the unit tests from the app module")

    private val base: Map<String, Entry> = parse(File(resDir, "values"))

    private val locales: List<File> = resDir.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-night" }
        .sortedBy { it.name }

    @Test
    fun `every locale declares exactly the base string and plurals keys`() {
        assertTrue("No translated locale directories found", locales.isNotEmpty())
        val baseKeys = base.keys.sorted()
        for (locale in locales) {
            val keys = parse(locale).keys.sorted()
            assertEquals("${locale.name} has a different key set than values", baseKeys, keys)
        }
    }

    @Test
    fun `every locale uses the same printf placeholders as base`() {
        for (locale in locales) {
            val translated = parse(locale)
            for ((key, source) in base) {
                val target = translated.getValue(key)
                assertEquals(
                    "${locale.name} changed the printf placeholders of $key",
                    source.placeholders,
                    target.placeholders,
                )
            }
        }
    }

    private fun parse(dir: File): Map<String, Entry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val root = factory.newDocumentBuilder()
            .parse(File(dir, "strings.xml"))
            .documentElement

        val entries = LinkedHashMap<String, Entry>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val element = children.item(i) as? Element ?: continue
            val type = element.tagName
            if (type != "string" && type != "plurals") continue
            val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
            val entry = Entry(type, name, placeholders(textOf(element)))
            check(entries.put(entry.key, entry) == null) { "${dir.name}/${element.tagName} $name is duplicated" }
        }
        return entries
    }

    /** Text of a `<string>` or of all `<item>`s of a `<plurals>`; nested markup is flattened. */
    private fun textOf(element: Element): String {
        if (element.tagName == "string") return element.textContent
        val items = element.getElementsByTagName("item")
        return (0 until items.length).joinToString(" ") { items.item(it).textContent }
    }

    /** Distinct printf placeholders (e.g. `%1$s`, `%1$.1f`), order-independent. */
    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.distinct().sorted().toList()

    private companion object {
        val PLACEHOLDER = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[A-Za-z]""")
    }
}
