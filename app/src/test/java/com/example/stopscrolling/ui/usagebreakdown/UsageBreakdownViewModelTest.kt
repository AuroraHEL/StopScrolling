package com.example.stopscrolling.ui.usagebreakdown

import com.example.stopscrolling.usage.AppUsageBreakdownItem
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
class UsageBreakdownViewModelTest {

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
    fun initialState_isLoading() {
        val viewModel = UsageBreakdownViewModel(
            hasPermission = { true },
            loadBreakdown = { emptyList() },
            ioDispatcher = testDispatcher
        )

        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun refreshBreakdown_withPermission_populatesSortedItemsAndTotal() = runTest(testDispatcher) {
        val sampleItems = listOf(
            AppUsageBreakdownItem(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                icon = null,
                durationMillis = 60 * 60_000L,
                proportion = 0.75f
            ),
            AppUsageBreakdownItem(
                packageName = "com.instagram.android",
                appName = "Instagram",
                icon = null,
                durationMillis = 20 * 60_000L,
                proportion = 0.25f
            )
        )

        val viewModel = UsageBreakdownViewModel(
            hasPermission = { true },
            loadBreakdown = { sampleItems },
            ioDispatcher = testDispatcher
        )

        viewModel.refreshBreakdown()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasUsagePermission)
        assertEquals(sampleItems, state.items)
        assertEquals(80 * 60_000L, state.totalDurationMillis)
    }

    @Test
    fun refreshBreakdown_whenNoUsageToday_exposesEmptyListAndStopsLoading() =
        runTest(testDispatcher) {
            val viewModel = UsageBreakdownViewModel(
                hasPermission = { true },
                loadBreakdown = { emptyList() },
                ioDispatcher = testDispatcher
            )

            viewModel.refreshBreakdown()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.hasUsagePermission)
            assertTrue(state.items.isEmpty())
            assertEquals(0L, state.totalDurationMillis)
        }

    @Test
    fun refreshBreakdown_withoutPermission_showsFallbackWithoutLoading() =
        runTest(testDispatcher) {
            var providerCalled = false
            val viewModel = UsageBreakdownViewModel(
                hasPermission = { false },
                loadBreakdown = {
                    providerCalled = true
                    emptyList()
                },
                ioDispatcher = testDispatcher
            )

            viewModel.refreshBreakdown()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(providerCalled)
            assertFalse(state.isLoading)
            assertFalse(state.hasUsagePermission)
            assertTrue(state.items.isEmpty())
        }

    @Test
    fun refreshBreakdown_whenSecurityExceptionThrown_fallsBackToMissingPermission() =
        runTest(testDispatcher) {
            val viewModel = UsageBreakdownViewModel(
                hasPermission = { true },
                loadBreakdown = { throw SecurityException("Permission revoked") },
                ioDispatcher = testDispatcher
            )

            viewModel.refreshBreakdown()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.hasUsagePermission)
            assertTrue(state.items.isEmpty())
        }
}