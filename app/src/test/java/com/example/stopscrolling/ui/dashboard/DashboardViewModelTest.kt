package com.example.stopscrolling.ui.dashboard

import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.PendingChangeType
import com.example.stopscrolling.data.PendingGroupChange
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
import org.junit.Assert.assertFalse
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

    // ---------- Sprint 1: Existing tests ----------

    @Test
    fun groups_isInitiallyEmpty() = runTest(testDispatcher) {
        assertTrue(viewModel.groups.value.isEmpty())
    }

    @Test
    fun groups_receivesDataFromRepository() =
        runTest(testDispatcher) {

            backgroundScope.launch {
                viewModel.groups.collect {}
            }

            runCurrent()

            fakeRepository.setGroups(
                listOf(
                    GroupWithApps(
                        group = AppGroup(
                            id = 1,
                            name = "Social Media"
                        ),
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
    fun groups_updatesWhenRepositoryChanges() =
        runTest(testDispatcher) {

            backgroundScope.launch {
                viewModel.groups.collect {}
            }

            runCurrent()

            fakeRepository.setGroups(
                listOf(
                    GroupWithApps(
                        group = AppGroup(
                            id = 1,
                            name = "Social Media"
                        ),
                        apps = emptyList()
                    )
                )
            )

            advanceUntilIdle()

            fakeRepository.setGroups(
                listOf(
                    GroupWithApps(
                        group = AppGroup(
                            id = 1,
                            name = "Social Media"
                        ),
                        apps = emptyList()
                    ),
                    GroupWithApps(
                        group = AppGroup(
                            id = 2,
                            name = "Games"
                        ),
                        apps = emptyList()
                    )
                )
            )

            advanceUntilIdle()

            assertEquals(2, viewModel.groups.value.size)
        }

    // ---------- Sprint 2: Edit Limit tests ----------

    @Test
    fun updateLimit_success_updatesRepository() =
        runTest(testDispatcher) {

            fakeRepository.updateResult = true

            var successCalled = false

            viewModel.updateLimit(
                groupId = 1,
                timeLimit = 60,
                limitType = LimitType.SOFT,
                onSuccess = {
                    successCalled = true
                }
            )

            advanceUntilIdle()

            assertEquals(1, fakeRepository.updateCount)
            assertEquals(1, fakeRepository.updatedGroupId)
            assertEquals(60, fakeRepository.updatedTimeLimit)
            assertEquals(
                LimitType.SOFT,
                fakeRepository.updatedLimitType
            )

            assertTrue(successCalled)
            assertFalse(viewModel.isUpdatingLimit.value)
            assertFalse(viewModel.limitUpdateError.value)
        }

    @Test
    fun updateLimit_failure_showsError() =
        runTest(testDispatcher) {

            fakeRepository.updateResult = false

            var successCalled = false

            viewModel.updateLimit(
                groupId = 1,
                timeLimit = 30,
                limitType = LimitType.HARD,
                onSuccess = {
                    successCalled = true
                }
            )

            advanceUntilIdle()

            assertEquals(1, fakeRepository.updateCount)
            assertEquals(1, fakeRepository.updatedGroupId)
            assertEquals(30, fakeRepository.updatedTimeLimit)
            assertEquals(
                LimitType.HARD,
                fakeRepository.updatedLimitType
            )

            assertFalse(successCalled)
            assertFalse(viewModel.isUpdatingLimit.value)
            assertTrue(viewModel.limitUpdateError.value)
        }

    // ---------- US-09: Edit Group tests ----------

    @Test
    fun updateGroup_success_updatesRepository() =
        runTest(testDispatcher) {

            fakeRepository.updateGroupResult = true

            var successCalled = false

            val apps = listOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically"
            )

            viewModel.updateGroup(
                groupId = 1,
                name = "Entertainment",
                packageNames = apps,
                timeLimit = 90,
                limitType = LimitType.SOFT,
                onSuccess = {
                    successCalled = true
                }
            )

            assertTrue(viewModel.isUpdatingGroup.value)

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.updateGroupCount
            )
            assertEquals(
                1,
                fakeRepository.editedGroupId
            )
            assertEquals(
                "Entertainment",
                fakeRepository.editedGroupName
            )
            assertEquals(
                apps,
                fakeRepository.editedPackageNames
            )
            assertEquals(
                90,
                fakeRepository.editedTimeLimit
            )
            assertEquals(
                LimitType.SOFT,
                fakeRepository.editedLimitType
            )

            assertTrue(successCalled)
            assertFalse(viewModel.isUpdatingGroup.value)
            assertFalse(viewModel.updateGroupError.value)
        }

    @Test
    fun updateGroup_failure_showsError() =
        runTest(testDispatcher) {

            fakeRepository.updateGroupResult = false

            var successCalled = false

            viewModel.updateGroup(
                groupId = 1,
                name = "Updated Group",
                packageNames = listOf("com.example.app"),
                timeLimit = 60,
                limitType = LimitType.HARD,
                onSuccess = {
                    successCalled = true
                }
            )

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.updateGroupCount
            )
            assertFalse(successCalled)
            assertFalse(viewModel.isUpdatingGroup.value)
            assertTrue(viewModel.updateGroupError.value)
        }

    @Test
    fun updateGroup_exception_showsError() =
        runTest(testDispatcher) {

            fakeRepository.throwOnUpdateGroup = true

            var successCalled = false

            viewModel.updateGroup(
                groupId = 1,
                name = "Updated Group",
                packageNames = listOf("com.example.app"),
                timeLimit = 60,
                limitType = LimitType.HARD,
                onSuccess = {
                    successCalled = true
                }
            )

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.updateGroupCount
            )
            assertFalse(successCalled)
            assertFalse(viewModel.isUpdatingGroup.value)
            assertTrue(viewModel.updateGroupError.value)
        }

    @Test
    fun updateGroup_preventsDuplicateRequests() =
        runTest(testDispatcher) {

            fakeRepository.updateGroupResult = true

            var successCount = 0

            repeat(2) {
                viewModel.updateGroup(
                    groupId = 1,
                    name = "Updated Group",
                    packageNames = listOf("com.example.app"),
                    timeLimit = 60,
                    limitType = LimitType.SOFT,
                    onSuccess = {
                        successCount++
                    }
                )
            }

            assertTrue(viewModel.isUpdatingGroup.value)

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.updateGroupCount
            )
            assertEquals(1, successCount)
            assertFalse(viewModel.isUpdatingGroup.value)
            assertFalse(viewModel.updateGroupError.value)
        }

    @Test
    fun clearUpdateGroupError_resetsError() =
        runTest(testDispatcher) {

            fakeRepository.updateGroupResult = false

            viewModel.updateGroup(
                groupId = 1,
                name = "Updated Group",
                packageNames = listOf("com.example.app"),
                timeLimit = 60,
                limitType = LimitType.HARD,
                onSuccess = {}
            )

            advanceUntilIdle()

            assertTrue(viewModel.updateGroupError.value)

            viewModel.clearUpdateGroupError()

            assertFalse(viewModel.updateGroupError.value)
        }

    // ---------- US-09: Delete Group tests ----------

    @Test
    fun deleteGroup_success_callsRepository() =
        runTest(testDispatcher) {

            fakeRepository.deleteGroupResult = true

            var successCalled = false

            viewModel.deleteGroup(
                groupId = 1,
                onSuccess = {
                    successCalled = true
                }
            )

            assertTrue(viewModel.isDeletingGroup.value)

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.deleteGroupCount
            )
            assertEquals(
                1,
                fakeRepository.deletedGroupId
            )

            assertTrue(successCalled)
            assertFalse(viewModel.isDeletingGroup.value)
            assertFalse(viewModel.deleteGroupError.value)
        }

    @Test
    fun deleteGroup_failure_showsError() =
        runTest(testDispatcher) {

            fakeRepository.deleteGroupResult = false

            var successCalled = false

            viewModel.deleteGroup(
                groupId = 1,
                onSuccess = {
                    successCalled = true
                }
            )

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.deleteGroupCount
            )
            assertFalse(successCalled)
            assertFalse(viewModel.isDeletingGroup.value)
            assertTrue(viewModel.deleteGroupError.value)
        }

    @Test
    fun deleteGroup_exception_showsError() =
        runTest(testDispatcher) {

            fakeRepository.throwOnDeleteGroup = true

            var successCalled = false

            viewModel.deleteGroup(
                groupId = 1,
                onSuccess = {
                    successCalled = true
                }
            )

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.deleteGroupCount
            )
            assertFalse(successCalled)
            assertFalse(viewModel.isDeletingGroup.value)
            assertTrue(viewModel.deleteGroupError.value)
        }

    @Test
    fun deleteGroup_preventsDuplicateRequests() =
        runTest(testDispatcher) {

            fakeRepository.deleteGroupResult = true

            var successCount = 0

            repeat(2) {
                viewModel.deleteGroup(
                    groupId = 1,
                    onSuccess = {
                        successCount++
                    }
                )
            }

            assertTrue(viewModel.isDeletingGroup.value)

            advanceUntilIdle()

            assertEquals(
                1,
                fakeRepository.deleteGroupCount
            )
            assertEquals(1, successCount)
            assertFalse(viewModel.isDeletingGroup.value)
            assertFalse(viewModel.deleteGroupError.value)
        }

    @Test
    fun clearDeleteGroupError_resetsError() =
        runTest(testDispatcher) {

            fakeRepository.deleteGroupResult = false

            viewModel.deleteGroup(
                groupId = 1,
                onSuccess = {}
            )

            advanceUntilIdle()

            assertTrue(viewModel.deleteGroupError.value)

            viewModel.clearDeleteGroupError()

            assertFalse(viewModel.deleteGroupError.value)
        }

    // ---------- US-09: Pending Changes test ----------

    @Test
    fun pendingChanges_updatesState() =
        runTest(testDispatcher) {

            backgroundScope.launch {
                viewModel.pendingChanges.collect {}
            }

            runCurrent()

            assertTrue(
                viewModel.pendingChanges.value.isEmpty()
            )

            val pendingDeletion = PendingGroupChange(
                id = 1,
                groupId = 1,
                changeType = PendingChangeType.DELETE_GROUP.name,
                packageName = null,
                executeAtMillis = 1700000000000L
            )

            val pendingAppRemoval = PendingGroupChange(
                id = 2,
                groupId = 2,
                changeType = PendingChangeType.REMOVE_APP.name,
                packageName = "com.instagram.android",
                executeAtMillis = 1700000000000L
            )

            fakeRepository.setPendingChanges(
                listOf(
                    pendingDeletion,
                    pendingAppRemoval
                )
            )

            runCurrent()

            assertEquals(
                2,
                viewModel.pendingChanges.value.size
            )

            assertEquals(
                listOf(
                    pendingDeletion,
                    pendingAppRemoval
                ),
                viewModel.pendingChanges.value
            )

            // Simulate completed pending changes.
            fakeRepository.setPendingChanges(emptyList())

            runCurrent()

            assertTrue(
                viewModel.pendingChanges.value.isEmpty()
            )
        }

    // ---------- Fake Repository ----------

    private class FakeAppGroupRepository :
        AppGroupRepository {

        private val groupsFlow =
            MutableStateFlow<List<GroupWithApps>>(
                emptyList()
            )

        private val pendingChangesFlow =
            MutableStateFlow<List<PendingGroupChange>>(
                emptyList()
            )

        // Existing Edit Limit properties
        var updateResult = false
        var updateCount = 0
        var updatedGroupId = -1
        var updatedTimeLimit = -1
        var updatedLimitType: LimitType? = null

        // US-09: Edit Group properties
        var updateGroupResult = false
        var updateGroupCount = 0
        var throwOnUpdateGroup = false

        var editedGroupId = -1
        var editedGroupName = ""
        var editedPackageNames: List<String> = emptyList()
        var editedTimeLimit = -1
        var editedLimitType: LimitType? = null

        // US-09: Delete Group properties
        var deleteGroupResult = false
        var deleteGroupCount = 0
        var throwOnDeleteGroup = false

        var deletedGroupId = -1

        override fun observeGroups() = groupsFlow

        override fun observePendingChanges() =
            pendingChangesFlow

        override suspend fun createGroup(
            name: String,
            packageNames: List<String>,
            timeLimit: Int,
            limitType: LimitType
        ): Int = 1

        override suspend fun updateLimit(
            groupId: Int,
            timeLimit: Int,
            limitType: LimitType
        ): Boolean {

            updateCount++
            updatedGroupId = groupId
            updatedTimeLimit = timeLimit
            updatedLimitType = limitType

            return updateResult
        }

        override suspend fun updateGroup(
            groupId: Int,
            name: String,
            packageNames: List<String>,
            timeLimit: Int,
            limitType: LimitType
        ): Boolean {

            updateGroupCount++

            editedGroupId = groupId
            editedGroupName = name
            editedPackageNames = packageNames
            editedTimeLimit = timeLimit
            editedLimitType = limitType

            if (throwOnUpdateGroup) {
                throw IllegalStateException(
                    "Failed to update group"
                )
            }

            return updateGroupResult
        }

        override suspend fun deleteGroup(
            groupId: Int
        ): Boolean {

            deleteGroupCount++
            deletedGroupId = groupId

            if (throwOnDeleteGroup) {
                throw IllegalStateException(
                    "Failed to delete group"
                )
            }

            return deleteGroupResult
        }

        override suspend fun processDueChanges(
            nowMillis: Long
        ): Int = 0

        override suspend fun applyDuePendingChanges():
                Int = 0

        fun setGroups(groups: List<GroupWithApps>) {
            groupsFlow.value = groups
        }

        fun setPendingChanges(
            changes: List<PendingGroupChange>
        ) {
            pendingChangesFlow.value = changes
        }
    }
}