package com.example.stopscrolling.usage

import com.example.stopscrolling.data.LimitKind
import com.example.stopscrolling.data.LimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// US-13: weekly limits in the limit evaluator
class LimitEvaluatorWeeklyTest {

    private fun group(
        id: Int = 1,
        dailyMinutes: Int,
        weeklyMinutes: Int,
        type: LimitType
    ) = RegulatedGroup(
        groupId = id,
        packageNames = listOf("app.a"),
        name = "Group $id",
        timeLimitMinutes = dailyMinutes,
        limitType = type,
        weeklyLimitMinutes = weeklyMinutes
    )

    private fun state(
        group: RegulatedGroup,
        dailyUsedMinutes: Int,
        weeklyUsedMinutes: Int,
        dailyExtra: Int = 0,
        weeklyExtra: Int = 0
    ) = GroupLimitState(
        group = group,
        usedMillis = dailyUsedMinutes * 60_000L,
        extraMinutes = dailyExtra,
        weeklyUsedMillis = weeklyUsedMinutes * 60_000L,
        weeklyExtraMinutes = weeklyExtra
    )

    @Test
    fun weeklyZero_meansNoWeeklyLimit() {
        val g = group(dailyMinutes = 60, weeklyMinutes = 0, type = LimitType.HARD)
        assertNull(LimitEvaluator.evaluate(listOf(state(g, 10, 5_000))).decision)
    }

    @Test
    fun weeklyLimitReached_dailyOk_showsHardForTheWeek() {
        val g = group(dailyMinutes = 120, weeklyMinutes = 300, type = LimitType.HARD)

        val decision = LimitEvaluator.evaluate(listOf(state(g, 30, 300))).decision

        assertNotNull(decision)
        assertEquals(LimitAction.SHOW_HARD, decision?.action)
        assertEquals(LimitKind.WEEKLY, decision?.limitKind)
        assertEquals(300, decision?.limitMinutes)
        assertEquals(300, decision?.usedMinutes)
    }

    @Test
    fun weeklyLimitReached_soft_showsSoftForTheWeek() {
        val g = group(dailyMinutes = 120, weeklyMinutes = 300, type = LimitType.SOFT)

        val decision = LimitEvaluator.evaluate(listOf(state(g, 30, 310))).decision

        assertEquals(LimitAction.SHOW_SOFT, decision?.action)
        assertEquals(LimitKind.WEEKLY, decision?.limitKind)
    }

    @Test
    fun underBothLimits_doesNotBlock() {
        val g = group(dailyMinutes = 120, weeklyMinutes = 300, type = LimitType.HARD)
        assertNull(LimitEvaluator.evaluate(listOf(state(g, 119, 299))).decision)
    }

    @Test
    fun dailyReached_weeklyOk_stillShowsTheDailyBlock() {
        val g = group(dailyMinutes = 60, weeklyMinutes = 600, type = LimitType.HARD)

        val decision = LimitEvaluator.evaluate(listOf(state(g, 60, 100))).decision

        assertEquals(LimitKind.DAILY, decision?.limitKind)
        assertEquals(60, decision?.limitMinutes)
    }

    @Test
    fun bothReached_theWeeklyBlockWins() {
        val g = group(dailyMinutes = 60, weeklyMinutes = 300, type = LimitType.HARD)

        val decision = LimitEvaluator.evaluate(listOf(state(g, 70, 320))).decision

        assertEquals(LimitKind.WEEKLY, decision?.limitKind)
        assertEquals(300, decision?.limitMinutes)
    }

    @Test
    fun softWeekly_extensionAddsTimeToTheWeek() {
        val g = group(dailyMinutes = 0, weeklyMinutes = 300, type = LimitType.SOFT)

        // 310 min used, +15 granted: allowed 315, so not blocked yet
        assertNull(LimitEvaluator.evaluate(listOf(state(g, 0, 310, weeklyExtra = 15))).decision)
        // 316 min used: blocked again
        val over = LimitEvaluator.evaluate(listOf(state(g, 0, 316, weeklyExtra = 15))).decision
        assertEquals(LimitKind.WEEKLY, over?.limitKind)
    }

    @Test
    fun hardWeekly_ignoresExtension() {
        val g = group(dailyMinutes = 0, weeklyMinutes = 300, type = LimitType.HARD)
        val decision = LimitEvaluator.evaluate(listOf(state(g, 0, 310, weeklyExtra = 15))).decision
        assertEquals(LimitAction.SHOW_HARD, decision?.action)
    }

    @Test
    fun twoGroups_hardWeeklyWinsOverSoftDaily() {
        val soft = group(id = 1, dailyMinutes = 60, weeklyMinutes = 0, type = LimitType.SOFT)
        val hard = group(id = 2, dailyMinutes = 0, weeklyMinutes = 300, type = LimitType.HARD)

        val decision = LimitEvaluator.evaluate(
            listOf(state(soft, 90, 90), state(hard, 0, 300))
        ).decision

        assertEquals(2, decision?.group?.groupId)
        assertEquals(LimitKind.WEEKLY, decision?.limitKind)
    }

    @Test
    fun nearTheWeeklyLimit_checksEverySecond() {
        val g = group(dailyMinutes = 0, weeklyMinutes = 300, type = LimitType.HARD)
        val result = LimitEvaluator.evaluate(listOf(state(g, 0, 299)))
        assertEquals(LimitEvaluator.FAST_CHECK_MILLIS, result.nextCheckInMillis)
    }

    @Test
    fun farFromTheWeeklyLimit_checksSlowly() {
        val g = group(dailyMinutes = 0, weeklyMinutes = 300, type = LimitType.HARD)
        val result = LimitEvaluator.evaluate(listOf(state(g, 0, 10)))
        assertEquals(LimitEvaluator.SLOW_CHECK_MILLIS, result.nextCheckInMillis)
    }
}
