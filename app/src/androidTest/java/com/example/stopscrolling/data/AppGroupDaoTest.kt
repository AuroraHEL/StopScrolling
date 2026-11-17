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
        val context = ApplicationProvider
            .getApplicationContext<Context>()

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
}
