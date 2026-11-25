package com.example.stopscrolling.usage

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// US-13: the week runs from Monday 00:00 to Sunday 23:59 (local time)
class WeekBoundaryTest {

    private val zone = TimeZone.getTimeZone("Europe/Helsinki")

    // 2026-11-23 is a Monday. [day] is the day of November 2026.
    private fun timeOf(day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, millis: Int = 0): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, millis)
        return calendar.timeInMillis
    }

    private val mondayMidnight = timeOf(23)

    @Test
    fun monday_atMidnight_startsTheWeekRightThere() {
        assertEquals(mondayMidnight, WeekBoundary.startOfWeekMillis(timeOf(23, 0, 0, 0), zone))
    }

    @Test
    fun monday_inTheAfternoon_returnsTheMondayMidnight() {
        assertEquals(mondayMidnight, WeekBoundary.startOfWeekMillis(timeOf(23, 15, 30), zone))
    }

    @Test
    fun wednesday_returnsTheMondayBefore() {
        assertEquals(mondayMidnight, WeekBoundary.startOfWeekMillis(timeOf(25, 9, 0), zone))
    }

    @Test
    fun sunday_lastMillisecond_stillBelongsToTheWeekOfThatMonday() {
        val sundayEnd = timeOf(29, 23, 59, 59, 999)
        assertEquals(mondayMidnight, WeekBoundary.startOfWeekMillis(sundayEnd, zone))
    }

    @Test
    fun nextMondayMidnight_startsANewWeek_andUsageResets() {
        val sundayEnd = timeOf(29, 23, 59, 59, 999)
        val nextMonday = timeOf(30, 0, 0, 0, 0)

        val weekOfSunday = WeekBoundary.startOfWeekMillis(sundayEnd, zone)
        val weekOfNextMonday = WeekBoundary.startOfWeekMillis(nextMonday, zone)

        assertNotEquals(weekOfSunday, weekOfNextMonday)
        assertEquals(nextMonday, weekOfNextMonday)
    }

    @Test
    fun startOfNextWeek_isTheNextMonday() {
        assertEquals(timeOf(30), WeekBoundary.startOfNextWeekMillis(timeOf(26, 12, 0), zone))
        // Sunday evening: the next week starts in a few minutes
        assertEquals(timeOf(30), WeekBoundary.startOfNextWeekMillis(timeOf(29, 23, 50), zone))
    }

    @Test
    fun weekWithClockChange_stillStartsAtMondayMidnight() {
        // In Europe/Helsinki the clocks go back on Sunday 25 October 2026 (at 04:00).
        // That week has 7 days + 1 hour: its start must still be Monday 19 October 00:00.
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.OCTOBER, 19, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monday = calendar.timeInMillis

        calendar.set(2026, Calendar.OCTOBER, 25, 22, 0, 0) // Sunday evening, after the change
        val sunday = calendar.timeInMillis

        assertEquals(monday, WeekBoundary.startOfWeekMillis(sunday, zone))
        // And the next week starts on Monday 26 October 00:00
        calendar.set(2026, Calendar.OCTOBER, 26, 0, 0, 0)
        assertEquals(calendar.timeInMillis, WeekBoundary.startOfNextWeekMillis(sunday, zone))
    }
}
