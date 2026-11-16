package com.example.stopscrolling.usage

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCalculatorTest {

    // A fake "day" that goes from 0 to 100 seconds (values are in milliseconds)
    private val dayStart = 0L
    private val now = 100_000L

    @Test
    fun oneSession_isCounted() {
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 25_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(15_000L, result["app.a"])
    }

    @Test
    fun twoSessionsOfTheSameApp_areSummed() {
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 20_000, false),
            ForegroundEvent("app.a", 50_000, true),
            ForegroundEvent("app.a", 60_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(20_000L, result["app.a"])
    }

    @Test
    fun differentApps_areCountedSeparately() {
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 20_000, false),
            ForegroundEvent("app.b", 20_000, true),
            ForegroundEvent("app.b", 50_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
        assertEquals(30_000L, result["app.b"])
    }

    @Test
    fun appStillInForeground_isCountedUntilNow() {
        val events = listOf(ForegroundEvent("app.a", 90_000, true))

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
    }

    @Test
    fun appOpenedBeforeMidnight_isCountedFromStartOfDay() {
        // The first event of the day is "went to background": it was open since midnight
        val events = listOf(ForegroundEvent("app.a", 5_000, false))

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(5_000L, result["app.a"])
    }

    @Test
    fun strayBackgroundEvent_isIgnored() {
        // The second "background" event has no matching "foreground" event
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 20_000, false),
            ForegroundEvent("app.a", 30_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
    }

    @Test
    fun noEvents_giveEmptyResult() {
        val result = UsageCalculator.sumForegroundTime(emptyList(), dayStart, now)

        assertTrue(result.isEmpty())
    }
}

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