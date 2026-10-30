package io.github.evative7.parley.network

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseTest {
    @Test fun `comments CRLF multiline data and incomplete EOF`() {
        val source = Buffer().writeUtf8(": heartbeat\r\nevent: message\r\ndata: first\r\ndata: second\r\n\r\ndata: final\n\ndata: incomplete")
        val events = mutableListOf<String>()
        readSse(source) { events.add(it); true }
        assertEquals(listOf("first\nsecond", "final"), events)
    }

    @Test fun `consumer can terminate without reading the remaining stream`() {
        val events = mutableListOf<String>()
        readSse(Buffer().writeUtf8("data: [DONE]\n\ndata: ignored\n\n")) {
            events += it
            false
        }
        assertEquals(listOf("[DONE]"), events)
    }
}
