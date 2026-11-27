package com.example.stopscrolling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.TimeExtension
import com.example.stopscrolling.data.repository.RoomTimeExtensionRepository
import com.example.stopscrolling.usage.DayBoundary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

// US-13: extensions granted since Monday 00:00 are added to the weekly soft limit
@RunWith(AndroidJUnit4::class)
class WeeklyExtensionsTest {

    private val zone = TimeZone.getTimeZone("Europe/Helsinki")

    // 2026-11-23 is a Monday. [day] is the day of November 2026.
    private fun timeOf(day: Int, hour: Int = 12): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.NOVEMBER, day, hour, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @Test
    fun extensionsOfTheWeek_areSummed_andThePreviousWeekIsIgnored() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        try {
            val dao = db.timeExtensionDao()
            val repository = RoomTimeExtensionRepository(dao) { zone }
            fun day(day: Int) = DayBoundary.startOfDayMillis(timeOf(day), zone)

            dao.insertExtension(TimeExtension(groupId = 1, dayStartMillis = day(22), grantedMinutes = 15)) // Sunday before
            dao.insertExtension(TimeExtension(groupId = 1, dayStartMillis = day(23), grantedMinutes = 15)) // Monday
            dao.insertExtension(TimeExtension(groupId = 1, dayStartMillis = day(25), grantedMinutes = 15)) // Wednesday
            dao.insertExtension(TimeExtension(groupId = 1, dayStartMillis = day(25), grantedMinutes = 15)) // Wednesday again
            dao.insertExtension(TimeExtension(groupId = 2, dayStartMillis = day(25), grantedMinutes = 15)) // other group

            // Wednesday: Monday + Wednesday x2 = 45 (the Sunday before is another week)
            assertEquals(45, repository.extraMinutesInWeek(groupId = 1, nowMillis = timeOf(25)))
            // The next Monday starts a new week: back to 0
            assertEquals(0, repository.extraMinutesInWeek(groupId = 1, nowMillis = timeOf(30, hour = 0)))
        } finally {
            db.close()
        }
    }
}
