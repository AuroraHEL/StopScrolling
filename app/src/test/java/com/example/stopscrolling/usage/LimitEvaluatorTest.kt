package com.example.stopscrolling.usage

import com.example.stopscrolling.data.LimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LimitEvaluatorTest {

    private fun group(id: Int, limitMinutes: Int, type: LimitType) = RegulatedGroup(
        groupId = id,
        packageNames = listOf("app.a"),
        name = "Group $id",
        timeLimitMinutes = limitMinutes,
        limitType = type
    )

    private fun state(group: RegulatedGroup, usedMinutes: Int, extraMinutes: Int = 0) =
        GroupLimitState(group, usedMinutes * 60_000L, extraMinutes)

    @Test
    fun limitZero_neverBlocks() {
        val result = LimitEvaluator.evaluate(listOf(state(group(1, 0, LimitType.HARD), 500)))
        assertNull(result.decision)
    }

    @Test
    fun underTheLimit_doesNotBlock() {
        val result = LimitEvaluator.evaluate(listOf(state(group(1, 60, LimitType.HARD), 59)))
        assertNull(result.decision)
    }

    @Test
    fun hardLimitReached_showsHard() {
        val result = LimitEvaluator.evaluate(listOf(state(group(1, 60, LimitType.HARD), 60)))
        assertEquals(LimitAction.SHOW_HARD, result.decision?.action)
        assertEquals(60, result.decision?.usedMinutes)
    }

    @Test
    fun softLimitReached_showsSoft() {
        val result = LimitEvaluator.evaluate(listOf(state(group(1, 60, LimitType.SOFT), 61)))
        assertEquals(LimitAction.SHOW_SOFT, result.decision?.action)
    }

    @Test
    fun softLimit_extensionAddsTime() {
        val soft = group(1, 60, LimitType.SOFT)
        assertNull(LimitEvaluator.evaluate(listOf(state(soft, 70, extraMinutes = 15))).decision)
        val over = LimitEvaluator.evaluate(listOf(state(soft, 75, extraMinutes = 15)))
        assertEquals(LimitAction.SHOW_SOFT, over.decision?.action)
    }

    @Test
    fun hardLimit_ignoresExtension() {
        val hard = group(1, 60, LimitType.HARD)
        val result = LimitEvaluator.evaluate(listOf(state(hard, 70, extraMinutes = 15)))
        assertEquals(LimitAction.SHOW_HARD, result.decision?.action)
    }

    @Test
    fun appInTwoGroups_hardWinsOverSoft() {
        val result = LimitEvaluator.evaluate(
            listOf(
                state(group(1, 60, LimitType.SOFT), 90),
                state(group(2, 60, LimitType.HARD), 90)
            )
        )
        assertNotNull(result.decision)
        assertEquals(2, result.decision?.group?.groupId)
        assertEquals(LimitAction.SHOW_HARD, result.decision?.action)
    }

    @Test
    fun nearTheLimit_checksEverySecond() {
        val result = LimitEvaluator.evaluate(listOf(state(group(1, 60, LimitType.HARD), 59)))
        assertEquals(LimitEvaluator.FAST_CHECK_MILLIS, result.nextCheckInMillis)
    }

    @Test
    fun farFromTheLimit_checksSlowly() {
        val result = LimitEvaluator.evaluate(listOf(state(group(1, 60, LimitType.HARD), 10)))
        assertEquals(LimitEvaluator.SLOW_CHECK_MILLIS, result.nextCheckInMillis)
    }

    @Test
    fun whileBlocked_checksEverySecond() {
        val result = LimitEvaluator.evaluate(listOf(state(group(1, 60, LimitType.SOFT), 90)))
        assertEquals(LimitEvaluator.FAST_CHECK_MILLIS, result.nextCheckInMillis)
    }
}