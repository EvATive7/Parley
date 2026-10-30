package io.github.evative7.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeMeterTest {

    @Test
    fun `silence is the floor`() {
        assertEquals(-120f, VolumeMeter.dbfs(ShortArray(320)), 0f)
        assertEquals(0f, VolumeMeter().process(ShortArray(320)), 0f)
    }

    @Test
    fun `full scale square wave is 0 dBFS`() {
        val pcm = ShortArray(320) { if (it % 2 == 0) Short.MAX_VALUE else (-Short.MAX_VALUE).toShort() }
        assertEquals(0f, VolumeMeter.dbfs(pcm), 0.01f)
    }

    @Test
    fun `level rises fast and falls slowly`() {
        val loud = ShortArray(320) { 16_000 }
        val meter = VolumeMeter()
        val up = meter.process(loud)
        val down = meter.process(ShortArray(320)).let { up - it }
        assertTrue("attack $up should exceed release $down", up > down)
    }
}
