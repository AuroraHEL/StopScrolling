package com.example.stopscrolling.ui.onboarding

import com.example.stopscrolling.data.CapabilityPreferences
import com.example.stopscrolling.data.repository.CapabilityPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapabilitySelectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: FakeCapabilityRepository
    private lateinit var viewModel: CapabilitySelectionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCapabilityRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default capabilities are enabled`() = runTest {
        viewModel = CapabilitySelectionViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertTrue(state.preferences.canWalk)
        assertTrue(state.preferences.canDoMentalArithmetic)
        assertFalse(state.isLoading)
        assertFalse(state.isCompleted)
    }

    @Test
    fun `saved preferences are loaded correctly`() = runTest {
        repository.updatePreferences(
            CapabilityPreferences(
                canWalk = false,
                canDoMentalArithmetic = true
            )
        )

        viewModel = CapabilitySelectionViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.preferences.canWalk)
        assertTrue(
            viewModel.uiState.value.preferences.canDoMentalArithmetic
        )
    }

    @Test
    fun `walking capability can be toggled`() = runTest {
        viewModel = CapabilitySelectionViewModel(repository)
        advanceUntilIdle()

        viewModel.setCanWalk(false)
        assertFalse(viewModel.uiState.value.preferences.canWalk)

        viewModel.setCanWalk(true)
        assertTrue(viewModel.uiState.value.preferences.canWalk)
    }

    @Test
    fun `mental arithmetic capability can be toggled`() = runTest {
        viewModel = CapabilitySelectionViewModel(repository)
        advanceUntilIdle()

        viewModel.setCanDoMentalArithmetic(false)
        assertFalse(
            viewModel.uiState.value.preferences.canDoMentalArithmetic
        )

        viewModel.setCanDoMentalArithmetic(true)
        assertTrue(
            viewModel.uiState.value.preferences.canDoMentalArithmetic
        )
    }

    @Test
    fun `continue saves selected capabilities`() = runTest {
        viewModel = CapabilitySelectionViewModel(repository)
        advanceUntilIdle()

        viewModel.setCanWalk(false)
        viewModel.setCanDoMentalArithmetic(true)

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertFalse(repository.preferences.value.canWalk)
        assertTrue(
            repository.preferences.value.canDoMentalArithmetic
        )
        assertTrue(repository.isOnboardingCompleted.value)
        assertTrue(viewModel.uiState.value.isCompleted)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `completed onboarding is recognized`() = runTest {
        repository.completeOnboarding(
            CapabilityPreferences()
        )

        viewModel = CapabilitySelectionViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCompleted)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `saving failure updates error state`() = runTest {
        viewModel = CapabilitySelectionViewModel(repository)
        advanceUntilIdle()

        repository.shouldFail = true

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isCompleted)
        assertFalse(repository.isOnboardingCompleted.value)
    }

    private class FakeCapabilityRepository :
        CapabilityPreferencesRepository {

        override val preferences =
            MutableStateFlow(CapabilityPreferences())

        override val isOnboardingCompleted =
            MutableStateFlow(false)

        var shouldFail = false

        override suspend fun updatePreferences(
            preferences: CapabilityPreferences
        ) {
            this.preferences.value = preferences
        }

        override suspend fun completeOnboarding(
            preferences: CapabilityPreferences
        ) {
            if (shouldFail) {
                throw IllegalStateException("Saving failed")
            }

            this.preferences.value = preferences
            isOnboardingCompleted.value = true
        }
    }
}
