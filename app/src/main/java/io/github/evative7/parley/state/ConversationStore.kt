package io.github.evative7.parley.state

/** Stores the settled conversation independently from service settings. */
interface ConversationStore {
    /** The saved cards, or null when no conversation has been saved yet. */
    suspend fun load(): List<CardState>?
    suspend fun save(cards: List<CardState>)
}
