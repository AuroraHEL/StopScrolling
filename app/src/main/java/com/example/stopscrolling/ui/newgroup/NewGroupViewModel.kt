package com.example.stopscrolling.ui.newgroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.data.InstalledApp
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.InstalledAppsRepository
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
    val isLoading: Boolean = true,
    val nameError: Boolean = false,
    val appsError: Boolean = false,
    val saved: Boolean = false
)

class NewGroupViewModel(
    private val groupRepository: AppGroupRepository,
    private val installedAppsRepository: InstalledAppsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGroupUiState())
    val uiState: StateFlow<NewGroupUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                installedAppsRepository.getInstalledApps()
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
            _uiState.update { it.copy(apps = apps, isLoading = false) }
        }
    }

    fun onNameChange(newName: String) {
        _uiState.update { it.copy(name = newName, nameError = false) }
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

    fun onSaveClick() {
        val state = _uiState.value
        val nameInvalid = state.name.isBlank()
        val appsInvalid = state.selectedPackages.isEmpty()
        if (nameInvalid || appsInvalid) {
            _uiState.update { it.copy(nameError = nameInvalid, appsError = appsInvalid) }
            return
        }
        viewModelScope.launch {
            groupRepository.createGroup(state.name, state.selectedPackages.toList())
            _uiState.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StopScrollingApp
                NewGroupViewModel(app.appGroupRepository, app.installedAppsRepository)
            }
        }
    }
}