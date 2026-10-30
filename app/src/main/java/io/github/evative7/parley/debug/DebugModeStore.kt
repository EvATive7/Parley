package io.github.evative7.parley.debug

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.debugDataStore by preferencesDataStore("debug")

/** Persists the debug mode switch and mirrors it into [DebugLog]. */
@Singleton
class DebugModeStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.debugDataStore
    private val key = booleanPreferencesKey("debug_mode")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            store.data.map { it[key] ?: false }.catch { emit(false) }.collect(DebugLog::setEnabled)
        }
    }

    suspend fun setEnabled(value: Boolean) {
        DebugLog.setEnabled(value)
        store.edit { it[key] = value }
    }
}
