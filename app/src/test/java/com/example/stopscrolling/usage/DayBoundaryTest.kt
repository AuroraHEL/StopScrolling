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

    @Test
    fun startOfNextDay_isMidnightOfTheFollowingDay() {
        val afternoon = timeOf(16, 15, 30, 45)

        val next = DayBoundary.startOfNextDayMillis(afternoon, zone)

        assertEquals(timeOf(17, 0, 0, 0), next)
    }

    @Test
    fun startOfNextDay_atMidnight_isTheNextMidnight() {
        val midnight = timeOf(17, 0, 0, 0)

        val next = DayBoundary.startOfNextDayMillis(midnight, zone)

        assertEquals(timeOf(18, 0, 0, 0), next)
    }

    @Test
    fun startOfNextDay_atTheEndOfTheMonth_isTheFirstOfTheNextMonth() {
        val evening = timeOf(30, 22, 0, 0)

        val next = DayBoundary.startOfNextDayMillis(evening, zone)

        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.DECEMBER, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        assertEquals(calendar.timeInMillis, next)
    }

    @Test
    fun startOfNextDay_whenTheClocksGoBack_stillStartsAtMidnight() {
        // On 25 October 2026 the clocks in Helsinki go back, so this day has 25 hours
        val noon = Calendar.getInstance(zone)
        noon.set(2026, Calendar.OCTOBER, 25, 12, 0, 0)
        noon.set(Calendar.MILLISECOND, 0)
        val expected = Calendar.getInstance(zone)
        expected.set(2026, Calendar.OCTOBER, 26, 0, 0, 0)
        expected.set(Calendar.MILLISECOND, 0)

        val next = DayBoundary.startOfNextDayMillis(noon.timeInMillis, zone)

        assertEquals(expected.timeInMillis, next)
    }
}