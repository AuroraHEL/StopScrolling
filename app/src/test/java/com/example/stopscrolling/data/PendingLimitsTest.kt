package com.example.stopscrolling.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLimitsTest {

    private val zone = TimeZone.getTimeZone("Europe/Helsinki")

    // Builds a time in milliseconds for the test time zone (November 2026)
    private fun timeOf(day: Int, hour: Int, minute: Int, second: Int = 0): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute, second)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // The user edits in the afternoon of the 23rd. The change starts at midnight of the 24th.
    private val afternoon = timeOf(23, 14, 0)
    private val midnight = timeOf(24, 0, 0)

    private val hardOneHour = AppGroup(
        id = 1,
        name = "Social",
        timeLimit = 60,
        limitType = LimitType.HARD
    )

    private fun edit(
        group: AppGroup,
        minutes: Int,
        type: LimitType,
        now: Long = afternoon
    ) = PendingLimits.applyEdit(group, minutes, type, now, zone)

    // ---------- Edits of a group with a hard limit ----------

    @Test
    fun hardLimit_newDuration_isSavedAsPending() {
        val result = edit(hardOneHour, 30, LimitType.HARD)

        // The current limit does not change
        assertEquals(60, result.timeLimit)
        assertEquals(LimitType.HARD, result.limitType)
        // The new limit waits
        assertEquals(30, result.pendingTimeLimit)
        assertEquals(LimitType.HARD, result.pendingLimitType)
        assertEquals(midnight, result.pendingFrom)
    }

    @Test
    fun hardLimit_switchToSoft_isSavedAsPending() {
        val result = edit(hardOneHour, 60, LimitType.SOFT)

        assertEquals(LimitType.HARD, result.limitType)
        assertEquals(LimitType.SOFT, result.pendingLimitType)
        assertEquals(60, result.pendingTimeLimit)
    }

    @Test
    fun hardLimit_removingTheLimit_isSavedAsPending() {
        val result = edit(hardOneHour, 0, LimitType.HARD)

        assertEquals(60, result.timeLimit)
        assertEquals(0, result.pendingTimeLimit)
    }

    @Test
    fun hardLimit_editAtTheLastSecondOfTheDay_startsAtTheNextMidnight() {
        val result = edit(hardOneHour, 30, LimitType.HARD, now = timeOf(23, 23, 59, 59))

        assertEquals(midnight, result.pendingFrom)
    }

    @Test
    fun hardLimit_editAtMidnightSharp_startsTomorrow() {
        // At 00:00 of the 24th the new day has already started
        val result = edit(hardOneHour, 30, LimitType.HARD, now = midnight)

        assertEquals(timeOf(25, 0, 0), result.pendingFrom)
    }

    @Test
    fun hardLimit_secondEditOfTheSameDay_keepsOnlyTheLatest() {
        val first = edit(hardOneHour, 30, LimitType.HARD, now = timeOf(23, 10, 0))
        val second = edit(first, 120, LimitType.SOFT, now = timeOf(23, 18, 0))

        assertEquals(60, second.timeLimit)
        assertEquals(120, second.pendingTimeLimit)
        assertEquals(LimitType.SOFT, second.pendingLimitType)
        assertEquals(midnight, second.pendingFrom)
    }

    @Test
    fun hardLimit_editBackToTheCurrentLimit_removesThePendingChange() {
        val pending = edit(hardOneHour, 30, LimitType.HARD)

        val result = edit(pending, 60, LimitType.HARD)

        assertEquals(hardOneHour, result)
    }

    // ---------- Edits of a group without a hard limit ----------

    @Test
    fun noLimit_firstLimit_worksAtOnce() {
        val noLimit = AppGroup(id = 2, name = "Games", timeLimit = 0, limitType = LimitType.HARD)

        val result = edit(noLimit, 45, LimitType.HARD)

        assertEquals(45, result.timeLimit)
        assertEquals(LimitType.HARD, result.limitType)
        assertNull(result.pendingTimeLimit)
        assertNull(result.pendingLimitType)
        assertNull(result.pendingFrom)
    }

    @Test
    fun softLimit_edit_worksAtOnce() {
        val soft = AppGroup(id = 3, name = "Video", timeLimit = 60, limitType = LimitType.SOFT)

        val result = edit(soft, 30, LimitType.SOFT)

        assertEquals(30, result.timeLimit)
        assertNull(result.pendingTimeLimit)
        assertNull(result.pendingFrom)
    }

    @Test
    fun softLimit_switchToHard_worksAtOnce() {
        val soft = AppGroup(id = 3, name = "Video", timeLimit = 60, limitType = LimitType.SOFT)

        val result = edit(soft, 60, LimitType.HARD)

        assertEquals(LimitType.HARD, result.limitType)
        assertNull(result.pendingLimitType)
    }

    @Test
    fun isUnderHardLimit_onlyForAHardLimitGreaterThanZero() {
        assertTrue(PendingLimits.isUnderHardLimit(hardOneHour))
        assertFalse(PendingLimits.isUnderHardLimit(hardOneHour.copy(timeLimit = 0)))
        assertFalse(PendingLimits.isUnderHardLimit(hardOneHour.copy(limitType = LimitType.SOFT)))
    }

    // ---------- Applying the pending change ----------

    private val waiting = edit(hardOneHour, 30, LimitType.SOFT)

    @Test
    fun applyDue_beforeMidnight_changesNothing() {
        val result = PendingLimits.applyDue(waiting, midnight - 1)

        assertEquals(waiting, result)
    }

    @Test
    fun applyDue_atMidnight_appliesThePendingChange() {
        val result = PendingLimits.applyDue(waiting, midnight)

        assertEquals(30, result.timeLimit)
        assertEquals(LimitType.SOFT, result.limitType)
        assertNull(result.pendingTimeLimit)
        assertNull(result.pendingLimitType)
        assertNull(result.pendingFrom)
    }

    @Test
    fun applyDue_daysLater_appliesThePendingChange() {
        // The phone was off for three days (reboot recovery): the group is read from Room
        // with its pending change and the change is applied at the first start
        val result = PendingLimits.applyDue(waiting, timeOf(26, 9, 0))

        assertEquals(30, result.timeLimit)
        assertEquals(LimitType.SOFT, result.limitType)
        assertNull(result.pendingFrom)
    }

    @Test
    fun applyDue_withoutPendingChange_changesNothing() {
        val result = PendingLimits.applyDue(hardOneHour, midnight)

        assertEquals(hardOneHour, result)
    }

    @Test
    fun applyDue_appliesThePendingWeeklyLimitToo() {
        val group = hardOneHour.copy(pendingWeeklyLimit = 600, pendingFrom = midnight)

        val result = PendingLimits.applyDue(group, midnight)

        assertEquals(600, result.weeklyLimit)
        assertNull(result.pendingWeeklyLimit)
        assertNull(result.pendingFrom)
    }

    @Test
    fun editAfterMidnight_usesTheLimitThatWasPending() {
        // At midnight the change is applied. The next edit works on the new current limit,
        // which is now a soft limit, so it works at once.
        val current = PendingLimits.applyDue(waiting, midnight)

        val result = edit(current, 90, LimitType.SOFT, now = timeOf(24, 8, 0))

        assertEquals(90, result.timeLimit)
        assertNull(result.pendingTimeLimit)
    }
}
