package io.github.evative7.parley.state

import io.github.evative7.parley.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class CardCellTest {
    private val zhEn = LangPair(Lang("zh-CN"), Lang("en"))

    private fun card(langs: LangPair = zhEn, sourceLang: Lang? = null, target: Lang? = null) = CardState(
        id = 1,
        langs = langs,
        stage = CardStage.Done,
        source = "src",
        sourceLang = sourceLang,
        target = target,
        translation = "tr",
    )

    @Test fun `speech stays on its side and the translation lands on the other`() {
        val c = card(sourceLang = Lang("en"), target = Lang("zh-CN"))
        assertEquals(CardCell.Translation("tr"), c.cell(0))
        assertEquals(CardCell.Source("src"), c.cell(1))
    }

    @Test fun `undetected speech shows on both sides`() {
        val c = card()
        assertEquals(CardCell.Hearing("src"), c.cell(0))
        assertEquals(CardCell.Hearing("src"), c.cell(1))
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
