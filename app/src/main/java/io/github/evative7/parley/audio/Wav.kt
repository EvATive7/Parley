package io.github.evative7.parley.audio

import java.io.ByteArrayOutputStream

const val AUDIO_SAMPLE_RATE = 16_000

fun pcm16Bytes(samples: ShortArray): ByteArray = ByteArray(samples.size * 2).also { bytes ->
    samples.forEachIndexed { index, sample ->
        bytes[index * 2] = (sample.toInt() and 0xff).toByte()
        bytes[index * 2 + 1] = ((sample.toInt() ushr 8) and 0xff).toByte()
    }
}

fun pcm16Wav(samples: ShortArray, sampleRate: Int = AUDIO_SAMPLE_RATE): ByteArray {
    val pcm = pcm16Bytes(samples)
    val out = ByteArrayOutputStream(44 + pcm.size)
    fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
    fun little16(value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
    }
    fun little32(value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 24) and 0xff)
    }
    ascii("RIFF")
    little32(36 + pcm.size)
    ascii("WAVEfmt ")
    little32(16)
    little16(1)
    little16(1)
    little32(sampleRate)
    little32(sampleRate * 2)
    little16(2)
    little16(16)
    ascii("data")
    little32(pcm.size)
    out.write(pcm)
    return out.toByteArray()
}

fun resample16kTo24k(samples: ShortArray): ShortArray {
    if (samples.isEmpty()) return samples
    if (samples.size == 1) return shortArrayOf(samples[0], samples[0])
    val targetSize = ((samples.size * 3L + 1) / 2).toInt()
    return ShortArray(targetSize) { target ->
        val sourcePosition = target * 2.0 / 3.0
        val left = sourcePosition.toInt().coerceAtMost(samples.lastIndex)
        val right = (left + 1).coerceAtMost(samples.lastIndex)
        val fraction = sourcePosition - left
        (samples[left] + (samples[right] - samples[left]) * fraction)
            .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

internal fun List<ShortArray>.flattenSamples(): ShortArray {
    val total = sumOf { it.size }
    return ShortArray(total).also { merged ->
        var offset = 0
        forEach { frame ->
            frame.copyInto(merged, offset)
            offset += frame.size
        }
    }
}
