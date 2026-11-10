package io.github.evative7.parley.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Streams 24 kHz mono PCM through AudioTrack without writing it to disk. */
@Singleton
class PcmPlayer @Inject constructor(@ApplicationContext context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }
    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var active: AudioTrack? = null

    /**
     * Plays [audio] as it arrives. Returns true when all of it was heard; false when [stop] or a lost audio
     * focus cut it short, so a partial clip is never mistaken for a whole one.
     */
    suspend fun play(audio: Flow<ByteArray>, onFirstAudio: suspend () -> Unit): Boolean = withContext(Dispatchers.IO) {
        requestFocus()
        var track: AudioTrack? = null
        var frames = 0L
        try {
            val whole = run {
                audio.collect { pcm ->
                    if (pcm.isEmpty()) return@collect
                    val current = track ?: newTrack().also {
                        track = it
                        active = it
                        it.play()
                        onFirstAudio()
                    }
                    // Never block in write(): a stopped track would hold this thread forever.
                    var offset = 0
                    while (offset < pcm.size) {
                        ensureActive()
                        if (active !== current) throw Interrupted()
                        val written = current.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                        check(written >= 0) { "AudioTrack write failed: $written" }
                        offset += written
                        if (offset < pcm.size) delay(BUFFER_WAIT_MS)
                    }
                    frames += pcm.size / BYTES_PER_FRAME
                }
                // Let the buffered tail play out before the track is released.
                val current = track ?: return@run true
                val deadline = System.nanoTime() + (frames * 1_000_000_000L / SAMPLE_RATE) + TAIL_SLACK_NS
                while (active === current && current.playbackHeadPosition.toLong() < frames && System.nanoTime() < deadline) {
                    delay(BUFFER_WAIT_MS)
                }
                active === current
            }
            whole
        } catch (_: Interrupted) {
            false
        } finally {
            val current = track
            if (active === current) active = null
            runCatching { current?.stop() }
            current?.release()
            abandonFocus()
        }
    }

    /** Thrown inside [play] when [stop] took the track away. */
    private class Interrupted : Exception()

    fun stop() {
        val track = active
        active = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
    }

    private fun newTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minimum = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 5 * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minimum)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val BYTES_PER_FRAME = 2
        const val BUFFER_WAIT_MS = 10L
        const val TAIL_SLACK_NS = 1_000_000_000L
    }
}
