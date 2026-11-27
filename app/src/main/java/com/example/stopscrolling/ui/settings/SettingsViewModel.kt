package com.example.stopscrolling.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.challenge.ChallengeType
import com.example.stopscrolling.data.CapabilityPreferences
import com.example.stopscrolling.data.repository.CapabilityPreferencesRepository
import com.example.stopscrolling.permission.AndroidChallengePermissionChecker
import com.example.stopscrolling.permission.ChallengePermissionChecker
import com.example.stopscrolling.permission.PermissionStatus
import com.example.stopscrolling.permission.PermissionStatusRules
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
    val hasError: Boolean = false,
    // US-04: state of the permission needed by the walking challenge
    val walkingPermission: PermissionStatus = PermissionStatus.NOT_NEEDED,
    // US-04: the short explanation shown before the system permission question
    val showPermissionExplanation: Boolean = false
)

/**
 * Settings screen (US-03): the user can change the capabilities chosen
 * during onboarding. Every change is saved at once.
 *
 * US-04: the walking permission is asked only here, when the user turns the
 * walking challenge on, never during onboarding.
 */
class SettingsViewModel(
    private val repository: CapabilityPreferencesRepository,
    private val permissionChecker: ChallengePermissionChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()

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
        val accepted = change { it.withCanWalk(enabled) }

        // US-04: ask only when the user turns walking on and we can still ask
        if (
            accepted &&
            enabled &&
            PermissionStatusRules.canAsk(_uiState.value.walkingPermission)
        ) {
            _uiState.update { it.copy(showPermissionExplanation = true) }
        }
    }

    fun setCanDoMentalArithmetic(enabled: Boolean) {
        change { it.withCanDoMentalArithmetic(enabled) }
    }

    // ---------- US-04: walking permission ----------

    /** The permission to ask for the walking challenge, or null if none is needed. */
    fun permissionToRequest(): String? =
        permissionChecker.permissionFor(ChallengeType.WALKING)

    /**
     * Looks at the real permission again. Called every time the screen is shown:
     * the user may have changed it in the Android settings.
     */
    fun refreshPermissions() {
        val needed = permissionToRequest() != null
        val granted = permissionChecker.isGranted(ChallengeType.WALKING)

        _uiState.update {
            it.copy(
                walkingPermission = PermissionStatusRules.statusOnRefresh(
                    needed = needed,
                    granted = granted,
                    previous = it.walkingPermission
                )
            )
        }
    }

    /** The user pressed "Allow" or "Try again": explain first, then ask. */
    fun requestWalkingPermission() {
        if (PermissionStatusRules.canAsk(_uiState.value.walkingPermission)) {
            _uiState.update { it.copy(showPermissionExplanation = true) }
        }
    }

    fun dismissPermissionExplanation() {
        _uiState.update { it.copy(showPermissionExplanation = false) }
    }

    /**
     * Called with the answer of the system permission question.
     * [shouldShowRationale] is what Android says right after the answer.
     */
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        _uiState.update {
            it.copy(
                walkingPermission = PermissionStatusRules.statusAfterRequest(
                    granted = granted,
                    shouldShowRationale = shouldShowRationale
                )
            )
        }
    }

    /**
     * [transform] returns null when the change would disable every challenge:
     * the choice is refused, nothing is saved and a message is shown.
     * Returns true if the change was accepted.
     */
    private fun change(
        transform: (CapabilityPreferences) -> CapabilityPreferences?
    ): Boolean {
        val current = _uiState.value
        if (current.isLoading) return false

        val updated = transform(current.preferences)

        if (updated == null) {
            _uiState.update { it.copy(showNoChallengeMessage = true) }
            return false
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

        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[
                    ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                ] as StopScrollingApp

                SettingsViewModel(
                    repository = application.capabilityPreferencesRepository,
                    permissionChecker = AndroidChallengePermissionChecker(application)
                )
            }
        }
    }
}
