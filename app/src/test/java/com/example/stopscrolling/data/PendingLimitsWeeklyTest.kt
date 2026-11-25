package com.example.stopscrolling.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// US-13: a change to a weekly HARD limit waits for midnight, like a daily one (US-15)
class PendingLimitsWeeklyTest {

    private val zone = TimeZone.getTimeZone("Europe/Helsinki")

    // Builds a time in milliseconds for the test time zone (November 2026)
    private fun timeOf(day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private val afternoon = timeOf(25, 14, 0)
    private val midnight = timeOf(26, 0, 0)

    private val hardWithWeekly = AppGroup(
        id = 1, name = "Social", timeLimit = 60, limitType = LimitType.HARD, weeklyLimit = 300
    )
    private val softWithWeekly = hardWithWeekly.copy(limitType = LimitType.SOFT)
    private val hardWithoutWeekly = hardWithWeekly.copy(weeklyLimit = 0)

    private fun edit(group: AppGroup, weekly: Int, now: Long = afternoon) =
        PendingLimits.applyWeeklyEdit(group, weekly, now, zone)

    @Test
    fun isUnderWeeklyHardLimit_onlyForHardGroupWithAWeeklyLimit() {
        assertTrue(PendingLimits.isUnderWeeklyHardLimit(hardWithWeekly))
        assertFalse(PendingLimits.isUnderWeeklyHardLimit(softWithWeekly))
        assertFalse(PendingLimits.isUnderWeeklyHardLimit(hardWithoutWeekly))
    }

    @Test
    fun hardWeekly_edit_isSavedAsPending_andCurrentLimitStays() {
        val result = edit(hardWithWeekly, 600)

        assertEquals(300, result.weeklyLimit)
        assertEquals(600, result.pendingWeeklyLimit)
        assertEquals(midnight, result.pendingFrom)
    }

    @Test
    fun hardWeekly_secondEdit_replacesTheFirst() {
        val result = edit(edit(hardWithWeekly, 600), 900)

        assertEquals(300, result.weeklyLimit)
        assertEquals(900, result.pendingWeeklyLimit)
    }

    @Test
    fun hardWeekly_backToCurrentValue_cancelsThePendingChange() {
        val result = edit(edit(hardWithWeekly, 600), 300)

        assertNull(result.pendingWeeklyLimit)
        assertNull(result.pendingFrom)
    }

    @Test
    fun hardWeekly_removingTheLimit_isAlsoPostponed() {
        val result = edit(hardWithWeekly, 0)

        assertEquals(300, result.weeklyLimit)
        assertEquals(0, result.pendingWeeklyLimit)
    }

    @Test
    fun softWeekly_edit_worksAtOnce() {
        val result = edit(softWithWeekly, 120)

        assertEquals(120, result.weeklyLimit)
        assertNull(result.pendingWeeklyLimit)
        assertNull(result.pendingFrom)
    }

    @Test
    fun firstWeeklyLimitOfAHardGroup_worksAtOnce() {
        val result = edit(hardWithoutWeekly, 400)

        assertEquals(400, result.weeklyLimit)
        assertNull(result.pendingWeeklyLimit)
        assertNull(result.pendingFrom)
    }

    @Test
    fun pendingWeeklyLimit_isAppliedAtMidnight_notBefore() {
        val pending = edit(hardWithWeekly, 600)

        assertEquals(300, PendingLimits.applyDue(pending, midnight - 1).weeklyLimit)

        val applied = PendingLimits.applyDue(pending, midnight)
        assertEquals(600, applied.weeklyLimit)
        assertNull(applied.pendingWeeklyLimit)
        assertNull(applied.pendingFrom)
    }

    @Test
    fun pendingWeeklyLimitZero_removesTheWeeklyLimitAtMidnight() {
        val applied = PendingLimits.applyDue(edit(hardWithWeekly, 0), midnight)

        assertEquals(0, applied.weeklyLimit)
    }

    @Test
    fun dailyAndWeeklyPending_coexist_andCancellingOneKeepsTheOther() {
        val both = edit(PendingLimits.applyEdit(hardWithWeekly, 30, LimitType.HARD, afternoon, zone), 600)
        assertEquals(30, both.pendingTimeLimit)
        assertEquals(600, both.pendingWeeklyLimit)

        val cancelledWeekly = edit(both, 300)
        assertNull(cancelledWeekly.pendingWeeklyLimit)
        assertEquals(30, cancelledWeekly.pendingTimeLimit)
        assertEquals(midnight, cancelledWeekly.pendingFrom) // still needed by the daily change
    }

    // ----- Group with a hard WEEKLY limit and no daily limit (found in the review of the PR) -----

    private val hardWeeklyOnly = hardWithWeekly.copy(timeLimit = 0)

    @Test
    fun isUnderHardLimit_alsoTrueForAHardGroupWithOnlyAWeeklyLimit() {
        assertTrue(PendingLimits.isUnderHardLimit(hardWeeklyOnly))
        // No daily and no weekly limit: nothing to protect
        assertFalse(PendingLimits.isUnderHardLimit(hardWeeklyOnly.copy(weeklyLimit = 0)))
        // A soft group is never under a hard limit
        assertFalse(PendingLimits.isUnderHardLimit(hardWeeklyOnly.copy(limitType = LimitType.SOFT)))
    }

    @Test
    fun weeklyOnlyHardGroup_changingToSoft_waitsForMidnight() {
        val result = PendingLimits.applyEdit(hardWeeklyOnly, 0, LimitType.SOFT, afternoon, zone)

        // The lock screen stays: the group is still hard until midnight
        assertEquals(LimitType.HARD, result.limitType)
        assertEquals(LimitType.SOFT, result.pendingLimitType)
        assertEquals(midnight, result.pendingFrom)
    }

    @Test
    fun weeklyOnlyHardGroup_changeToSoft_isAppliedAtMidnight() {
        val pending = PendingLimits.applyEdit(hardWeeklyOnly, 0, LimitType.SOFT, afternoon, zone)

        assertEquals(LimitType.HARD, PendingLimits.applyDue(pending, midnight - 1).limitType)
        assertEquals(LimitType.SOFT, PendingLimits.applyDue(pending, midnight).limitType)
    }

    @Test
    fun weeklyOnlyHardGroup_sameValues_isNotAChange() {
        val result = PendingLimits.applyEdit(hardWeeklyOnly, 0, LimitType.HARD, afternoon, zone)

        assertNull(result.pendingLimitType)
        assertNull(result.pendingTimeLimit)
        assertNull(result.pendingFrom)
    }

    @Test
    fun weeklyOnlyHardGroup_typeAndWeeklyEditedTogether_bothWaitForMidnight() {
        // What the Edit Limit dialog does: the weekly edit first, then the daily edit
        val weeklyEdited = PendingLimits.applyWeeklyEdit(hardWeeklyOnly, 600, afternoon, zone)
        val result = PendingLimits.applyEdit(weeklyEdited, 0, LimitType.SOFT, afternoon, zone)

        assertEquals(LimitType.HARD, result.limitType)
        assertEquals(300, result.weeklyLimit)
        assertEquals(LimitType.SOFT, result.pendingLimitType)
        assertEquals(600, result.pendingWeeklyLimit)
        assertEquals(midnight, result.pendingFrom)
    }

    @Test
    fun softWeeklyOnlyGroup_changingToHard_worksAtOnce() {
        val soft = hardWeeklyOnly.copy(limitType = LimitType.SOFT)

        val result = PendingLimits.applyEdit(soft, 0, LimitType.HARD, afternoon, zone)

        assertEquals(LimitType.HARD, result.limitType)
        assertNull(result.pendingLimitType)
    }
}
