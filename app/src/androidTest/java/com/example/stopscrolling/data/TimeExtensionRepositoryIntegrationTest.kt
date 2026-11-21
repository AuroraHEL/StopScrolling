package com.example.stopscrolling.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stopscrolling.data.repository.RoomTimeExtensionRepository
import com.example.stopscrolling.ui.overlay.EXTENSION_MINUTES
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class TimeExtensionRepositoryIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTimeExtensionRepository

    private val utc = TimeZone.getTimeZone("UTC")

    // 2026-11-21 12:00 UTC
    private val dayOne = 1795262400000L

    // 2026-11-22 12:00 UTC
    private val dayTwo = dayOne + 24 * 60 * 60 * 1000L

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()

        repository = RoomTimeExtensionRepository(
            dao = database.timeExtensionDao(),
            timeZoneProvider = { utc }
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun grantExtension_saves15Minutes() = runBlocking {
        repository.grantExtension(
            groupId = 1,
            minutes = EXTENSION_MINUTES,
            nowMillis = dayOne
        )

        val result = repository.extraMinutesOnDay(
            groupId = 1,
            nowMillis = dayOne
        )

        assertEquals(15, result)
    }

    @Test
    fun multipleExtensions_addUpTo30Minutes() = runBlocking {
        repository.grantExtension(1, 15, dayOne)
        repository.grantExtension(1, 15, dayOne)

        val result = repository.extraMinutesOnDay(1, dayOne)

        assertEquals(30, result)
    }

    @Test
    fun extensions_areSeparatedByGroup() = runBlocking {
        repository.grantExtension(1, 15, dayOne)
        repository.grantExtension(2, 30, dayOne)

        assertEquals(
            15,
            repository.extraMinutesOnDay(1, dayOne)
        )

        assertEquals(
            30,
            repository.extraMinutesOnDay(2, dayOne)
        )
    }

    @Test
    fun extensions_resetOnNextDay() = runBlocking {
        repository.grantExtension(1, 15, dayOne)

        assertEquals(
            15,
            repository.extraMinutesOnDay(1, dayOne)
        )

        assertEquals(
            0,
            repository.extraMinutesOnDay(1, dayTwo)
        )
    }

    @Test
    fun invalidMinutes_areRejected() = runBlocking {
        var rejected = false

        try {
            repository.grantExtension(
                groupId = 1,
                minutes = 0,
                nowMillis = dayOne
            )
        } catch (e: IllegalArgumentException) {
            rejected = true
        }

        assertEquals(true, rejected)
        assertEquals(
            0,
            repository.extraMinutesOnDay(1, dayOne)
        )
    }
}
