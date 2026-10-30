package io.github.evative7.parley.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavTest {
    @Test fun `writes mono PCM16 WAV header and little endian samples`() {
        val wav = pcm16Wav(shortArrayOf(0x1234, -2))
        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("data", wav.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertArrayEquals(byteArrayOf(0x34, 0x12, -2, -1), wav.copyOfRange(44, 48))
    }

    @Test fun `resamples 16 kHz frames to one and a half times the samples`() {
        val output = resample16kTo24k(shortArrayOf(0, 300, 600, 900))
        assertEquals(6, output.size)
        assertEquals(0, output.first().toInt())
        assertEquals(900, output.last().toInt())
    }
}
