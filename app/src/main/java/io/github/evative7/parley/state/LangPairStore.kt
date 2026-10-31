package io.github.evative7.parley.state

/** Remembers the language pair across launches. */
interface LangPairStore {
    /** The last saved pair, or null if none was saved or it can't be read. */
    suspend fun load(): LangPair?
    suspend fun save(langs: LangPair)
}
