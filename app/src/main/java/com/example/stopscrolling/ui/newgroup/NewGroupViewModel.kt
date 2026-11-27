package com.example.stopscrolling.ui.newgroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.data.InstalledApp
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.InstalledAppsRepository
import com.example.stopscrolling.util.WeeklyLimitError
import com.example.stopscrolling.util.WeeklyLimitValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NewGroupUiState(
    val name: String = "",
    val apps: List<InstalledApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),

    // US-12/17: Limit settings
    val timeLimit: Int = 0,
    val limitType: LimitType = LimitType.HARD,

    // US-13: optional weekly limit, typed in hours and minutes
    val weeklyEnabled: Boolean = false,
    val weeklyHoursText: String = "",
    val weeklyMinutesText: String = "",
    val weeklyLimitError: WeeklyLimitError? = null,

    val isLoading: Boolean = true,
    val nameError: Boolean = false,
    val appsError: Boolean = false,
    val saved: Boolean = false
)

class NewGroupViewModel(
    private val groupRepository: AppGroupRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    // Injectable so that tests can replace Dispatchers.IO with a test dispatcher
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGroupUiState())
    val uiState: StateFlow<NewGroupUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(ioDispatcher) {
                installedAppsRepository.getInstalledApps()
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
            _uiState.update {
                it.copy(apps = apps, isLoading = false)
            }
        }
    }

    fun onNameChange(newName: String) {
        _uiState.update {
            it.copy(name = newName, nameError = false)
        }
    }

    fun onAppToggle(packageName: String) {
        _uiState.update { state ->
            val selection = if (packageName in state.selectedPackages) {
                state.selectedPackages - packageName
            } else {
                state.selectedPackages + packageName
            }
            state.copy(selectedPackages = selection, appsError = false)
        }
    }

    fun onTimeLimitChange(minutes: Int) {
        if (minutes >= 0) {
            _uiState.update {
                it.copy(timeLimit = minutes)
            }
        }
    }

    fun onLimitTypeChange(type: LimitType) {
        _uiState.update {
            it.copy(limitType = type)
        }
    }

    // US-13: the three functions below change the weekly limit fields.
    // Typing in a field removes the error message.
    fun onWeeklyEnabledChange(enabled: Boolean) {
        _uiState.update {
            it.copy(weeklyEnabled = enabled, weeklyLimitError = null)
        }
    }

    fun onWeeklyHoursChange(text: String) {
        _uiState.update {
            it.copy(weeklyHoursText = text, weeklyLimitError = null)
        }
    }

    fun onWeeklyMinutesChange(text: String) {
        _uiState.update {
            it.copy(weeklyMinutesText = text, weeklyLimitError = null)
        }
    }

    fun onSaveClick() {
        val state = _uiState.value
        val nameInvalid = state.name.isBlank()
        val appsInvalid = state.selectedPackages.isEmpty()

        // US-13: 0 means "no weekly limit". When the switch is on, the fields must be valid.
        val weeklyMinutes: Int? = if (state.weeklyEnabled) {
            WeeklyLimitValidator.parseMinutes(state.weeklyHoursText, state.weeklyMinutesText)
        } else {
            0
        }
        val weeklyError: WeeklyLimitError? = if (state.weeklyEnabled) {
            WeeklyLimitValidator.validate(weeklyMinutes, state.timeLimit)
        } else {
            null
        }

        if (nameInvalid || appsInvalid || weeklyError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameInvalid,
                    appsError = appsInvalid,
                    weeklyLimitError = weeklyError
                )
            }
            return
        }

        viewModelScope.launch {
            groupRepository.createGroup(
                name = state.name,
                packageNames = state.selectedPackages.toList(),
                timeLimit = state.timeLimit,
                limitType = state.limitType,
                weeklyLimit = weeklyMinutes ?: 0
            )

            _uiState.update {
                it.copy(saved = true)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StopScrollingApp
                NewGroupViewModel(
                    app.appGroupRepository,
                    app.installedAppsRepository
                )
            }
        }
    }
}
