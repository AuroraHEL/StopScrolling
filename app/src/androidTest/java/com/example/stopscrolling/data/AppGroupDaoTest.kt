package com.example.stopscrolling.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppGroupDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AppGroupDao

    @Before
    fun setup() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()

        dao = database.appGroupDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // ---------- Existing Tests ----------

    @Test
    fun insertGroup_returnsGeneratedId() = runBlocking {
        val id = dao.insertGroup(
            AppGroup(name = "Social Media")
        )

        assertTrue(id > 0)
    }

    @Test
    fun insertGroup_savesCorrectName() = runBlocking {
        dao.insertGroup(
            AppGroup(name = "Social Media")
        )

        val groups = dao.observeGroupsWithApps().first()

        assertEquals(1, groups.size)
        assertEquals("Social Media", groups[0].group.name)
    }

    @Test
    fun insertGroupApps_savesMultipleApps() = runBlocking {
        val groupId = dao.insertGroup(
            AppGroup(name = "Social Media")
        ).toInt()

        dao.insertGroupApps(
            listOf(
                GroupApp(
                    groupId = groupId,
                    packageName = "com.instagram.android"
                ),
                GroupApp(
                    groupId = groupId,
                    packageName = "com.zhiliaoapp.musically"
                )
            )
        )

        val groups = dao.observeGroupsWithApps().first()
        val apps = groups[0].apps.map { it.packageName }

        assertEquals(2, apps.size)
        assertTrue(apps.contains("com.instagram.android"))
        assertTrue(apps.contains("com.zhiliaoapp.musically"))
    }

    @Test
    fun observeGroupsWithApps_sortsByName() = runBlocking {
        dao.insertGroup(AppGroup(name = "zebra"))
        dao.insertGroup(AppGroup(name = "Apple"))
        dao.insertGroup(AppGroup(name = "banana"))

        val groups = dao.observeGroupsWithApps().first()
        val names = groups.map { it.group.name }

        assertEquals(
            listOf("Apple", "banana", "zebra"),
            names
        )
    }

    @Test
    fun observeGroupsWithApps_returnsEmptyList() = runBlocking {
        val groups = dao.observeGroupsWithApps().first()

        assertTrue(groups.isEmpty())
    }

    // ---------- US-09 Task 2 Tests ----------

    @Test
    fun updateGroup_updatesNameAndLimit() = runBlocking {
        val groupId = dao.insertGroup(
            AppGroup(
                name = "Social Media",
                timeLimit = 30,
                limitType = LimitType.HARD
            )
        ).toInt()

        val updatedRows = dao.updateGroup(
            groupId = groupId,
            name = "Study Apps",
            timeLimit = 60,
            limitType = LimitType.SOFT
        )

        val groups = dao.observeGroupsWithApps().first()
        val updatedGroup = groups.first().group

        assertEquals(1, updatedRows)
        assertEquals("Study Apps", updatedGroup.name)
        assertEquals(60, updatedGroup.timeLimit)
        assertEquals(LimitType.SOFT, updatedGroup.limitType)
    }

    @Test
    fun deleteGroupApps_removesAppAssociations() = runBlocking {
        val groupId = dao.insertGroup(
            AppGroup(name = "Social Media")
        ).toInt()

        dao.insertGroupApps(
            listOf(
                GroupApp(
                    groupId = groupId,
                    packageName = "com.example.app1"
                ),
                GroupApp(
                    groupId = groupId,
                    packageName = "com.example.app2"
                )
            )
        )

        val deletedRows = dao.deleteGroupApps(groupId)
        val groups = dao.observeGroupsWithApps().first()

        assertEquals(2, deletedRows)
        assertEquals(1, groups.size)
        assertTrue(groups.first().apps.isEmpty())
    }

    @Test
    fun deleteGroup_removesGroup() = runBlocking {
        val groupId = dao.insertGroup(
            AppGroup(name = "Social Media")
        ).toInt()

        val deletedRows = dao.deleteGroup(groupId)
        val groups = dao.observeGroupsWithApps().first()

        assertEquals(1, deletedRows)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun updateNonexistentGroup_returnsZero() = runBlocking {
        val updatedRows = dao.updateGroup(
            groupId = 9999,
            name = "Unknown",
            timeLimit = 30,
            limitType = LimitType.HARD
        )

        assertEquals(0, updatedRows)
    }
}
