package com.example.stopscrolling.ui.newgroup

import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.InstalledAppsRepository
import com.example.stopscrolling.util.WeeklyLimitError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class NewGroupViewModelTest {

    private lateinit var groupRepository: FakeGroupRepository
    private lateinit var installedAppsRepository: InstalledAppsRepository
    private lateinit var viewModel: NewGroupViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        groupRepository = FakeGroupRepository()

        installedAppsRepository =
            Mockito.mock(InstalledAppsRepository::class.java)

        Mockito.`when`(
            installedAppsRepository.getInstalledApps()
        ).thenReturn(emptyList())

        viewModel = NewGroupViewModel(
            groupRepository,
            installedAppsRepository
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasEmptyNameAndSelection() =
        runTest(testDispatcher) {
            assertEquals("", viewModel.uiState.value.name)
            assertTrue(
                viewModel.uiState.value.selectedPackages.isEmpty()
            )
            assertFalse(viewModel.uiState.value.saved)
            assertEquals(0, viewModel.uiState.value.timeLimit)
            assertEquals(
                LimitType.HARD,
                viewModel.uiState.value.limitType
            )
        }

    @Test
    fun onNameChange_updatesName() =
        runTest(testDispatcher) {
            viewModel.onNameChange("Social Media")

            assertEquals(
                "Social Media",
                viewModel.uiState.value.name
            )
        }

    @Test
    fun onAppToggle_selectsAndDeselectsApp() =
        runTest(testDispatcher) {
            viewModel.onAppToggle("com.instagram.android")

            assertTrue(
                "com.instagram.android" in
                        viewModel.uiState.value.selectedPackages
            )

            viewModel.onAppToggle("com.instagram.android")

            assertFalse(
                "com.instagram.android" in
                        viewModel.uiState.value.selectedPackages
            )
        }

    @Test
    fun onSaveClick_withEmptyName_showsError() =
        runTest(testDispatcher) {
            viewModel.onAppToggle("com.instagram.android")
            viewModel.onSaveClick()

            assertTrue(viewModel.uiState.value.nameError)
            assertFalse(viewModel.uiState.value.saved)
            assertEquals(0, groupRepository.saveCount)
        }

    @Test
    fun onSaveClick_withNoApps_showsError() =
        runTest(testDispatcher) {
            viewModel.onNameChange("Social Media")
            viewModel.onSaveClick()

            assertTrue(viewModel.uiState.value.appsError)
            assertFalse(viewModel.uiState.value.saved)
            assertEquals(0, groupRepository.saveCount)
        }

    @Test
    fun onSaveClick_withValidData_savesGroup() =
        runTest(testDispatcher) {
            viewModel.onNameChange("Social Media")
            viewModel.onAppToggle("com.instagram.android")

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(1, groupRepository.saveCount)
            assertEquals(
                "Social Media",
                groupRepository.savedName
            )
            assertEquals(
                listOf("com.instagram.android"),
                groupRepository.savedPackages
            )
            assertTrue(viewModel.uiState.value.saved)
        }

    // US-12/17: Verify selected limit settings are saved
    @Test
    fun onSaveClick_withLimitSettings_passesValuesToRepository() =
        runTest(testDispatcher) {
            viewModel.onNameChange("Social Media")
            viewModel.onAppToggle("com.instagram.android")
            viewModel.onTimeLimitChange(60)
            viewModel.onLimitTypeChange(LimitType.SOFT)

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(1, groupRepository.saveCount)
            assertEquals(60, groupRepository.savedTimeLimit)
            assertEquals(
                LimitType.SOFT,
                groupRepository.savedLimitType
            )
            assertTrue(viewModel.uiState.value.saved)
        }

    // US-12/17: Verify "No limit" is saved as 0
    @Test
    fun onSaveClick_withNoLimit_passesDefaultValues() =
        runTest(testDispatcher) {
            viewModel.onNameChange("Games")
            viewModel.onAppToggle("com.example.game")

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(1, groupRepository.saveCount)
            assertEquals(0, groupRepository.savedTimeLimit)
            assertEquals(
                LimitType.HARD,
                groupRepository.savedLimitType
            )
            assertTrue(viewModel.uiState.value.saved)
        }

    // ---------- US-13: weekly limit ----------

    private fun fillValidGroup() {
        viewModel.onNameChange("Social Media")
        viewModel.onAppToggle("com.instagram.android")
    }

    @Test
    fun weeklyLimit_isOffByDefault_andSavedAsZero() =
        runTest(testDispatcher) {
            fillValidGroup()

            viewModel.onSaveClick()
            runCurrent()

            assertFalse(viewModel.uiState.value.weeklyEnabled)
            assertEquals(0, groupRepository.savedWeeklyLimit)
            assertTrue(viewModel.uiState.value.saved)
        }

    @Test
    fun weeklyLimit_hoursAndMinutes_arePassedToRepositoryInMinutes() =
        runTest(testDispatcher) {
            fillValidGroup()
            viewModel.onWeeklyEnabledChange(true)
            viewModel.onWeeklyHoursChange("10")
            viewModel.onWeeklyMinutesChange("30")

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(630, groupRepository.savedWeeklyLimit)
            assertTrue(viewModel.uiState.value.saved)
        }

    @Test
    fun weeklyLimit_switchOffAfterTyping_savesNoWeeklyLimit() =
        runTest(testDispatcher) {
            fillValidGroup()
            viewModel.onWeeklyEnabledChange(true)
            viewModel.onWeeklyHoursChange("10")
            viewModel.onWeeklyEnabledChange(false)

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(0, groupRepository.savedWeeklyLimit)
        }

    @Test
    fun weeklyLimit_enabledButEmpty_showsErrorAndDoesNotSave() =
        runTest(testDispatcher) {
            fillValidGroup()
            viewModel.onWeeklyEnabledChange(true)

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(WeeklyLimitError.EMPTY, viewModel.uiState.value.weeklyLimitError)
            assertEquals(0, groupRepository.saveCount)
            assertFalse(viewModel.uiState.value.saved)
        }

    @Test
    fun weeklyLimit_zero_showsNotPositiveError() =
        runTest(testDispatcher) {
            fillValidGroup()
            viewModel.onWeeklyEnabledChange(true)
            viewModel.onWeeklyHoursChange("0")

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(
                WeeklyLimitError.NOT_POSITIVE,
                viewModel.uiState.value.weeklyLimitError
            )
            assertEquals(0, groupRepository.saveCount)
        }

    @Test
    fun weeklyLimit_moreThan168Hours_showsTooLongError() =
        runTest(testDispatcher) {
            fillValidGroup()
            viewModel.onWeeklyEnabledChange(true)
            viewModel.onWeeklyHoursChange("169")

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(WeeklyLimitError.TOO_LONG, viewModel.uiState.value.weeklyLimitError)
            assertEquals(0, groupRepository.saveCount)
        }

    @Test
    fun weeklyLimit_lowerThanDailyLimit_showsBelowDailyError() =
        runTest(testDispatcher) {
            fillValidGroup()
            viewModel.onTimeLimitChange(120) // 2 hours per day
            viewModel.onWeeklyEnabledChange(true)
            viewModel.onWeeklyHoursChange("1") // 1 hour per week

            viewModel.onSaveClick()
            runCurrent()

            assertEquals(
                WeeklyLimitError.BELOW_DAILY,
                viewModel.uiState.value.weeklyLimitError
            )
            assertEquals(0, groupRepository.saveCount)
        }

    @Test
    fun weeklyLimit_typingAgain_removesTheError() =
        runTest(testDispatcher) {
            fillValidGroup()
            viewModel.onWeeklyEnabledChange(true)
            viewModel.onSaveClick()
            assertEquals(WeeklyLimitError.EMPTY, viewModel.uiState.value.weeklyLimitError)

            viewModel.onWeeklyHoursChange("5")

            assertNull(viewModel.uiState.value.weeklyLimitError)
        }

    private class FakeGroupRepository : AppGroupRepository {

        private val groupsFlow =
            MutableStateFlow<List<GroupWithApps>>(emptyList())

        var saveCount = 0
        var savedName = ""
        var savedPackages = emptyList<String>()
        var savedTimeLimit = -1
        var savedLimitType: LimitType? = null
        var savedWeeklyLimit = -1

        override fun observeGroups() = groupsFlow

        override suspend fun createGroup(
            name: String,
            packageNames: List<String>,
            timeLimit: Int,
            limitType: LimitType,
            weeklyLimit: Int
        ): Int {
            saveCount++
            savedWeeklyLimit = weeklyLimit
            savedName = name
            savedPackages = packageNames
            savedTimeLimit = timeLimit
            savedLimitType = limitType
            return 1
        }

        override suspend fun updateLimit(
            groupId: Int,
            timeLimit: Int,
            limitType: LimitType
        ): Boolean {
            return false
        }

        override suspend fun updateLimits(
            groupId: Int,
            timeLimit: Int,
            limitType: LimitType,
            weeklyLimit: Int
        ): Boolean {
            return false
        }

        override suspend fun applyDuePendingChanges(): Int = 0
    }
}
