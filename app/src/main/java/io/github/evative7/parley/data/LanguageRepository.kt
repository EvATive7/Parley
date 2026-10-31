package io.github.evative7.parley.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.evative7.parley.model.Lang
import io.github.evative7.parley.state.LangPair
import io.github.evative7.parley.state.LangPairStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.languageDataStore by preferencesDataStore("languages")

/** The language pair last picked in the language bar. */
@Singleton
class LanguageRepository @Inject constructor(@ApplicationContext context: Context) : LangPairStore {
    private val store = context.languageDataStore
    private val first = stringPreferencesKey("first")
    private val second = stringPreferencesKey("second")

    /** The saved pair as it changes, for readers that can't suspend; null until one is saved. */
    val current: StateFlow<LangPair?> = store.data
        .map { decode(it[first], it[second]) }
        .catch { emit(null) }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.Eagerly, null)

    override suspend fun load(): LangPair? = runCatching {
        val p = store.data.first()
        decode(p[first], p[second])
    }.getOrNull()

    private fun decode(a: String?, b: String?): LangPair? {
        val pair = LangPair(Lang(a ?: return null), Lang(b ?: return null))
        // A language dropped from the bar since, or a corrupted pair, falls back to the default.
        return pair.takeIf { it.first in Lang.targets && it.second in Lang.targets && it.first != it.second }
    }

    override suspend fun save(langs: LangPair) {
        runCatching {
            store.edit {
                it[first] = langs.first.tag
                it[second] = langs.second.tag
            }
        }
    }
}
