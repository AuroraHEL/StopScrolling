package com.example.stopscrolling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppGroupRepositoryTest {

    private fun createDatabase(): AppDatabase {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        return Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
    }

    @Test
    fun createGroup_savesAndRetrievesLimit() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            repository.createGroup(
                name = "Social Media",
                packageNames = listOf("com.instagram.android"),
                timeLimit = 60,
                limitType = LimitType.SOFT
            )

            val groups = repository.observeGroups().first()

            assertEquals(1, groups.size)
            assertEquals("Social Media", groups[0].group.name)
            assertEquals(60, groups[0].group.timeLimit)
            assertEquals(LimitType.SOFT, groups[0].group.limitType)
            assertEquals(1, groups[0].apps.size)
            assertEquals(
                "com.instagram.android",
                groups[0].apps.first().packageName
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun createGroup_usesDefaultLimitValues() = runBlocking {
        val db = createDatabase()

        try {
            // Use the interface to test default arguments
            val repository: AppGroupRepository =
                AppGroupRepositoryImpl(db)

            repository.createGroup(
                name = "Games",
                packageNames = emptyList()
            )

            val groups = repository.observeGroups().first()

            assertEquals(1, groups.size)
            assertEquals(0, groups[0].group.timeLimit)
            assertEquals(LimitType.HARD, groups[0].group.limitType)
        } finally {
            db.close()
        }
    }

    @Test
    fun createGroup_rejectsNegativeTimeLimit() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)
            var exceptionThrown = false

            try {
                repository.createGroup(
                    name = "Games",
                    packageNames = emptyList(),
                    timeLimit = -10,
                    limitType = LimitType.HARD
                )
            } catch (e: IllegalArgumentException) {
                exceptionThrown = true
            }

            assertTrue(
                "Negative time limit should throw IllegalArgumentException",
                exceptionThrown
            )

            assertEquals(
                0,
                repository.observeGroups().first().size
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun updateLimit_updatesExistingGroup() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf("com.instagram.android"),
                timeLimit = 60,
                limitType = LimitType.HARD
            )

            val updated = repository.updateLimit(
                groupId = groupId,
                timeLimit = 90,
                limitType = LimitType.SOFT
            )

            assertTrue(updated)

            val groups = repository.observeGroups().first()
            assertEquals(1, groups.size)
            assertEquals(groupId, groups[0].group.id)
            assertEquals(90, groups[0].group.timeLimit)
            assertEquals(LimitType.SOFT, groups[0].group.limitType)
            assertEquals(1, groups[0].apps.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun updateLimit_rejectsNegativeTimeLimit() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Games",
                packageNames = emptyList(),
                timeLimit = 30
            )

            var exceptionThrown = false

            try {
                repository.updateLimit(
                    groupId = groupId,
                    timeLimit = -10,
                    limitType = LimitType.HARD
                )
            } catch (e: IllegalArgumentException) {
                exceptionThrown = true
            }

            assertTrue(exceptionThrown)

            val groups = repository.observeGroups().first()
            assertEquals(30, groups[0].group.timeLimit)
        } finally {
            db.close()
        }
    }

    @Test
    fun updateLimit_returnsFalseForNonexistentGroup() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            val updated = repository.updateLimit(
                groupId = 999,
                timeLimit = 60,
                limitType = LimitType.SOFT
            )

            assertEquals(false, updated)
            assertEquals(0, repository.observeGroups().first().size)
        } finally {
            db.close()
        }
    }

    // ---------- US-09 Task 2 Tests ----------

    @Test
    fun updateGroup_updatesDetailsAndReplacesApps() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf(
                    "com.instagram.android",
                    "com.example.oldapp"
                ),
                timeLimit = 30,
                limitType = LimitType.HARD
            )

            val updated = repository.updateGroup(
                groupId = groupId,
                name = "Study Apps",
                packageNames = listOf(
                    "com.example.newapp",
                    "com.instagram.android"
                ),
                timeLimit = 60,
                limitType = LimitType.SOFT
            )

            assertTrue(updated)

            val groups = repository.observeGroups().first()
            assertEquals(1, groups.size)

            val group = groups.first()
            assertEquals(groupId, group.group.id)
            assertEquals("Study Apps", group.group.name)
            assertEquals(60, group.group.timeLimit)
            assertEquals(LimitType.SOFT, group.group.limitType)

            val packages = group.apps.map { it.packageName }.toSet()
            assertEquals(
                setOf(
                    "com.example.newapp",
                    "com.instagram.android"
                ),
                packages
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun updateGroup_returnsFalseForNonexistentGroup() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db)

            val updated = repository.updateGroup(
                groupId = 9999,
                name = "Unknown",
                packageNames = listOf("com.example.app"),
                timeLimit = 30,
                limitType = LimitType.HARD
            )

            assertEquals(false, updated)
            assertTrue(repository.observeGroups().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun updateGroup_rejectsInvalidTimeLimit() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Games",
                packageNames = listOf("com.example.game"),
                timeLimit = 30
            )

            var exceptionThrown = false

            try {
                repository.updateGroup(
                    groupId = groupId,
                    name = "Updated Games",
                    packageNames = listOf("com.example.newgame"),
                    timeLimit = -10,
                    limitType = LimitType.SOFT
                )
            } catch (e: IllegalArgumentException) {
                exceptionThrown = true
            }

            assertTrue(exceptionThrown)

            val group = repository.observeGroups().first().single()
            assertEquals("Games", group.group.name)
            assertEquals(30, group.group.timeLimit)
            assertEquals(LimitType.HARD, group.group.limitType)
            assertEquals(
                listOf("com.example.game"),
                group.apps.map { it.packageName }
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteGroup_removesGroupAndApps() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf(
                    "com.instagram.android",
                    "com.example.app"
                )
            )

            val deleted = repository.deleteGroup(groupId)

            assertTrue(deleted)
            assertTrue(repository.observeGroups().first().isEmpty())

            // Verify that app associations were removed too.
            val remainingApps = db.openHelper.readableDatabase
                .query(
                    "SELECT * FROM group_apps WHERE groupId = ?",
                    arrayOf(groupId.toString())
                ).use { cursor ->
                    cursor.count
                }

            assertEquals(0, remainingApps)
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteGroup_returnsFalseForNonexistentGroup() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db)

            val deleted = repository.deleteGroup(9999)

            assertEquals(false, deleted)
            assertTrue(repository.observeGroups().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
