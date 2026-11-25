package com.example.stopscrolling.usage

import org.junit.Assert.assertEquals
import org.junit.Test

// US-13: weekly usage = past days (cached) + today (live). The two parts must give the same
// result as one calculation over the whole week, also for an app open across midnight.
class UsageCalculatorWeekTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    private val weekStart = 0L
    private val todayStart = 2 * day      // Wednesday 00:00
    private val now = todayStart + 3 * hour // Wednesday 03:00

    private fun resumed(pkg: String, at: Long) = ForegroundEvent(pkg, at, true, "$pkg.Main")
    private fun paused(pkg: String, at: Long) = ForegroundEvent(pkg, at, false, "$pkg.Main")

    private val events = listOf(
        // Monday: 1 hour
        resumed("app.a", 10 * hour),
        paused("app.a", 11 * hour),
        // Tuesday evening to Wednesday 01:00: open across midnight (2 h + 1 h)
        resumed("app.a", 2 * day - 2 * hour),
        paused("app.a", todayStart + 1 * hour),
        // Wednesday: 30 minutes of another app
        resumed("app.b", todayStart + 2 * hour),
        paused("app.b", todayStart + 2 * hour + 30 * minute)
    )

    @Test
    fun pastDaysPlusToday_equalsTheWholeWeek() {
        val past = UsageCalculator.sumForegroundTime(events, weekStart, todayStart)
        val today = UsageCalculator.sumForegroundTime(events, todayStart, now)
        val whole = UsageCalculator.sumForegroundTime(events, weekStart, now)

        for (pkg in listOf("app.a", "app.b")) {
            assertEquals(
                "package $pkg",
                whole[pkg] ?: 0L,
                (past[pkg] ?: 0L) + (today[pkg] ?: 0L)
            )
        }
    }

    @Test
    fun appOpenAcrossMidnight_isSplitBetweenPastDaysAndToday() {
        val past = UsageCalculator.sumForegroundTime(events, weekStart, todayStart)
        val today = UsageCalculator.sumForegroundTime(events, todayStart, now)

        // Past: 1 h on Monday + 2 h on Tuesday evening
        assertEquals(3 * hour, past["app.a"])
        // Today: 1 h after midnight
        assertEquals(1 * hour, today["app.a"])
        assertEquals(30 * minute, today["app.b"])
    }
}
