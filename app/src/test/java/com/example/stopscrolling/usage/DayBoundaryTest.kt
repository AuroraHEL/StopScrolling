package com.example.stopscrolling.usage

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DayBoundaryTest {

    private val zone = TimeZone.getTimeZone("Europe/Helsinki")

    // Builds a time in milliseconds for the test time zone
    private fun timeOf(day: Int, hour: Int, minute: Int, second: Int): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @Test
    fun startOfDay_isMidnightOfTheSameDay() {
        val afternoon = timeOf(16, 15, 30, 45)

        val start = DayBoundary.startOfDayMillis(afternoon, zone)

        assertEquals(timeOf(16, 0, 0, 0), start)
    }

    @Test
    fun atMidnight_theNewDayStarts() {
        val lastSecondOfTheDay = timeOf(16, 23, 59, 59)
        val midnight = timeOf(17, 0, 0, 0)

        val startBefore = DayBoundary.startOfDayMillis(lastSecondOfTheDay, zone)
        val startAfter = DayBoundary.startOfDayMillis(midnight, zone)

        assertNotEquals(startBefore, startAfter)
        assertEquals(midnight, startAfter)
    }
}