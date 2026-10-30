package io.github.evative7.parley.debug

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {
    @After fun tearDown() = DebugLog.setEnabled(false)

    @Test fun `disabled log neither records nor builds the message`() {
        var built = false
        DebugLog.log("T") { built = true; "x" }
        assertFalse(built)
        assertTrue(DebugLog.entries.value.isEmpty())
        assertNull(DebugLog.sink("T"))
    }

    @Test fun `enabled log keeps the newest entries up to capacity`() {
        DebugLog.setEnabled(true)
        repeat(DebugLog.CAPACITY + 5) { i -> DebugLog.log("T") { "m$i" } }
        val entries = DebugLog.entries.value
        assertEquals(DebugLog.CAPACITY, entries.size)
        assertEquals("m5", entries.first().message)
        assertEquals("m${DebugLog.CAPACITY + 4}", entries.last().message)
    }

    @Test fun `sink records under its tag and turning off clears the log`() {
        DebugLog.setEnabled(true)
        DebugLog.sink("DashScope")!!("event=task-started")
        assertEquals("DashScope", DebugLog.entries.value.single().tag)
        DebugLog.setEnabled(false)
        assertTrue(DebugLog.entries.value.isEmpty())
    }
}
