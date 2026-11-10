package io.github.evative7.parley.tts

import io.github.evative7.parley.model.Lang
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechCacheTest {
    @Test fun `key includes card language service voice and text`() {
        val cache = SpeechCache(20)
        val base = SpeechKey(1, Lang("en"), TtsService.Mock, "voice", "hello")
        cache.put(base, byteArrayOf(1))
        assertArrayEquals(byteArrayOf(1), cache.get(base))
        assertNull(cache.get(base.copy(cardId = 2)))
        assertNull(cache.get(base.copy(lang = Lang("fr"))))
        assertNull(cache.get(base.copy(service = TtsService.System)))
        assertNull(cache.get(base.copy(voice = "other")))
        assertNull(cache.get(base.copy(text = "changed")))
    }

    @Test fun `least recently used clip is evicted and a card can be dropped`() {
        val cache = SpeechCache(2)
        val first = SpeechKey(1, Lang("en"), TtsService.Mock, "v", "one")
        val second = first.copy(cardId = 2, text = "two")
        val third = first.copy(cardId = 3, text = "three")
        cache.put(first, byteArrayOf(1))
        cache.put(second, byteArrayOf(2))
        cache.get(first)
        cache.put(third, byteArrayOf(3))
        assertEquals(listOf(first, third), cache.keys())
        cache.removeCard(1)
        assertEquals(listOf(third), cache.keys())
    }
}
