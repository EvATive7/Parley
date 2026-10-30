package io.github.evative7.parley.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject

/** Captures 16 kHz mono PCM from the microphone in 20 ms frames. Audio never touches disk. */
class AudioRecorder @Inject constructor() {

    /**
     * Cold flow: recording starts on collection and stops when the collector is cancelled.
     * The caller must hold `RECORD_AUDIO`; otherwise this throws [SecurityException].
     */
    @SuppressLint("MissingPermission")
    fun frames(): Flow<ShortArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, CHANNEL, ENCODING,
            maxOf(minBuffer, FRAME_SAMPLES * 2 * 4),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
        try {
            record.startRecording()
            val buffer = ShortArray(FRAME_SAMPLES)
            while (currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, FRAME_SAMPLES)
                if (read < 0) error("AudioRecord.read returned $read")
                if (read > 0) emit(buffer.copyOf(read))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = SAMPLE_RATE / 50 // 20 ms
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
