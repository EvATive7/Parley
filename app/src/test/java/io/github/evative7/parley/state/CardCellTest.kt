package io.github.evative7.parley.state

import io.github.evative7.parley.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCellTest {
    private val zhEn = LangPair(Lang("zh-CN"), Lang("en"))

    private fun card(
        langs: LangPair = zhEn,
        sourceLang: Lang? = null,
        targets: List<Lang> = emptyList(),
        stage: CardStage = CardStage.Done,
    ) = CardState(
        id = 1,
        langs = langs,
        stage = stage,
        source = "src",
        sourceLang = sourceLang,
        translations = targets.map { CardTranslation(it, "tr:${it.tag}") },
    )

    @Test fun `speech stays on its side and the translation lands on the other`() {
        val c = card(sourceLang = Lang("en"), targets = listOf(Lang("zh-CN")))
        assertEquals(CardCell.Translation(CardTranslation(Lang("zh-CN"), "tr:zh-CN")), c.cell(0))
        assertEquals(CardCell.Source("src"), c.cell(1))
    }

    @Test fun `undetected speech shows on both sides`() {
        val c = card()
        assertEquals(CardCell.Hearing("src"), c.cell(0))
        assertEquals(CardCell.Hearing("src"), c.cell(1))
    }

    @Test fun `speech translated both ways shows whole on each side with that side's translation`() {
        val c = card(sourceLang = Lang("fr"), targets = listOf(Lang("zh-CN"), Lang("en")))
        assertEquals(CardCell.Whole(CardTranslation(Lang("zh-CN"), "tr:zh-CN")), c.cell(0))
        assertEquals(CardCell.Whole(CardTranslation(Lang("en"), "tr:en")), c.cell(1))
    }

    @Test fun `a recognition error shows on both sides`() {
        val c = card(stage = CardStage.Error)
        assertTrue(c.showsError(0))
        assertTrue(c.showsError(1))
    }

    @Test fun `a translation error shows only where the translation was going`() {
        val c = card(sourceLang = Lang("en"), targets = listOf(Lang("zh-CN")), stage = CardStage.Error)
        assertTrue(c.showsError(0))
        assertFalse(c.showsError(1))
    }

    @Test fun `a translation error on speech translated both ways shows on both sides`() {
        val c = card(sourceLang = Lang("fr"), targets = listOf(Lang("zh-CN"), Lang("en")), stage = CardStage.Error)
        assertTrue(c.showsError(0))
        assertTrue(c.showsError(1))
    }

    @Test fun `a settled card shows no error`() {
        val c = card(sourceLang = Lang("en"), targets = listOf(Lang("zh-CN")))
        assertFalse(c.showsError(0))
        assertFalse(c.showsError(1))
    }

    @Test fun `cards follow their languages after a swap`() {
        val c = card()
        assertEquals(0, c.slotFor(zhEn, 0))
        assertEquals(1, c.slotFor(zhEn, 1))
        assertEquals(1, c.slotFor(zhEn.swapped(), 0))
        assertEquals(0, c.slotFor(zhEn.swapped(), 1))
    }

    @Test fun `a newly picked language keeps the remaining one in place`() {
        val c = card()
        // en moved to the left, ja replaced zh on the right.
        assertEquals(1, c.slotFor(LangPair(Lang("en"), Lang("ja")), 0))
        assertEquals(0, c.slotFor(LangPair(Lang("en"), Lang("ja")), 1))
        // Nothing in common: keep the recorded order.
        assertEquals(0, c.slotFor(LangPair(Lang("ja"), Lang("ko")), 0))
    }

    @Test fun `script variants match exactly before falling back to the base language`() {
        val c = card(langs = LangPair(Lang("zh-CN"), Lang("zh-TW")))
        assertEquals(1, c.slotFor(LangPair(Lang("zh-TW"), Lang("zh-CN")), 0))
        assertEquals(0, c.slotFor(LangPair(Lang("zh-TW"), Lang("zh-CN")), 1))
    }
}
