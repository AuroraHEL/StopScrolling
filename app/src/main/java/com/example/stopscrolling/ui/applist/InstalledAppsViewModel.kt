package com.example.stopscrolling.ui.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stopscrolling.data.InstalledApp
import com.example.stopscrolling.data.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstalledAppsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        InstalledAppsRepository(application)

    private val _apps =
        MutableStateFlow<List<InstalledApp>>(emptyList())

    val apps: StateFlow<List<InstalledApp>> =
        _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    val error: StateFlow<String?> =
        _error.asStateFlow()

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getInstalledApps()
                        .sortedWith(
                            compareBy(
                                String.CASE_INSENSITIVE_ORDER
                            ) { it.name }
                        )
                }

                _apps.value = result

            } catch (e: Exception) {
                _error.value =
                    e.message ?: "Failed to load installed apps"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
