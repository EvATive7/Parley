package io.github.evative7.parley.tts

import javax.inject.Inject
import javax.inject.Singleton

/** Memory-only PCM cache. Access order keeps the most recently replayed clips. */
@Singleton
class SpeechCache internal constructor(private val capacity: Int) {
    @Inject constructor() : this(DEFAULT_CAPACITY)

    private val clips = object : LinkedHashMap<SpeechKey, ByteArray>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SpeechKey, ByteArray>?): Boolean =
            size > capacity
    }

    @Synchronized fun get(key: SpeechKey): ByteArray? = clips[key]

    @Synchronized fun put(key: SpeechKey, pcm: ByteArray) {
        clips[key] = pcm
    }

    @Synchronized fun removeCard(cardId: Long) {
        clips.keys.removeAll { it.cardId == cardId }
    }

    @Synchronized fun clear() = clips.clear()

    @Synchronized internal fun keys(): List<SpeechKey> = clips.keys.toList()

    private companion object {
        const val DEFAULT_CAPACITY = 20
    }
}
