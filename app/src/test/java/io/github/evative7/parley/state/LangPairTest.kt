package io.github.evative7.parley.state

import io.github.evative7.parley.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LangPairTest {
    private val zhEn = LangPair(Lang("zh-CN"), Lang("en"))

    @Test fun `speech in either language goes to the other`() {
        assertEquals(Lang("en"), zhEn.targetFor(Lang("zh-CN")))
        assertEquals(Lang("zh-CN"), zhEn.targetFor(Lang("en")))
    }

    @Test fun `a language outside the pair goes to the second slot`() {
        assertEquals(Lang("en"), zhEn.targetFor(Lang("ja")))
        assertEquals(Lang("en"), zhEn.targetFor(null))
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
