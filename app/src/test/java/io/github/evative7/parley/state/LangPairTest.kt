package io.github.evative7.parley.state

import io.github.evative7.parley.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LangPairTest {
    private val zhEn = LangPair(Lang("zh-CN"), Lang("en"))

    @Test fun `speech in either language goes to the other`() {
        assertEquals(listOf(Lang("en")), zhEn.targetsFor(Lang("zh-CN")))
        assertEquals(listOf(Lang("zh-CN")), zhEn.targetsFor(Lang("en")))
        // A detector reports the base language, or another region of it.
        assertEquals(listOf(Lang("en")), zhEn.targetsFor(Lang("zh")))
        assertEquals(listOf(Lang("zh-CN")), zhEn.targetsFor(Lang("en-GB")))
    }

    @Test fun `a language outside the pair goes to both`() {
        assertEquals(listOf(Lang("zh-CN"), Lang("en")), zhEn.targetsFor(Lang("ja")))
    }

    @Test fun `an unknown language goes to both`() {
        assertEquals(listOf(Lang("zh-CN"), Lang("en")), zhEn.targetsFor(null))
    }

    @Test fun `variants of one language are told apart only by their exact tag`() {
        val scripts = LangPair(Lang("zh-CN"), Lang("zh-TW"))
        assertEquals(listOf(Lang("zh-TW")), scripts.targetsFor(Lang("zh-CN")))
        assertEquals(listOf(Lang("zh-CN")), scripts.targetsFor(Lang("zh-tw")))
        assertEquals(listOf(Lang("zh-CN"), Lang("zh-TW")), scripts.targetsFor(Lang("zh")))
    }

    @Test fun `picking the other slot's language swaps`() {
        assertEquals(LangPair(Lang("en"), Lang("zh-CN")), zhEn.with(0, Lang("en")))
        assertEquals(LangPair(Lang("ja"), Lang("en")), zhEn.with(0, Lang("ja")))
        assertEquals(LangPair(Lang("zh-CN"), Lang("ko")), zhEn.with(1, Lang("ko")))
    }

    @Test fun `first language follows the device`() {
        assertEquals(Lang("zh-CN"), Lang.defaultFirst(Locale.forLanguageTag("zh-CN")))
        assertEquals(Lang("zh-TW"), Lang.defaultFirst(Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals(Lang("ja"), Lang.defaultFirst(Locale.JAPAN))
        assertEquals(Lang("en"), Lang.defaultFirst(Locale.forLanguageTag("sv-SE")))
        assertEquals(Lang("en"), Lang.defaultSecond(Lang("zh-TW")))
        assertEquals(Lang("zh-CN"), Lang.defaultSecond(Lang("ja")))
    }

    @Test fun `Chinese variants are named by script rather than region`() {
        assertEquals("简体中文", Lang("zh-CN").displayName(Locale.SIMPLIFIED_CHINESE))
        assertEquals("繁体中文", Lang("zh-TW").displayName(Locale.SIMPLIFIED_CHINESE))
        assertEquals("簡體中文", Lang("zh-CN").displayName(Locale.TRADITIONAL_CHINESE))
        assertEquals("繁體中文", Lang("zh-TW").displayName(Locale.TRADITIONAL_CHINESE))
    }
}
