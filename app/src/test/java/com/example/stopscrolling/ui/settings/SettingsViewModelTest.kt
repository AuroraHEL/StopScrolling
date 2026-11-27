package com.example.stopscrolling.ui.settings

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: FakeCapabilityRepository

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
    fun `saved preferences are shown`() = runTest {
        repository.preferences.value = CapabilityPreferences(canWalk = false)

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.preferences.canWalk)
        assertTrue(state.preferences.canDoMentalArithmetic)
    }

    @Test
    fun `turning off walking saves the new preferences`() = runTest {
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setCanWalk(false)
        advanceUntilIdle()

        assertEquals(
            CapabilityPreferences(canWalk = false, canDoMentalArithmetic = true),
            repository.preferences.value
        )
        assertFalse(viewModel.uiState.value.preferences.canWalk)
        assertFalse(viewModel.uiState.value.showNoChallengeMessage)
    }

    @Test
    fun `turning off math saves the new preferences`() = runTest {
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setCanDoMentalArithmetic(false)
        advanceUntilIdle()

        assertFalse(repository.preferences.value.canDoMentalArithmetic)
        assertTrue(repository.preferences.value.canWalk)
    }

    @Test
    fun `the last enabled challenge cannot be turned off`() = runTest {
        repository.preferences.value = CapabilityPreferences(canWalk = false)

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setCanDoMentalArithmetic(false)
        advanceUntilIdle()

        // Refused: nothing saved, the user sees a message
        assertTrue(repository.preferences.value.canDoMentalArithmetic)
        assertEquals(0, repository.saveCount)
        assertTrue(viewModel.uiState.value.showNoChallengeMessage)
    }

    @Test
    fun `the message disappears after a valid change`() = runTest {
        repository.preferences.value = CapabilityPreferences(canWalk = false)

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.setCanDoMentalArithmetic(false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showNoChallengeMessage)

        viewModel.setCanWalk(true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showNoChallengeMessage)
        assertTrue(repository.preferences.value.canWalk)
    }

    @Test
    fun `saving failure shows an error`() = runTest {
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        repository.shouldFail = true

        viewModel.setCanWalk(false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasError)
        assertTrue(repository.preferences.value.canWalk)
    }

    @Test
    fun `changes are ignored while the preferences are loading`() = runTest {
        val viewModel = SettingsViewModel(repository)

        // advanceUntilIdle() was not called: the screen is still loading
        viewModel.setCanWalk(false)
        advanceUntilIdle()

        assertEquals(0, repository.saveCount)
    }

    private class FakeCapabilityRepository : CapabilityPreferencesRepository {

        override val preferences = MutableStateFlow(CapabilityPreferences())

        override val isOnboardingCompleted = MutableStateFlow(false)

        var shouldFail = false
        var saveCount = 0

        override suspend fun updatePreferences(
            preferences: CapabilityPreferences
        ) {
            if (shouldFail) {
                throw IllegalStateException("Saving failed")
            }

            saveCount++
            this.preferences.value = preferences
        }

        override suspend fun completeOnboarding(
            preferences: CapabilityPreferences
        ) {
            this.preferences.value = preferences
            isOnboardingCompleted.value = true
        }
    }
}
