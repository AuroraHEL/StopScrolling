package com.example.stopscrolling.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeExtensionLogicTest {

    @Test
    fun sameDayTimestamps_shareSameDayStartMillis() {
        val tz = TimeZone.getTimeZone("Europe/Helsinki")
        val morning = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 19, 8, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val evening = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 19, 22, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(
            DayBoundary.startOfDayMillis(morning, tz),
            DayBoundary.startOfDayMillis(evening, tz)
        )
    }

    @Test
    fun nextDayAfterMidnight_producesNewDayStartMillis() {
        val tz = TimeZone.getTimeZone("Europe/Helsinki")
        val beforeMidnight = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 19, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val afterMidnight = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 20, 0, 0, 0)
            set(Calendar.MILLISECOND, 1)
        }.timeInMillis

        assertNotEquals(
            DayBoundary.startOfDayMillis(beforeMidnight, tz),
            DayBoundary.startOfDayMillis(afterMidnight, tz)
        )
    }
}