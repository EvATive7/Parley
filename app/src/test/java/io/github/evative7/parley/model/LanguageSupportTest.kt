package io.github.evative7.parley.model

import io.github.evative7.parley.asr.DashScopeRegion
import io.github.evative7.parley.tts.TtsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSupportTest {
    private val templates = SupportTextTemplates(
        supports = { "Supports $it." },
        unsupported = { "Doesn't support $it." },
        extras = { "Also supports $it." },
        extrasRemaining = { names, count -> "Also supports $names and $count more." },
        dialects = { "Also supports dialects such as $it." },
    )

    @Test fun `summary compares project languages by base in project order`() {
        val project = listOf(Lang("en"), Lang("zh-CN"), Lang("ja"), Lang("zh-TW"))
        val summary = LanguageSupport(setOf("zh", "en", "fr")).summarize(project)
        assertEquals(listOf(Lang("en"), Lang("zh-CN"), Lang("zh-TW")), summary.supported)
        assertEquals(listOf(Lang("ja")), summary.unsupported)
        assertEquals(listOf("fr"), summary.extras)
    }

    @Test fun `render includes capped extras remainder and dialect sentence`() {
        val support = LanguageSupport(
            setOf("en", "de", "fr", "it", "ja", "ko", "pt", "ru"),
            listOf("Cantonese", "Shanghainese"),
        )
        val text = support.summarize(listOf(Lang("en"), Lang("zh-CN")), extraLimit = 6).render(
            languageName = { it.uppercase() }, projectName = { it.tag }, templates = templates,
        )
        assertTrue(text.contains("Supports en."))
        assertTrue(text.contains("Doesn't support zh-CN."))
        assertTrue(text.contains("and 1 more."))
        assertTrue(text.contains("Cantonese, Shanghainese"))
    }

    @Test fun `render omits unsupported extras and dialect sentences when empty`() {
        val text = LanguageSupport(setOf("en")).summarize(listOf(Lang("en"))).render(
            languageName = { it }, projectName = { it.tag }, templates = templates,
        )
        assertEquals("Supports en.", text)
    }

    @Test fun `TTS project support always agrees with the regional default voice`() {
        for (service in TtsService.entries) {
            for (region in DashScopeRegion.entries) {
                val support = service.support(region)
                for (lang in Lang.targets) {
                    assertEquals(
                        "$service $region ${lang.tag}",
                        service.defaultVoice(lang, region) != null,
                        lang.base in support.languages,
                    )
                }
            }
        }
        assertTrue(TtsService.CosyVoiceV3Flash.supports(Lang("ja"), DashScopeRegion.Beijing))
        assertFalse(TtsService.CosyVoiceV3Flash.supports(Lang("ja"), DashScopeRegion.Singapore))
    }
}
