package com.example.stopscrolling.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// US-13: non-empty, greater than 0, at most 168 hours, not lower than the daily limit
class WeeklyLimitValidatorTest {

    // ----- parseMinutes -----

    @Test
    fun parse_hoursAndMinutes() {
        assertEquals(150, WeeklyLimitValidator.parseMinutes("2", "30"))
    }

    @Test
    fun parse_onlyHours() {
        assertEquals(120, WeeklyLimitValidator.parseMinutes("2", ""))
    }

    @Test
    fun parse_onlyMinutes() {
        assertEquals(45, WeeklyLimitValidator.parseMinutes("", "45"))
    }

    @Test
    fun parse_bothEmpty_isNull() {
        assertNull(WeeklyLimitValidator.parseMinutes("", ""))
        assertNull(WeeklyLimitValidator.parseMinutes("  ", " "))
    }

    @Test
    fun parse_notANumber_isNull() {
        assertNull(WeeklyLimitValidator.parseMinutes("a", "10"))
    }

    // ----- validate -----

    @Test
    fun empty_isRefused() {
        assertEquals(WeeklyLimitError.EMPTY, WeeklyLimitValidator.validate(null, 0))
    }

    @Test
    fun zero_isRefused() {
        assertEquals(WeeklyLimitError.NOT_POSITIVE, WeeklyLimitValidator.validate(0, 0))
    }

    @Test
    fun negative_isRefused() {
        assertEquals(WeeklyLimitError.NOT_POSITIVE, WeeklyLimitValidator.validate(-5, 0))
    }

    @Test
    fun exactly168Hours_isAccepted() {
        assertNull(WeeklyLimitValidator.validate(168 * 60, 0))
    }

    @Test
    fun moreThan168Hours_isRefused() {
        assertEquals(WeeklyLimitError.TOO_LONG, WeeklyLimitValidator.validate(168 * 60 + 1, 0))
    }

    @Test
    fun lowerThanTheDailyLimit_isRefused() {
        assertEquals(WeeklyLimitError.BELOW_DAILY, WeeklyLimitValidator.validate(59, 60))
    }

    @Test
    fun equalToTheDailyLimit_isAccepted() {
        assertNull(WeeklyLimitValidator.validate(60, 60))
    }

    @Test
    fun noDailyLimit_anyPositiveWeeklyLimitIsAccepted() {
        assertNull(WeeklyLimitValidator.validate(1, 0))
    }
}
