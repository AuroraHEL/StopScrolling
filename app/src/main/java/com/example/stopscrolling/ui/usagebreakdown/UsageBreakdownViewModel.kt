package com.example.stopscrolling.ui.usagebreakdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.hasUsageStatsPermission
import com.example.stopscrolling.usage.AppUsageBreakdownItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the per-app daily usage breakdown screen (US-28). */
data class UsageBreakdownUiState(
    val items: List<AppUsageBreakdownItem> = emptyList(),
    val totalDurationMillis: Long = 0L,
    val isLoading: Boolean = true,
    val hasUsagePermission: Boolean = true
)

/**
 * Loads today's per-app usage breakdown off the main thread for [UsageBreakdownScreen] (US-28).
 */
class UsageBreakdownViewModel(
    private val hasPermission: () -> Boolean,
    private val loadBreakdown: () -> List<AppUsageBreakdownItem>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsageBreakdownUiState())
    val uiState: StateFlow<UsageBreakdownUiState> = _uiState.asStateFlow()

    /** Re-checks Usage Access permission and loads today's sorted per-app usage list. */
    fun refreshBreakdown() {
        if (!hasPermission()) {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    totalDurationMillis = 0L,
                    isLoading = false,
                    hasUsagePermission = false
                )
            }
            return
        }

        if (_uiState.value.items.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasUsagePermission = true
                )
            }
        }

        viewModelScope.launch {
            try {
                val breakdown = withContext(ioDispatcher) {
                    loadBreakdown()
                }
                val totalMillis = breakdown.sumOf { it.durationMillis.coerceAtLeast(0L) }
                _uiState.update {
                    it.copy(
                        items = breakdown,
                        totalDurationMillis = totalMillis,
                        isLoading = false,
                        hasUsagePermission = true
                    )
                }
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        items = emptyList(),
                        totalDurationMillis = 0L,
                        isLoading = false,
                        hasUsagePermission = false
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StopScrollingApp
                UsageBreakdownViewModel(
                    hasPermission = { hasUsageStatsPermission(app) },
                    loadBreakdown = { app.appUsageBreakdownProvider.getTodayUsageBreakdown() }
                )
            }
        }
    }
}