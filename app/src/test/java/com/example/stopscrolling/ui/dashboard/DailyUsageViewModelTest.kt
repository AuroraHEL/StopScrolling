package com.example.stopscrolling.ui.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyUsageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshUsage_withPermission_updatesTotalMinutes() = runTest(testDispatcher) {
        val viewModel = DailyUsageViewModel(
            hasPermission = { true },
            loadTodayTotalMillis = { 134 * 60_000L },
            ioDispatcher = testDispatcher
        )

        viewModel.refreshUsage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasUsagePermission)
        assertFalse(state.isLoading)
        assertEquals(134, state.totalMinutes)
    }

    @Test
    fun refreshUsage_withoutPermission_showsFallbackWithoutCallingProvider() =
        runTest(testDispatcher) {
            var providerCalled = false
            val viewModel = DailyUsageViewModel(
                hasPermission = { false },
                loadTodayTotalMillis = {
                    providerCalled = true
                    50 * 60_000L
                },
                ioDispatcher = testDispatcher
            )

            viewModel.refreshUsage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(providerCalled)
            assertFalse(state.hasUsagePermission)
            assertFalse(state.isLoading)
            assertEquals(0, state.totalMinutes)
        }

    @Test
    fun refreshUsage_whenSecurityExceptionThrown_fallsBackGracefully() =
        runTest(testDispatcher) {
            val viewModel = DailyUsageViewModel(
                hasPermission = { true },
                loadTodayTotalMillis = { throw SecurityException("Revoked") },
                ioDispatcher = testDispatcher
            )

            viewModel.refreshUsage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.hasUsagePermission)
            assertFalse(state.isLoading)
            assertEquals(0, state.totalMinutes)
        }

    @Test
    fun refreshUsage_whenPermissionRegranted_recoversAndShowsUsage() =
        runTest(testDispatcher) {
            var granted = false
            val viewModel = DailyUsageViewModel(
                hasPermission = { granted },
                loadTodayTotalMillis = { 45 * 60_000L },
                ioDispatcher = testDispatcher
            )

            viewModel.refreshUsage()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.hasUsagePermission)

            granted = true
            viewModel.refreshUsage()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasUsagePermission)
            assertEquals(45, viewModel.uiState.value.totalMinutes)
        }
}