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
import com.example.stopscrolling.data.PendingGroupChange
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

    // ---------- Observe Groups ----------

    val groups: StateFlow<List<GroupWithApps>> =
        repository.observeGroups()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    // ---------- US-09: Observe Pending Changes ----------

    val pendingChanges: StateFlow<List<PendingGroupChange>> =
        repository.observePendingChanges()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    // ---------- Update Limit ----------

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

    // ---------- US-09: Delete Group ----------

    private val _isDeletingGroup = MutableStateFlow(false)
    val isDeletingGroup: StateFlow<Boolean> =
        _isDeletingGroup.asStateFlow()

    private val _deleteGroupError = MutableStateFlow(false)
    val deleteGroupError: StateFlow<Boolean> =
        _deleteGroupError.asStateFlow()

    fun deleteGroup(
        groupId: Int,
        onSuccess: () -> Unit
    ) {
        if (_isDeletingGroup.value) return

        _deleteGroupError.value = false
        _isDeletingGroup.value = true

        viewModelScope.launch {
            try {
                val deleted = repository.deleteGroup(groupId)

                if (deleted) {
                    // Hard Limit: deletion may be scheduled.
                    // Other groups: deletion happens immediately.
                    onSuccess()
                } else {
                    _deleteGroupError.value = true
                }
            } catch (e: Exception) {
                _deleteGroupError.value = true
            } finally {
                _isDeletingGroup.value = false
            }
        }
    }

    fun clearDeleteGroupError() {
        _deleteGroupError.value = false
    }

    // ---------- US-09 Task 3: Update Group ----------

    private val _isUpdatingGroup = MutableStateFlow(false)
    val isUpdatingGroup: StateFlow<Boolean> =
        _isUpdatingGroup.asStateFlow()

    private val _updateGroupError = MutableStateFlow(false)
    val updateGroupError: StateFlow<Boolean> =
        _updateGroupError.asStateFlow()

    fun updateGroup(
        groupId: Int,
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: LimitType,
        onSuccess: () -> Unit
    ) {
        if (_isUpdatingGroup.value) return

        _updateGroupError.value = false
        _isUpdatingGroup.value = true

        viewModelScope.launch {
            try {
                val updated = repository.updateGroup(
                    groupId = groupId,
                    name = name,
                    packageNames = packageNames,
                    timeLimit = timeLimit,
                    limitType = limitType
                )

                if (updated) {
                    onSuccess()
                } else {
                    _updateGroupError.value = true
                }
            } catch (e: Exception) {
                _updateGroupError.value = true
            } finally {
                _isUpdatingGroup.value = false
            }
        }
    }

    fun clearUpdateGroupError() {
        _updateGroupError.value = false
    }

    // ---------- Factory ----------

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StopScrollingApp
                DashboardViewModel(app.appGroupRepository)
            }
        }
    }
}
