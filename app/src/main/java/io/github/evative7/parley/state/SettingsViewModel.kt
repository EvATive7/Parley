package io.github.evative7.parley.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.evative7.parley.data.ServiceSettings
import io.github.evative7.parley.data.SettingsRepository
import io.github.evative7.parley.debug.DebugLog
import io.github.evative7.parley.debug.DebugModeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val debugModeStore: DebugModeStore,
) : ViewModel() {
    val settings = repository.settings
    val debugMode = DebugLog.enabled
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed = _saveFailed.asStateFlow()

    /** Saves the services and the debug mode switch together, so discarding the page undoes both. */
    fun save(value: ServiceSettings, debugMode: Boolean, onSaved: () -> Unit) {
        if (_saving.value || value.validationError() != null) return
        viewModelScope.launch {
            _saving.value = true
            _saveFailed.value = false
            try {
                repository.save(value)
                if (debugMode != DebugLog.enabled.value) debugModeStore.setEnabled(debugMode)
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.value = true
            } finally {
                _saving.value = false
            }
        }
    }
}
