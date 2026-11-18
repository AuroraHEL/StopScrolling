package com.example.stopscrolling.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.repository.AppGroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: AppGroupRepository
) : ViewModel() {

    val groups: StateFlow<List<GroupWithApps>> =
        repository.observeGroups()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    private val _isUpdatingLimit = MutableStateFlow(false)
    val isUpdatingLimit: StateFlow<Boolean> =
        _isUpdatingLimit.asStateFlow()

    private val _limitUpdateError = MutableStateFlow(false)
    val limitUpdateError: StateFlow<Boolean> =
        _limitUpdateError.asStateFlow()

    fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType,
        onSuccess: () -> Unit
    ) {
        if (_isUpdatingLimit.value) return

        _limitUpdateError.value = false
        _isUpdatingLimit.value = true

        viewModelScope.launch {
            try {
                val updated = repository.updateLimit(
                    groupId = groupId,
                    timeLimit = timeLimit,
                    limitType = limitType
                )

                if (updated) {
                    onSuccess()
                } else {
                    _limitUpdateError.value = true
                }
            } catch (e: Exception) {
                _limitUpdateError.value = true
            } finally {
                _isUpdatingLimit.value = false
            }
        }
    }

    fun clearLimitUpdateError() {
        _limitUpdateError.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StopScrollingApp
                DashboardViewModel(app.appGroupRepository)
            }
        }
    }
}
