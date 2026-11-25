package com.example.stopscrolling.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppGroupDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AppGroupDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()

        dao = database.appGroupDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helper function to create a test group
    private suspend fun createGroup(
        name: String = "Social Media",
        timeLimit: Int = 60,
        limitType: LimitType = LimitType.SOFT
    ): Int {
        return dao.insertGroup(
            AppGroup(
                name = name,
                timeLimit = timeLimit,
                limitType = limitType
            )
        ).toInt()
    }

    // Helper function to add apps to a group
    private suspend fun addApps(
        groupId: Int,
        vararg packageNames: String
    ) {
        dao.insertGroupApps(
            packageNames.map { packageName ->
                GroupApp(
                    groupId = groupId,
                    packageName = packageName
                )
            }
        )
    }

    @Test
    fun updateGroup_updatesGroupDetails() = runBlocking {
        val groupId = createGroup()

        val updatedRows = dao.updateGroup(
            groupId = groupId,
            name = "Entertainment",
            timeLimit = 90,
            limitType = LimitType.HARD
        )

        val group = dao.getGroupById(groupId)

        assertEquals(1, updatedRows)
        assertNotNull(group)
        assertEquals("Entertainment", group?.name)
        assertEquals(90, group?.timeLimit)
        assertEquals(LimitType.HARD, group?.limitType)
    }

    @Test
    fun updateGroup_nonexistentGroup_returnsZero() = runBlocking {
        val updatedRows = dao.updateGroup(
            groupId = 999,
            name = "Nonexistent Group",
            timeLimit = 30,
            limitType = LimitType.SOFT
        )

        assertEquals(0, updatedRows)
    }

    @Test
    fun updateGroup_preservesWeeklyAndPendingLimits() = runBlocking {
        val groupId = dao.insertGroup(
            AppGroup(
                name = "Social Media",
                timeLimit = 60,
                limitType = LimitType.HARD,
                weeklyLimit = 300,
                pendingTimeLimit = 90,
                pendingLimitType = LimitType.SOFT,
                pendingWeeklyLimit = 400,
                pendingFrom = 1700000000000L
            )
        ).toInt()

        dao.updateGroup(
            groupId = groupId,
            name = "Updated Group",
            timeLimit = 45,
            limitType = LimitType.HARD
        )

        val group = requireNotNull(
            dao.getGroupById(groupId)
        )

        assertEquals("Updated Group", group.name)
        assertEquals(45, group.timeLimit)
        assertEquals(300, group.weeklyLimit)
        assertEquals(90, group.pendingTimeLimit)
        assertEquals(
            LimitType.SOFT,
            group.pendingLimitType
        )
        assertEquals(400, group.pendingWeeklyLimit)
        assertEquals(
            1700000000000L,
            group.pendingFrom
        )
    }

    @Test
    fun updateGroupRow_updatesPendingLimitsAndPreservesApps() =
        runBlocking {

            val groupId = createGroup(
                limitType = LimitType.HARD
            )

            addApps(
                groupId,
                "com.instagram.android"
            )

            val originalGroup = requireNotNull(
                dao.getGroup(groupId)
            )

            val updatedGroup = originalGroup.copy(
                pendingTimeLimit = 90,
                pendingLimitType = LimitType.SOFT,
                pendingFrom = 1700000000000L
            )

            val updatedRows = dao.updateGroupRow(
                updatedGroup
            )

            val savedGroup = dao.getGroup(groupId)

            assertEquals(1, updatedRows)
            assertEquals(updatedGroup, savedGroup)

            val apps = dao.getAppsForGroup(groupId)

            assertEquals(1, apps.size)
            assertEquals(
                "com.instagram.android",
                apps.first().packageName
            )
        }

    @Test
    fun getGroupById_returnsCorrectGroup() = runBlocking {
        val firstGroupId = createGroup("Social Media")
        val secondGroupId = createGroup("Games")

        val firstGroup = dao.getGroupById(firstGroupId)
        val secondGroup = dao.getGroupById(secondGroupId)

        assertEquals("Social Media", firstGroup?.name)
        assertEquals("Games", secondGroup?.name)
    }

    @Test
    fun getGroupById_nonexistentGroup_returnsNull() = runBlocking {
        assertNull(dao.getGroupById(999))
    }

    @Test
    fun getAppsForGroup_returnsOnlyAppsFromSelectedGroup() =
        runBlocking {

            val firstGroupId = createGroup("Social Media")
            val secondGroupId = createGroup("Games")

            addApps(
                firstGroupId,
                "com.instagram.android",
                "com.zhiliaoapp.musically"
            )

            addApps(
                secondGroupId,
                "com.example.game"
            )

            val apps = dao.getAppsForGroup(firstGroupId)

            assertEquals(2, apps.size)

            assertEquals(
                setOf(
                    "com.instagram.android",
                    "com.zhiliaoapp.musically"
                ),
                apps.map { it.packageName }.toSet()
            )
        }

    @Test
    fun deleteGroupApp_removesOnlySelectedApp() = runBlocking {
        val groupId = createGroup()

        addApps(
            groupId,
            "com.instagram.android",
            "com.zhiliaoapp.musically"
        )

        val deletedRows = dao.deleteGroupApp(
            groupId,
            "com.instagram.android"
        )

        val remainingApps = dao.getAppsForGroup(groupId)

        assertEquals(1, deletedRows)
        assertEquals(1, remainingApps.size)

        assertEquals(
            "com.zhiliaoapp.musically",
            remainingApps.first().packageName
        )

        assertNotNull(dao.getGroupById(groupId))
    }

    @Test
    fun deleteGroupApp_doesNotAffectOtherGroups() = runBlocking {
        val firstGroupId = createGroup("Group A")
        val secondGroupId = createGroup("Group B")

        val packageName = "com.instagram.android"

        addApps(firstGroupId, packageName)
        addApps(secondGroupId, packageName)

        dao.deleteGroupApp(firstGroupId, packageName)

        assertTrue(
            dao.getAppsForGroup(firstGroupId).isEmpty()
        )

        val secondGroupApps =
            dao.getAppsForGroup(secondGroupId)

        assertEquals(1, secondGroupApps.size)
        assertEquals(
            packageName,
            secondGroupApps.first().packageName
        )
    }

    @Test
    fun deleteGroupApps_removesAllAppsInSelectedGroup() =
        runBlocking {

            val firstGroupId = createGroup("Group A")
            val secondGroupId = createGroup("Group B")

            addApps(
                firstGroupId,
                "com.instagram.android",
                "com.zhiliaoapp.musically"
            )

            addApps(
                secondGroupId,
                "com.example.game"
            )

            val deletedRows =
                dao.deleteGroupApps(firstGroupId)

            assertEquals(2, deletedRows)

            assertTrue(
                dao.getAppsForGroup(firstGroupId).isEmpty()
            )

            assertEquals(
                1,
                dao.getAppsForGroup(secondGroupId).size
            )

            assertNotNull(
                dao.getGroupById(firstGroupId)
            )
        }

    @Test
    fun deleteGroup_removesGroup() = runBlocking {
        val firstGroupId = createGroup("Group A")
        val secondGroupId = createGroup("Group B")

        val deletedRows = dao.deleteGroup(firstGroupId)

        assertEquals(1, deletedRows)
        assertNull(dao.getGroupById(firstGroupId))
        assertNotNull(dao.getGroupById(secondGroupId))
    }

    @Test
    fun deleteGroup_nonexistentGroup_returnsZero() =
        runBlocking {

            val deletedRows = dao.deleteGroup(999)

            assertEquals(0, deletedRows)
        }

    @Test
    fun observeGroupsWithApps_reflectsEditAndDelete() =
        runBlocking {

            val groupId = createGroup()

            addApps(
                groupId,
                "com.instagram.android"
            )

            val initialGroups =
                dao.observeGroupsWithApps().first()

            assertEquals(1, initialGroups.size)

            dao.updateGroup(
                groupId = groupId,
                name = "Updated Group",
                timeLimit = 120,
                limitType = LimitType.SOFT
            )

            val updatedGroups =
                dao.observeGroupsWithApps().first()

            assertEquals(1, updatedGroups.size)
            assertEquals(
                "Updated Group",
                updatedGroups.first().group.name
            )
            assertEquals(
                120,
                updatedGroups.first().group.timeLimit
            )

            dao.deleteGroupApps(groupId)
            dao.deleteGroup(groupId)

            val remainingGroups =
                dao.observeGroupsWithApps().first()

            assertTrue(remainingGroups.isEmpty())
        }
}