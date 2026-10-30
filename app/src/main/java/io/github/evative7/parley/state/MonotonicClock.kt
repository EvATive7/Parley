package io.github.evative7.parley.state

fun interface MonotonicClock {
    fun nowMs(): Long
}
