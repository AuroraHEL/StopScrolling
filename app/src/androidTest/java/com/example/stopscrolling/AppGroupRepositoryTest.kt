package com.example.stopscrolling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.PendingChangeType
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ---------- Create Group Tests ----------

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

    // ---------- Update Limit Tests ----------

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

            // Increasing a Hard Limit is allowed.
            val updated = repository.updateLimit(
                groupId = groupId,
                timeLimit = 90,
                limitType = LimitType.HARD
            )

            assertTrue(updated)

            val groups = repository.observeGroups().first()

            assertEquals(1, groups.size)
            assertEquals(groupId, groups[0].group.id)
            assertEquals(90, groups[0].group.timeLimit)
            assertEquals(LimitType.HARD, groups[0].group.limitType)
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

            assertFalse(updated)
            assertTrue(repository.observeGroups().first().isEmpty())

        } finally {
            db.close()
        }
    }

    // ---------- US-09 Task 2: Update Group Tests ----------

    @Test
    fun updateGroup_updatesDetailsAndReplacesApps() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            // Use Soft Limit to test immediate app replacement.
            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf(
                    "com.instagram.android",
                    "com.example.oldapp"
                ),
                timeLimit = 30,
                limitType = LimitType.SOFT
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

            val packages = group.apps.map {
                it.packageName
            }.toSet()

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

            assertFalse(updated)
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

    // ---------- Delete Group Tests ----------

    @Test
    fun deleteGroup_removesGroupAndApps() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            // Default time limit is 0, so deletion is immediate.
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

            assertFalse(deleted)
            assertTrue(repository.observeGroups().first().isEmpty())

        } finally {
            db.close()
        }
    }

    // ---------- US-09 Task 3: Pending Changes Tests ----------

    @Test
    fun hardLimitDeletion_waitsUntilScheduledTime() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Hard Limit Test",
                packageNames = listOf("com.example.app1"),
                timeLimit = 3,
                limitType = LimitType.HARD
            )

            // Active Hard Limit schedules deletion.
            assertTrue(repository.deleteGroup(groupId))

            val pending = repository.observePendingChanges()
                .first()
                .single {
                    it.groupId == groupId &&
                            it.changeType ==
                            PendingChangeType.DELETE_GROUP.name
                }

            // Before midnight: group must remain.
            val processedBefore = repository.processDueChanges(
                nowMillis = pending.executeAtMillis - 1
            )

            assertEquals(0, processedBefore)

            assertTrue(
                repository.observeGroups().first()
                    .any { it.group.id == groupId }
            )

            // At midnight: delete the group.
            val processedAt = repository.processDueChanges(
                nowMillis = pending.executeAtMillis
            )

            assertEquals(1, processedAt)

            assertFalse(
                repository.observeGroups().first()
                    .any { it.group.id == groupId }
            )

            assertFalse(
                repository.observePendingChanges().first()
                    .any { it.groupId == groupId }
            )

        } finally {
            db.close()
        }
    }

    @Test
    fun hardLimitAppRemoval_waitsUntilScheduledTime() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Hard Limit Apps",
                packageNames = listOf(
                    "com.example.app1",
                    "com.example.app2"
                ),
                timeLimit = 3,
                limitType = LimitType.HARD
            )

            // Remove app2 from the selected apps.
            assertTrue(
                repository.updateGroup(
                    groupId = groupId,
                    name = "Hard Limit Apps",
                    packageNames = listOf("com.example.app1"),
                    timeLimit = 3,
                    limitType = LimitType.HARD
                )
            )

            val pending = repository.observePendingChanges()
                .first()
                .single {
                    it.groupId == groupId &&
                            it.changeType ==
                            PendingChangeType.REMOVE_APP.name
                }

            assertEquals(
                "com.example.app2",
                pending.packageName
            )

            // Before midnight: both apps remain.
            val processedBefore = repository.processDueChanges(
                nowMillis = pending.executeAtMillis - 1
            )

            assertEquals(0, processedBefore)

            val before = repository.observeGroups()
                .first()
                .single { it.group.id == groupId }

            assertEquals(2, before.apps.size)

            // At midnight: app2 is removed.
            val processedAt = repository.processDueChanges(
                nowMillis = pending.executeAtMillis
            )

            assertEquals(1, processedAt)

            val after = repository.observeGroups()
                .first()
                .single { it.group.id == groupId }

            assertEquals(
                listOf("com.example.app1"),
                after.apps.map { it.packageName }
            )

            assertFalse(
                repository.observePendingChanges().first()
                    .any { it.groupId == groupId }
            )

        } finally {
            db.close()
        }
    }

    @Test
    fun softLimitDeletion_happensImmediately() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            val groupId = repository.createGroup(
                name = "Soft Limit Test",
                packageNames = listOf("com.example.app1"),
                timeLimit = 3,
                limitType = LimitType.SOFT
            )

            assertTrue(repository.deleteGroup(groupId))

            assertFalse(
                repository.observeGroups().first()
                    .any { it.group.id == groupId }
            )

            assertTrue(
                repository.observePendingChanges().first()
                    .isEmpty()
            )

        } finally {
            db.close()
        }
    }
}
