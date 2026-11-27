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
import org.junit.Test
import org.junit.runner.RunWith

// US-13: the weekly limit is saved with the group
@RunWith(AndroidJUnit4::class)
class WeeklyLimitRepositoryTest {

    private fun createDatabase(): AppDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun createGroup_savesWeeklyLimit() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            repository.createGroup(
                name = "Social Media",
                packageNames = listOf("com.instagram.android"),
                timeLimit = 60,
                limitType = LimitType.HARD,
                weeklyLimit = 300
            )

            val group = repository.observeGroups().first().single().group

            assertEquals(60, group.timeLimit)
            assertEquals(300, group.weeklyLimit)
        } finally {
            db.close()
        }
    }

    @Test
    fun createGroup_withoutWeeklyLimit_savesZero() = runBlocking {
        val db = createDatabase()

        try {
            val repository = AppGroupRepositoryImpl(db)

            repository.createGroup(
                name = "Games",
                packageNames = listOf("com.example.game")
            )

            assertEquals(0, repository.observeGroups().first().single().group.weeklyLimit)
        } finally {
            db.close()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun createGroup_withNegativeWeeklyLimit_isRefused(): Unit = runBlocking {
        val db = createDatabase()

        try {
            AppGroupRepositoryImpl(db).createGroup(
                name = "Bad",
                packageNames = listOf("com.example.bad"),
                weeklyLimit = -1
            )
            Unit
        } finally {
            db.close()
        }
    }
}
