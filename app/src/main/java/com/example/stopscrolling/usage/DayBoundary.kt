package com.example.stopscrolling.usage

import java.util.Calendar
import java.util.TimeZone

/*
 * Helper to find where "today" starts.
 *
 * A day always starts at midnight (00:00) in the local time zone.
 * We never store a counter that we have to reset: every time we measure the usage
 * we ask for the time between the start of today and now, so after midnight
 * the usage starts again from zero automatically.
 */

object DayBoundary {

    /*
     * Returns the start of the day (00:00:00.000) that contains [nowMillis],
     * in milliseconds since the epoch.
     */
    fun startOfDayMillis(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /*
     * Returns the start of the next day (00:00 of tomorrow), in milliseconds since the epoch.
     * US-15 uses it as the moment when a pending change of a hard limit starts.
     * We add one calendar day, not 24 hours, because a day can have 23 or 25 hours
     * when the clocks change.
     */
    fun startOfNextDayMillis(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = startOfDayMillis(nowMillis, timeZone)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }
}