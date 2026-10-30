package io.github.evative7.parley.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory event log shown by the debug overlay. Nothing reaches disk or logcat.
 * Callers log events, sizes and timings only: never keys, audio, or request/response bodies.
 */
object DebugLog {
    data class Entry(val id: Long, val wallMs: Long, val tag: String, val message: String)

    const val CAPACITY = 500

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val nextId = AtomicLong()

    fun setEnabled(value: Boolean) {
        if (_enabled.value == value) return
        _enabled.value = value
        if (!value) clear()
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** [message] is only built while debug mode is on. */
    inline fun log(tag: String, message: () -> String) {
        if (enabled.value) append(tag, message())
    }

    /** A callback for components that take a diagnostics sink; `null` while debug mode is off. */
    fun sink(tag: String): ((String) -> Unit)? =
        if (enabled.value) { message -> append(tag, message) } else null

    @PublishedApi
    internal fun append(tag: String, message: String) {
        val entry = Entry(nextId.getAndIncrement(), System.currentTimeMillis(), tag, message)
        _entries.update { (it + entry).takeLast(CAPACITY) }
    }
}
