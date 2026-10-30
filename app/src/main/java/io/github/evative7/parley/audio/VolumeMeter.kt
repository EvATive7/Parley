package io.github.evative7.parley.audio

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Turns PCM frames into a smoothed 0–1 loudness level for animation.
 * RMS → dBFS → linear map of [floorDb, 0] onto [0, 1], then fast attack / slow release.
 */
class VolumeMeter(
    private val floorDb: Float = -55f,
    private val attack: Float = 0.6f,
    private val release: Float = 0.35f,
) {
    var level: Float = 0f
        private set

    fun process(pcm: ShortArray): Float {
        val target = normalize(dbfs(pcm))
        val k = if (target > level) attack else release
        level += (target - level) * k
        return level
    }

    fun reset() {
        level = 0f
    }

    internal fun normalize(db: Float): Float = ((db - floorDb) / -floorDb).coerceIn(0f, 1f)

    companion object {
        fun dbfs(pcm: ShortArray): Float {
            if (pcm.isEmpty()) return -120f
            var sum = 0.0
            for (s in pcm) sum += s.toDouble() * s
            val rms = sqrt(sum / pcm.size)
            if (rms < 1.0) return -120f
            return (20 * log10(rms / Short.MAX_VALUE)).toFloat()
        }
    }
}
