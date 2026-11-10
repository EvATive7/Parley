package io.github.evative7.parley.mock

/** How long a mock service takes. [Slow] still finishes within the 10 s pipeline timeout. */
enum class MockSpeed(val recognizeMs: Long, val firstTokenMs: Long, val chunkMs: Long) {
    Fast(recognizeMs = 120, firstTokenMs = 80, chunkMs = 15),
    Normal(recognizeMs = 450, firstTokenMs = 350, chunkMs = 45),
    Slow(recognizeMs = 2_500, firstTokenMs = 1_800, chunkMs = 140);

    companion object {
        fun fromName(value: String?): MockSpeed = entries.firstOrNull { it.name == value } ?: Normal
    }
}

/** A failure the mock recognizer produces on every press, for trying out each error path. */
enum class MockAsrFault {
    None, NoSpeech, NetworkWhileRecording, Authentication, Timeout;

    companion object {
        fun fromName(value: String?): MockAsrFault = entries.firstOrNull { it.name == value } ?: None
    }
}

/** A failure the mock translator produces on every request. */
enum class MockTranslationFault {
    None, NetworkMidway, Authentication, RateLimit, Timeout;

    companion object {
        fun fromName(value: String?): MockTranslationFault = entries.firstOrNull { it.name == value } ?: None
    }
}

/** A failure the mock speech synthesizer produces on every request. */
enum class MockTtsFault {
    None, NetworkMidway, Authentication, Timeout;

    companion object {
        fun fromName(value: String?): MockTtsFault = entries.firstOrNull { it.name == value } ?: None
    }
}
