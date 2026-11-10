package io.github.evative7.parley.state

import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.tts.TtsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsAutoPlayTest {
    private val translation = CardTranslation(Lang("en"), "Hello")
    private val card = CardState(
        id = 1,
        langs = LangPair(Lang("zh-CN"), Lang("en")),
        stage = CardStage.Done,
        translations = listOf(translation),
    )
    private val enabled = ServiceSettings(ttsService = TtsService.Mock, ttsAutoGenerate = true, ttsAutoPlayPortrait = true)

    @Test fun `one completed translation auto plays only in the foreground`() {
        assertEquals(translation, autoPlayTranslation(card, enabled, foreground = true, faceToFace = false))
        assertNull(autoPlayTranslation(card, enabled, foreground = false, faceToFace = false))
        assertNull(autoPlayTranslation(card.copy(stage = CardStage.Translating), enabled, true, false))
        assertNull(autoPlayTranslation(card, enabled.copy(ttsAutoPlayPortrait = false), true, false))
        assertNull(autoPlayTranslation(card, enabled.copy(ttsAutoGenerate = false), true, false))
        assertNull(autoPlayTranslation(card, enabled.copy(ttsService = null), true, false))
    }

    @Test fun `each layout has its own switch, and both need generation`() {
        val faceToFaceOnly = enabled.copy(ttsAutoPlayPortrait = false, ttsAutoPlayFaceToFace = true)
        assertEquals(translation, autoPlayTranslation(card, faceToFaceOnly, foreground = true, faceToFace = true))
        assertNull(autoPlayTranslation(card, faceToFaceOnly, foreground = true, faceToFace = false))
        assertNull(autoPlayTranslation(card, faceToFaceOnly.copy(ttsAutoGenerate = false), true, true))
    }

    @Test fun `two targets are tap only and face to face needs its own switch`() {
        assertNull(autoPlayTranslation(card.copy(translations = listOf(translation, translation.copy(to = Lang("ja")))), enabled, true, false))
        assertNull(autoPlayTranslation(card, enabled, foreground = true, faceToFace = true))
        assertEquals(
            translation,
            autoPlayTranslation(card, enabled.copy(ttsAutoPlayFaceToFace = true), foreground = true, faceToFace = true),
        )
    }
}
