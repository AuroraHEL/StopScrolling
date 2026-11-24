package com.example.stopscrolling.ui.onboarding

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CapabilitySelectionUiState(
    val preferences: CapabilityPreferences = CapabilityPreferences(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false,
    val hasError: Boolean = false
)

class CapabilitySelectionViewModel(
    private val repository: CapabilityPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CapabilitySelectionUiState()
    )

    val uiState = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, hasError = false)
            }

            try {
                val preferences = repository.preferences.first()
                val completed = repository.isOnboardingCompleted.first()

                _uiState.update {
                    it.copy(
                        preferences = preferences,
                        isLoading = false,
                        isCompleted = completed
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasError = true
                    )
                }
            }
        }
    }

    fun setCanWalk(enabled: Boolean) {
        _uiState.update {
            if (it.isLoading || it.isSaving || it.isCompleted) {
                it
            } else {
                it.copy(
                    preferences = it.preferences.copy(
                        canWalk = enabled
                    )
                )
            }
        }
    }

    fun setCanDoMentalArithmetic(enabled: Boolean) {
        _uiState.update {
            if (it.isLoading || it.isSaving || it.isCompleted) {
                it
            } else {
                it.copy(
                    preferences = it.preferences.copy(
                        canDoMentalArithmetic = enabled
                    )
                )
            }
        }
    }

    fun completeOnboarding() {
        val currentState = _uiState.value

        if (
            currentState.isLoading ||
            currentState.isSaving ||
            currentState.isCompleted
        ) return

        _uiState.update {
            it.copy(isSaving = true, hasError = false)
        }

        viewModelScope.launch {
            try {
                repository.completeOnboarding(
                    currentState.preferences
                )

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isCompleted = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        hasError = true
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as StopScrollingApp

                CapabilitySelectionViewModel(
                    application.capabilityPreferencesRepository
                )
            }
        }
    }
}
