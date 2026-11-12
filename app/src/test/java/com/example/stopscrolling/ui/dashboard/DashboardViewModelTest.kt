
package com.example.stopscrolling.ui.dashboard

import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.repository.AppGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepository: FakeAppGroupRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAppGroupRepository()
        viewModel = DashboardViewModel(fakeRepository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun groups_isInitiallyEmpty() = runTest(testDispatcher) {
        assertTrue(viewModel.groups.value.isEmpty())
    }

    @Test
    fun groups_receivesDataFromRepository() = runTest(testDispatcher) {
        backgroundScope.launch {
            viewModel.groups.collect {}
        }
        runCurrent()

        fakeRepository.setGroups(
            listOf(
                GroupWithApps(
                    group = AppGroup(id = 1, name = "Social Media"),
                    apps = emptyList()
                )
            )
        )

        advanceUntilIdle()

        assertEquals(1, viewModel.groups.value.size)
        assertEquals(
            "Social Media",
            viewModel.groups.value[0].group.name
        )
    }

    @Test
    fun groups_updatesWhenRepositoryChanges() = runTest(testDispatcher) {
        backgroundScope.launch {
            viewModel.groups.collect {}
        }
        runCurrent()

        fakeRepository.setGroups(
            listOf(
                GroupWithApps(
                    group = AppGroup(id = 1, name = "Social Media"),
                    apps = emptyList()
                )
            )
        )
        advanceUntilIdle()

        fakeRepository.setGroups(
            listOf(
                GroupWithApps(
                    group = AppGroup(id = 1, name = "Social Media"),
                    apps = emptyList()
                ),
                GroupWithApps(
                    group = AppGroup(id = 2, name = "Games"),
                    apps = emptyList()
                )
            )
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.groups.value.size)
    }

    private class FakeAppGroupRepository : AppGroupRepository {

        private val groupsFlow =
            MutableStateFlow<List<GroupWithApps>>(emptyList())

        override fun observeGroups() = groupsFlow

        override suspend fun createGroup(
            name: String,
            packageNames: List<String>
        ): Int = 1

        fun setGroups(groups: List<GroupWithApps>) {
            groupsFlow.value = groups
        }
    }
}
