package com.example.stopscrolling.usage

import java.util.Calendar
import java.util.TimeZone

/*
 * Helper to find where "this week" starts (US-13).
 *
 * A week runs from Monday 00:00 to Sunday 23:59 in the local time zone.
 * As for the day (see [DayBoundary]), we never store a counter to reset: we always
 * ask for the usage between the start of the week and now, so the weekly usage
 * starts again from zero every Monday at 00:00 without any code that runs at that time.
 */
object WeekBoundary {

    /*
     * Returns the start of the week (Monday 00:00:00.000) that contains [nowMillis],
     * in milliseconds since the epoch.
     */
    fun startOfWeekMillis(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = DayBoundary.startOfDayMillis(nowMillis, timeZone)
        // Calendar.SUNDAY = 1, MONDAY = 2 ... SATURDAY = 7.
        // Monday gives 0, Tuesday 1 ... Sunday 6.
        val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        // We remove calendar days, not 24 hours: a day can have 23 or 25 hours
        // when the clocks change.
        calendar.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        return calendar.timeInMillis
    }

    /* Returns the start of the next week (next Monday 00:00), in milliseconds since the epoch. */
    fun startOfNextWeekMillis(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = startOfWeekMillis(nowMillis, timeZone)
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        return calendar.timeInMillis
    }
}
