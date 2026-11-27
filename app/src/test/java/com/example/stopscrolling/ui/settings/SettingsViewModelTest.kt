package com.example.stopscrolling.ui.settings

import com.example.stopscrolling.data.CapabilityPreferences
import com.example.stopscrolling.data.repository.CapabilityPreferencesRepository
import com.example.stopscrolling.permission.FakeChallengePermissionChecker
import com.example.stopscrolling.permission.PermissionStatus
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
    private lateinit var permissionChecker: FakeChallengePermissionChecker

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCapabilityRepository()
        permissionChecker = FakeChallengePermissionChecker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saved preferences are shown`() = runTest {
        repository.preferences.value = CapabilityPreferences(canWalk = false)

        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.preferences.canWalk)
        assertTrue(state.preferences.canDoMentalArithmetic)
    }

    @Test
    fun `turning off walking saves the new preferences`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
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
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.setCanDoMentalArithmetic(false)
        advanceUntilIdle()

        assertFalse(repository.preferences.value.canDoMentalArithmetic)
        assertTrue(repository.preferences.value.canWalk)
    }

    @Test
    fun `the last enabled challenge cannot be turned off`() = runTest {
        repository.preferences.value = CapabilityPreferences(canWalk = false)

        val viewModel = SettingsViewModel(repository, permissionChecker)
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

        val viewModel = SettingsViewModel(repository, permissionChecker)
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
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        repository.shouldFail = true

        viewModel.setCanWalk(false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasError)
        assertTrue(repository.preferences.value.canWalk)
    }

    @Test
    fun `changes are ignored while the preferences are loading`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)

        // advanceUntilIdle() was not called: the screen is still loading
        viewModel.setCanWalk(false)
        advanceUntilIdle()

        assertEquals(0, repository.saveCount)
    }

    // ---------- US-04: walking permission ----------

    @Test
    fun `permission is not asked when the screen opens`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        assertEquals(PermissionStatus.NOT_GRANTED, viewModel.uiState.value.walkingPermission)
        assertFalse(viewModel.uiState.value.showPermissionExplanation)
    }

    @Test
    fun `turning walking on shows the explanation before the system question`() = runTest {
        repository.preferences.value = CapabilityPreferences(canWalk = false)
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.setCanWalk(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showPermissionExplanation)
        assertTrue(repository.preferences.value.canWalk)
    }

    @Test
    fun `turning walking off never asks for the permission`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.setCanWalk(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showPermissionExplanation)
    }

    @Test
    fun `no explanation when the permission is already granted`() = runTest {
        permissionChecker.walkingGranted = true
        repository.preferences.value = CapabilityPreferences(canWalk = false)
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.setCanWalk(true)
        advanceUntilIdle()

        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.walkingPermission)
        assertFalse(viewModel.uiState.value.showPermissionExplanation)
    }

    @Test
    fun `no explanation on a phone that needs no permission`() = runTest {
        permissionChecker.walkingPermissionNeeded = false
        repository.preferences.value = CapabilityPreferences(canWalk = false)
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.setCanWalk(true)
        advanceUntilIdle()

        assertEquals(PermissionStatus.NOT_NEEDED, viewModel.uiState.value.walkingPermission)
        assertFalse(viewModel.uiState.value.showPermissionExplanation)
    }

    @Test
    fun `no explanation while the screen is still loading`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)

        // advanceUntilIdle() was not called: the change is ignored
        viewModel.setCanWalk(true)

        assertFalse(viewModel.uiState.value.showPermissionExplanation)
    }

    @Test
    fun `the explanation can be dismissed`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.requestWalkingPermission()
        assertTrue(viewModel.uiState.value.showPermissionExplanation)

        viewModel.dismissPermissionExplanation()
        assertFalse(viewModel.uiState.value.showPermissionExplanation)
    }

    @Test
    fun `granted answer enables walking and keeps the other challenges`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = true, shouldShowRationale = false)

        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.walkingPermission)
        assertTrue(viewModel.uiState.value.preferences.canDoMentalArithmetic)
    }

    @Test
    fun `denied answer does not crash and math stays available`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, shouldShowRationale = true)

        assertEquals(PermissionStatus.DENIED, viewModel.uiState.value.walkingPermission)
        assertTrue(viewModel.uiState.value.preferences.canDoMentalArithmetic)
        assertFalse(viewModel.uiState.value.hasError)
    }

    @Test
    fun `do not ask again leads to the app settings`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, shouldShowRationale = false)
        assertEquals(
            PermissionStatus.DENIED_PERMANENTLY,
            viewModel.uiState.value.walkingPermission
        )

        // The system question would not appear: no explanation is shown
        viewModel.requestWalkingPermission()
        assertFalse(viewModel.uiState.value.showPermissionExplanation)
    }

    @Test
    fun `permission granted in the Android settings is detected on return`() = runTest {
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()
        viewModel.onPermissionResult(granted = false, shouldShowRationale = false)

        permissionChecker.walkingGranted = true
        viewModel.refreshPermissions()

        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.walkingPermission)
    }

    @Test
    fun `permission revoked in the Android settings is detected on return`() = runTest {
        permissionChecker.walkingGranted = true
        val viewModel = SettingsViewModel(repository, permissionChecker)
        advanceUntilIdle()
        assertEquals(PermissionStatus.GRANTED, viewModel.uiState.value.walkingPermission)

        permissionChecker.walkingGranted = false
        viewModel.refreshPermissions()

        assertEquals(PermissionStatus.NOT_GRANTED, viewModel.uiState.value.walkingPermission)
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
