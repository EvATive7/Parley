package io.github.evative7.parley.audio

import kotlinx.coroutines.flow.Flow

fun interface AudioSource {
    fun frames(): Flow<ShortArray>
}
