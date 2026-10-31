package io.github.evative7.parley.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.state.LangPair
import io.github.evative7.parley.state.LangPairStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.languageDataStore by preferencesDataStore("languages")

/** The language pair last picked in the language bar. */
@Singleton
class LanguageRepository @Inject constructor(@ApplicationContext context: Context) : LangPairStore {
    private val store = context.languageDataStore
    private val first = stringPreferencesKey("first")
    private val second = stringPreferencesKey("second")

    override suspend fun load(): LangPair? = runCatching {
        val p = store.data.first()
        val a = p[first]?.let(::Lang) ?: return null
        val b = p[second]?.let(::Lang) ?: return null
        // A language dropped from the bar since, or a corrupted pair, falls back to the default.
        LangPair(a, b).takeIf { a in Lang.targets && b in Lang.targets && a != b }
    }.getOrNull()

    override suspend fun save(langs: LangPair) {
        runCatching {
            store.edit {
                it[first] = langs.first.tag
                it[second] = langs.second.tag
            }
        }
    }
}
