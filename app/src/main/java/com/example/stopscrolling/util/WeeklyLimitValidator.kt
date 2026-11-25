package com.example.stopscrolling.util

/** Why a weekly limit is refused (US-13). Each error has its own message on the screen. */
enum class WeeklyLimitError {
    EMPTY,
    NOT_POSITIVE,
    TOO_LONG,
    BELOW_DAILY
}

/*
 * Rules of the weekly limit (US-13). No Android here, so they are easy to test.
 * A weekly limit is entered in hours and minutes and is stored in minutes.
 */
object WeeklyLimitValidator {

    const val MAX_WEEKLY_HOURS = 168 // 7 days
    const val MAX_WEEKLY_MINUTES = MAX_WEEKLY_HOURS * 60

    /*
     * Turns the two text fields into minutes.
     * Returns null if both are empty or if a field is not a number.
     * One empty field counts as 0: "2" hours and "" minutes is 120 minutes.
     */
    fun parseMinutes(hoursText: String, minutesText: String): Int? {
        val hoursBlank = hoursText.isBlank()
        val minutesBlank = minutesText.isBlank()
        if (hoursBlank && minutesBlank) return null

        val hours = if (hoursBlank) 0 else hoursText.trim().toIntOrNull()
        val minutes = if (minutesBlank) 0 else minutesText.trim().toIntOrNull()
        if (hours == null || minutes == null || hours < 0 || minutes < 0) return null

        // The fields are limited to 3 digits on the screen, so this cannot overflow.
        return hours * 60 + minutes
    }

    /*
     * Returns the first error of a weekly limit, or null if it is valid.
     * [weeklyMinutes] is null when the field is empty.
     * [dailyMinutes] is the daily limit of the group (0 = no daily limit, nothing to compare).
     */
    fun validate(weeklyMinutes: Int?, dailyMinutes: Int): WeeklyLimitError? = when {
        weeklyMinutes == null -> WeeklyLimitError.EMPTY
        weeklyMinutes <= 0 -> WeeklyLimitError.NOT_POSITIVE
        weeklyMinutes > MAX_WEEKLY_MINUTES -> WeeklyLimitError.TOO_LONG
        dailyMinutes > 0 && weeklyMinutes < dailyMinutes -> WeeklyLimitError.BELOW_DAILY
        else -> null
    }
}
