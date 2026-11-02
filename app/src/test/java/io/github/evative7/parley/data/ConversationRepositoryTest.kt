package io.github.evative7.parley.data

import io.github.evative7.parley.asr.AsrAlternative
import io.github.evative7.parley.asr.AsrService
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.state.CardStage
import io.github.evative7.parley.state.CardState
import io.github.evative7.parley.state.CardTranslation
import io.github.evative7.parley.state.ErrorKind
import io.github.evative7.parley.state.LangPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationRepositoryTest {
    private val pair = LangPair(Lang("zh-CN"), Lang("en"))

    @Test fun `JSON round trip keeps every persisted card field except id`() {
        val cards = listOf(
            CardState(
                id = 91,
                langs = pair,
                stage = CardStage.Done,
                source = "bonjour",
                sourceLang = Lang("fr"),
                sourceService = AsrService.FunAsrRealtimeStream,
                alternatives = listOf(AsrAlternative(AsrService.Gpt4oTranscribeNonStream, "bon jour")),
                translations = listOf(
                    CardTranslation(Lang("zh-CN"), "你好"),
                    CardTranslation(Lang("en"), "hello"),
                ),
                error = ErrorKind.Network,
                canRetry = true,
            ),
            CardState(
                id = 92,
                langs = pair,
                stage = CardStage.Error,
                source = "你好",
                sourceLang = Lang("zh"),
                translations = listOf(CardTranslation(Lang("en"), "hel")),
                error = ErrorKind.Timeout,
                canRetry = true,
            ),
        )

        val decoded = ConversationRepository.decode(ConversationRepository.encode(cards))

        assertEquals(cards.map { it.copy(id = 0) }, decoded)
    }

    @Test fun `only settled useful cards are encoded and the newest 200 are kept`() {
        val cards = (0L..204L).map { id ->
            CardState(id, pair, CardStage.Done, source = "card$id")
        } + listOf(
            CardState(300, pair, CardStage.Listening, source = "live"),
            CardState(301, pair, CardStage.Error),
        )

        val decoded = ConversationRepository.decode(ConversationRepository.encode(cards))

        assertEquals(200, decoded.size)
        assertEquals("card0", decoded.first().source)
        assertEquals("card199", decoded.last().source)
    }

    @Test fun `unknown optional enum is dropped without losing its card`() {
        val card = CardState(
            id = 1,
            langs = pair,
            stage = CardStage.Done,
            source = "hello",
            sourceService = AsrService.FunAsrRealtimeStream,
        )
        val text = ConversationRepository.encode(listOf(card))
            .replace(AsrService.FunAsrRealtimeStream.name, "RemovedService")

        assertNull(ConversationRepository.decode(text).single().sourceService)
    }

    @Test fun `a newer conversation document is refused`() {
        val text = ConversationRepository.encode(emptyList()).replace("\"version\":1", "\"version\":2")
        assertThrows(IllegalArgumentException::class.java) { ConversationRepository.decode(text) }
    }
}
