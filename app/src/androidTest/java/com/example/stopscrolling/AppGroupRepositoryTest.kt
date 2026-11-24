package com.example.stopscrolling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.PendingChangeType
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // Fixed time for testing pending changes.
    private fun timeOf(day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
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

            // Soft limit changes take effect immediately.
            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf("com.instagram.android"),
                timeLimit = 60,
                limitType = LimitType.SOFT
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

            assertFalse(updated)
            assertTrue(repository.observeGroups().first().isEmpty())
        } finally {
            db.close()
        }
    }

    // ---------- US-09: Update Group Tests ----------

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

    // ---------- US-09 and US-15 Integration Test ----------

    @Test
    fun updateGroup_hardLimitChange_waitsUntilMidnight() = runBlocking {
        val db = createDatabase()

        try {
            var now = timeOf(23, 14, 0)

            val repository = AppGroupRepositoryImpl(db) { now }

            val groupId = repository.createGroup(
                name = "Games",
                packageNames = listOf("com.example.game"),
                timeLimit = 30,
                limitType = LimitType.HARD
            )

            val updated = repository.updateGroup(
                groupId = groupId,
                name = "Updated Games",
                packageNames = listOf("com.example.game"),
                timeLimit = 60,
                limitType = LimitType.SOFT
            )

            assertTrue(updated)

            val before = repository.observeGroups().first().single().group

            // Name changes immediately.
            assertEquals("Updated Games", before.name)

            // Current hard limit remains active.
            assertEquals(30, before.timeLimit)
            assertEquals(LimitType.HARD, before.limitType)

            // New limit is scheduled for midnight.
            assertEquals(60, before.pendingTimeLimit)
            assertEquals(LimitType.SOFT, before.pendingLimitType)
            assertEquals(timeOf(24, 0, 0), before.pendingFrom)

            // Before midnight, nothing should be applied.
            now = timeOf(23, 23, 59)

            assertEquals(0, repository.applyDuePendingChanges())

            // Apply the pending limit at midnight.
            now = timeOf(24, 0, 0)

            assertEquals(1, repository.applyDuePendingChanges())

            val after = repository.observeGroups().first().single().group

            assertEquals("Updated Games", after.name)
            assertEquals(60, after.timeLimit)
            assertEquals(LimitType.SOFT, after.limitType)

            assertNull(after.pendingTimeLimit)
            assertNull(after.pendingLimitType)
            assertNull(after.pendingFrom)
        } finally {
            db.close()
        }
    }

    // ---------- US-09: Delete Group Tests ----------

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

    // ---------- US-09: Pending Group Changes Tests ----------

    @Test
    fun hardLimitDeletion_waitsUntilScheduledTime() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db) {
                timeOf(23, 14, 0)
            }

            val groupId = repository.createGroup(
                name = "Hard Limit Test",
                packageNames = listOf("com.example.app1"),
                timeLimit = 3,
                limitType = LimitType.HARD
            )

            assertTrue(repository.deleteGroup(groupId))

            val pending = repository.observePendingChanges()
                .first()
                .single {
                    it.groupId == groupId &&
                            it.changeType ==
                            PendingChangeType.DELETE_GROUP.name
                }

            assertEquals(
                timeOf(24, 0, 0),
                pending.executeAtMillis
            )

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
            val repository = AppGroupRepositoryImpl(db) {
                timeOf(23, 14, 0)
            }

            val groupId = repository.createGroup(
                name = "Hard Limit Apps",
                packageNames = listOf(
                    "com.example.app1",
                    "com.example.app2"
                ),
                timeLimit = 3,
                limitType = LimitType.HARD
            )

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

            assertEquals(
                timeOf(24, 0, 0),
                pending.executeAtMillis
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

    // ---------- US-15: Pending Limit Changes Tests ----------

    @Test
    fun updateLimit_hardLimit_isSavedAsPending() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db) {
                timeOf(23, 14, 0)
            }

            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf("com.instagram.android"),
                timeLimit = 60,
                limitType = LimitType.HARD
            )

            val updated = repository.updateLimit(
                groupId,
                30,
                LimitType.SOFT
            )

            assertTrue(updated)

            val group = repository.observeGroups().first()[0].group

            // Current limit stays active.
            assertEquals(60, group.timeLimit)
            assertEquals(LimitType.HARD, group.limitType)

            // New limit waits for midnight.
            assertEquals(30, group.pendingTimeLimit)
            assertEquals(LimitType.SOFT, group.pendingLimitType)
            assertEquals(timeOf(24, 0, 0), group.pendingFrom)
        } finally {
            db.close()
        }
    }

    @Test
    fun applyDuePendingChanges_atMidnight_appliesTheChange() = runBlocking {
        val db = createDatabase()

        try {
            var now = timeOf(23, 14, 0)

            val repository = AppGroupRepositoryImpl(db) { now }

            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf("com.instagram.android"),
                timeLimit = 60,
                limitType = LimitType.HARD
            )

            repository.updateLimit(groupId, 30, LimitType.SOFT)

            now = timeOf(24, 0, 0)

            val changedGroups = repository.applyDuePendingChanges()

            assertEquals(1, changedGroups)

            val group = repository.observeGroups().first()[0].group

            assertEquals(30, group.timeLimit)
            assertEquals(LimitType.SOFT, group.limitType)

            assertNull(group.pendingTimeLimit)
            assertNull(group.pendingLimitType)
            assertNull(group.pendingFrom)

            // Nothing is left to apply.
            assertEquals(0, repository.applyDuePendingChanges())
        } finally {
            db.close()
        }
    }

    @Test
    fun applyDuePendingChanges_beforeMidnight_keepsTheChangeWaiting() =
        runBlocking {

            val db = createDatabase()

            try {
                var now = timeOf(23, 14, 0)

                val repository = AppGroupRepositoryImpl(db) { now }

                val groupId = repository.createGroup(
                    name = "Social Media",
                    packageNames = listOf("com.instagram.android"),
                    timeLimit = 60,
                    limitType = LimitType.HARD
                )

                repository.updateLimit(groupId, 30, LimitType.SOFT)

                now = timeOf(23, 23, 59)

                val changedGroups = repository.applyDuePendingChanges()

                assertEquals(0, changedGroups)

                val group = repository.observeGroups().first()[0].group

                assertEquals(60, group.timeLimit)
                assertEquals(30, group.pendingTimeLimit)
            } finally {
                db.close()
            }
        }

    @Test
    fun updateLimit_afterMidnightWithoutApplying_appliesThePendingChangeFirst() =
        runBlocking {

            val db = createDatabase()

            try {
                var now = timeOf(23, 14, 0)

                val repository = AppGroupRepositoryImpl(db) { now }

                val groupId = repository.createGroup(
                    name = "Social Media",
                    packageNames = listOf("com.instagram.android"),
                    timeLimit = 60,
                    limitType = LimitType.HARD
                )

                repository.updateLimit(groupId, 30, LimitType.SOFT)

                // Service did not run at midnight.
                now = timeOf(24, 8, 0)

                repository.updateLimit(groupId, 90, LimitType.SOFT)

                val group = repository.observeGroups().first()[0].group

                assertEquals(90, group.timeLimit)
                assertEquals(LimitType.SOFT, group.limitType)
                assertNull(group.pendingTimeLimit)
            } finally {
                db.close()
            }
        }

    @Test
    fun pendingChange_survivesClosingTheDatabase() = runBlocking {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        val dbName = "pending-change-test.db"

        context.deleteDatabase(dbName)

        try {
            // First start: edit a hard limit.
            val firstDb = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                dbName
            ).build()

            val groupId = try {
                val repository = AppGroupRepositoryImpl(firstDb) {
                    timeOf(23, 14, 0)
                }

                val id = repository.createGroup(
                    name = "Social Media",
                    packageNames = listOf("com.instagram.android"),
                    timeLimit = 60,
                    limitType = LimitType.HARD
                )

                repository.updateLimit(id, 30, LimitType.HARD)

                id
            } finally {
                firstDb.close()
            }

            // Second start: pending change must still exist.
            val secondDb = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                dbName
            ).build()

            try {
                val group = AppGroupRepositoryImpl(secondDb)
                    .observeGroups()
                    .first()[0].group

                assertEquals(groupId, group.id)
                assertEquals(60, group.timeLimit)
                assertEquals(30, group.pendingTimeLimit)
                assertEquals(timeOf(24, 0, 0), group.pendingFrom)
            } finally {
                secondDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
