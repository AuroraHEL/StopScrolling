package com.example.stopscrolling.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.hasUsageStatsPermission
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the Dashboard daily screen-time card (US-27). */
data class DailyUsageUiState(
    val totalMinutes: Int = 0,
    val isLoading: Boolean = true,
    val hasUsagePermission: Boolean = true
)

/**
 * Loads today's total phone usage off the main thread and exposes permission state
 * for the Dashboard summary card (US-27).
 */
class DailyUsageViewModel(
    private val hasPermission: () -> Boolean,
    private val loadTodayTotalMillis: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyUsageUiState())
    val uiState: StateFlow<DailyUsageUiState> = _uiState.asStateFlow()

    /** Re-checks Usage Access permission and refreshes today's total screen time. */
    fun refreshUsage() {
        if (!hasPermission()) {
            _uiState.update {
                it.copy(
                    totalMinutes = 0,
                    isLoading = false,
                    hasUsagePermission = false
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                val totalMillis = withContext(ioDispatcher) {
                    loadTodayTotalMillis()
                }
                val totalMinutes = (totalMillis.coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt()
                _uiState.update {
                    it.copy(
                        totalMinutes = totalMinutes,
                        isLoading = false,
                        hasUsagePermission = true
                    )
                }
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        totalMinutes = 0,
                        isLoading = false,
                        hasUsagePermission = false
                    )
                }
            }
        }
    }

    companion object {
        const val REFRESH_INTERVAL_MILLIS = 60_000L
        private const val MILLIS_PER_MINUTE = 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StopScrollingApp
                DailyUsageViewModel(
                    hasPermission = { hasUsageStatsPermission(app) },
                    loadTodayTotalMillis = { app.dailyUsageProvider.getTodayTotalUsageMillis() }
                )
            }
        }
    }
}