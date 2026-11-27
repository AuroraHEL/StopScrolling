package com.example.stopscrolling.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.data.CapabilityPreferences
import com.example.stopscrolling.data.repository.CapabilityPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: CapabilityPreferences = CapabilityPreferences(),
    val isLoading: Boolean = true,
    // US-03: true when the user tried to turn off the last enabled challenge
    val showNoChallengeMessage: Boolean = false,
    val hasError: Boolean = false
)

/**
 * Settings screen (US-03): the user can change the capabilities chosen
 * during onboarding. Every change is saved at once.
 */
class SettingsViewModel(
    private val repository: CapabilityPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // The screen always shows what is saved
        viewModelScope.launch {
            repository.preferences
                .catch {
                    _uiState.update {
                        it.copy(isLoading = false, hasError = true)
                    }
                }
                .collect { saved ->
                    _uiState.update {
                        it.copy(preferences = saved, isLoading = false)
                    }
                }
        }
    }

    fun setCanWalk(enabled: Boolean) {
        change { it.withCanWalk(enabled) }
    }

    fun setCanDoMentalArithmetic(enabled: Boolean) {
        change { it.withCanDoMentalArithmetic(enabled) }
    }

    /**
     * [transform] returns null when the change would disable every challenge:
     * the choice is refused, nothing is saved and a message is shown.
     */
    private fun change(
        transform: (CapabilityPreferences) -> CapabilityPreferences?
    ) {
        val current = _uiState.value
        if (current.isLoading) return

        val updated = transform(current.preferences)

        if (updated == null) {
            _uiState.update { it.copy(showNoChallengeMessage = true) }
            return
        }

        _uiState.update {
            it.copy(showNoChallengeMessage = false, hasError = false)
        }

        viewModelScope.launch {
            try {
                repository.updatePreferences(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(hasError = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as StopScrollingApp

                SettingsViewModel(
                    application.capabilityPreferencesRepository
                )
            }
        }
    }
}
