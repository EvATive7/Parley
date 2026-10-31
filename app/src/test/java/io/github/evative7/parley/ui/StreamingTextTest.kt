package io.github.evative7.parley.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingTextTest {
    private val color = Color.Black

    @Test fun `appended text shows whole, with the new part present but invisible`() {
        val out = streamingText("Hello world", known = 5, segments = emptyList(), color = color)
        assertEquals("Hello world", out.text)
        assertEquals(Color.Transparent, out.spanStyles.single().item.color)
        assertEquals(5, out.spanStyles.single().start)
    }

    @Test fun `fading segments keep the whole text`() {
        val out = streamingText("Hello world", known = 11, segments = listOf(5 until 11 to 0.5f), color = color)
        assertEquals("Hello world", out.text)
        // Alpha is stored in 8 bits.
        assertEquals(0.5f, out.spanStyles.single().item.color.alpha, 0.01f)
    }

    // The two crashes on the K90 Pro Max: the text in a slot got shorter than what was known about it.
    @Test fun `text shorter than the known length does not crash`() {
        assertEquals("fourteen chars", streamingText("fourteen chars", known = 25, segments = emptyList(), color = color).text)
        assertEquals("seven c", streamingText("seven c", known = 19, segments = emptyList(), color = color).text)
    }

    @Test fun `segments past the end of a shorter text are clamped`() {
        val out = streamingText("short", known = 30, segments = listOf(3 until 20 to 0.2f, 20 until 30 to 0.1f), color = color)
        assertEquals("short", out.text)
    }

    @Test fun `empty text`() {
        assertEquals("", streamingText("", known = 12, segments = listOf(0 until 12 to 0.3f), color = color).text)
    }
}
