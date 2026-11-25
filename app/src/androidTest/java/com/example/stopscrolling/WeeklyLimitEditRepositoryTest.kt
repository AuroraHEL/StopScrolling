package com.example.stopscrolling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

// US-13: editing the limits of an existing group (follows the next-day rule of US-15)
@RunWith(AndroidJUnit4::class)
class WeeklyLimitEditRepositoryTest {

    private fun createDatabase(): AppDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    // Wednesday 25 November 2026, 14:00 (device time zone)
    private val wednesdayAfternoon: Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 25, 14, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun hardGroup_weeklyEdit_isPending_andSurvivesReading() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db) { wednesdayAfternoon }
            val id = repository.createGroup(
                name = "Social",
                packageNames = listOf("app.a"),
                timeLimit = 60,
                limitType = LimitType.HARD,
                weeklyLimit = 300
            )

            val saved = repository.updateLimits(id, 60, LimitType.HARD, weeklyLimit = 600)

            val group = repository.observeGroups().first().single().group
            assertEquals(true, saved)
            assertEquals(300, group.weeklyLimit)
            assertEquals(600, group.pendingWeeklyLimit)
        } finally {
            db.close()
        }
    }

    @Test
    fun softGroup_weeklyEdit_worksAtOnce() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db) { wednesdayAfternoon }
            val id = repository.createGroup(
                name = "Video",
                packageNames = listOf("app.v"),
                timeLimit = 60,
                limitType = LimitType.SOFT,
                weeklyLimit = 300
            )

            repository.updateLimits(id, 60, LimitType.SOFT, weeklyLimit = 120)

            val group = repository.observeGroups().first().single().group
            assertEquals(120, group.weeklyLimit)
            assertNull(group.pendingWeeklyLimit)
        } finally {
            db.close()
        }
    }

    @Test
    fun softGroupBecomingHard_weeklyEditStillWorksAtOnce() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db) { wednesdayAfternoon }
            val id = repository.createGroup(
                name = "Video",
                packageNames = listOf("app.v"),
                timeLimit = 60,
                limitType = LimitType.SOFT,
                weeklyLimit = 300
            )

            // Both edits look at the group as it was before: it was soft, so nothing waits
            repository.updateLimits(id, 60, LimitType.HARD, weeklyLimit = 600)

            val group = repository.observeGroups().first().single().group
            assertEquals(LimitType.HARD, group.limitType)
            assertEquals(600, group.weeklyLimit)
            assertNull(group.pendingWeeklyLimit)
        } finally {
            db.close()
        }
    }

    @Test
    fun unknownGroup_returnsFalse() = runBlocking {
        val db = createDatabase()
        try {
            val repository = AppGroupRepositoryImpl(db) { wednesdayAfternoon }
            assertFalse(repository.updateLimits(999, 60, LimitType.HARD, weeklyLimit = 0))
        } finally {
            db.close()
        }
    }
}
