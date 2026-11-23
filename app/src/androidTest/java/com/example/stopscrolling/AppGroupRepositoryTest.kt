package com.example.stopscrolling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

            // A soft limit changes at once. A change to a hard limit waits until midnight (US-15).
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

            assertEquals(false, updated)
            assertEquals(0, repository.observeGroups().first().size)
        } finally {
            db.close()
        }
    }

    // ---------- US-15: pending changes of a hard limit ----------

    // A time of November 2026 in the time zone of the phone, like the repository uses
    private fun timeOf(day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @Test
    fun updateLimit_hardLimit_isSavedAsPending() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db) { timeOf(23, 14, 0) }
            val groupId = repository.createGroup(
                name = "Social Media",
                packageNames = listOf("com.instagram.android"),
                timeLimit = 60,
                limitType = LimitType.HARD
            )

            val updated = repository.updateLimit(groupId, 30, LimitType.SOFT)

            assertTrue(updated)
            val group = repository.observeGroups().first()[0].group
            // The current limit stays active
            assertEquals(60, group.timeLimit)
            assertEquals(LimitType.HARD, group.limitType)
            // The new limit waits for midnight
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
            // Nothing is left to apply
            assertEquals(0, repository.applyDuePendingChanges())
        } finally {
            db.close()
        }
    }

    @Test
    fun applyDuePendingChanges_beforeMidnight_keepsTheChangeWaiting() = runBlocking {
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
    fun updateLimit_afterMidnightWithoutApplying_appliesThePendingChangeFirst() = runBlocking {
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

            // The service did not run at midnight. The user edits the group the next morning.
            now = timeOf(24, 8, 0)
            repository.updateLimit(groupId, 90, LimitType.SOFT)

            // The old pending change became the current limit, so this edit (soft) works at once
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "pending-change-test.db"
        context.deleteDatabase(dbName)

        try {
            // First start: the user edits a hard limit
            val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
            val groupId = try {
                val repository = AppGroupRepositoryImpl(firstDb) { timeOf(23, 14, 0) }
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

            // Second start (like after a restart of the phone): the pending change is still there
            val secondDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
            try {
                val group = AppGroupRepositoryImpl(secondDb).observeGroups().first()[0].group
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