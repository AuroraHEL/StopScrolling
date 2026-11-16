package com.example.stopscrolling.ui.newgroup

import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.InstalledAppsRepository
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

    private class FakeGroupRepository : AppGroupRepository {

        private val groupsFlow =
            MutableStateFlow<List<GroupWithApps>>(emptyList())

        var saveCount = 0
        var savedName = ""
        var savedPackages = emptyList<String>()

        override fun observeGroups() = groupsFlow

        override suspend fun createGroup(
            name: String,
            packageNames: List<String>
        ): Int {
            saveCount++
            savedName = name
            savedPackages = packageNames
            return 1
        }
    }
}
