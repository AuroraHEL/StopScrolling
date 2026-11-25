package com.example.stopscrolling.usage

import com.example.stopscrolling.data.LimitType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardLimitWarningEvaluatorTest {

    private fun createGroup(
        limitMinutes: Int,
        limitType: LimitType = LimitType.HARD
    ) = RegulatedGroup(
        groupId = 1,
        packageNames = listOf("com.example.test"),
        name = "Test Group",
        timeLimitMinutes = limitMinutes,
        limitType = limitType
    )

    @Test
    fun `warns immediately when hard limit is 5 minutes`() {
        val group = createGroup(limitMinutes = 5)

        assertTrue(
            HardLimitWarningEvaluator.shouldWarn(
                group,
                usedMillis = 0L
            )
        )
    }

    @Test
    fun `warns immediately when hard limit is less than 5 minutes`() {
        val group = createGroup(limitMinutes = 3)

        assertTrue(
            HardLimitWarningEvaluator.shouldWarn(
                group,
                usedMillis = 0L
            )
        )
    }

    @Test
    fun `does not warn when more than 5 minutes remain`() {
        val group = createGroup(limitMinutes = 30)

        assertFalse(
            HardLimitWarningEvaluator.shouldWarn(
                group,
                usedMillis = 20 * 60_000L
            )
        )
    }

    @Test
    fun `warns when exactly 5 minutes remain`() {
        val group = createGroup(limitMinutes = 30)

        assertTrue(
            HardLimitWarningEvaluator.shouldWarn(
                group,
                usedMillis = 25 * 60_000L
            )
        )
    }

    @Test
    fun `does not warn after hard limit is reached`() {
        val group = createGroup(limitMinutes = 30)

        assertFalse(
            HardLimitWarningEvaluator.shouldWarn(
                group,
                usedMillis = 30 * 60_000L
            )
        )
    }

    @Test
    fun `does not warn for soft limits`() {
        val group = createGroup(
            limitMinutes = 30,
            limitType = LimitType.SOFT
        )

        assertFalse(
            HardLimitWarningEvaluator.shouldWarn(
                group,
                usedMillis = 25 * 60_000L
            )
        )
    }

    @Test
    fun `does not warn when no limit is set`() {
        val group = createGroup(limitMinutes = 0)

        assertFalse(
            HardLimitWarningEvaluator.shouldWarn(
                group,
                usedMillis = 0L
            )
        )
    }

    @Test
    fun `hard limit warning is not affected by extensions`() {
        val group = createGroup(limitMinutes = 30)

        val state = GroupLimitState(
            group = group,
            usedMillis = 25 * 60_000L,
            extraMinutes = 15
        )

        // Hard limits must ignore extensions.
        assertTrue(
            HardLimitWarningEvaluator.shouldWarn(
                state.group,
                state.usedMillis
            )
        )

        // The group must still be blocked at its original limit.
        val reachedLimit = state.copy(
            usedMillis = 30 * 60_000L
        )

        val result = LimitEvaluator.evaluate(listOf(reachedLimit))

        assertTrue(result.decision?.action == LimitAction.SHOW_HARD)
    }

    @Test
    fun `soft limit extension delays blocking`() {
        val group = createGroup(
            limitMinutes = 30,
            limitType = LimitType.SOFT
        )

        val state = GroupLimitState(
            group = group,
            usedMillis = 30 * 60_000L,
            extraMinutes = 15
        )

        // A soft limit with a 15-minute extension
        // should not be blocked at 30 minutes.
        val result = LimitEvaluator.evaluate(listOf(state))

        assertTrue(result.decision == null)

        // Soft limits must never trigger hard-limit warnings.
        assertFalse(
            HardLimitWarningEvaluator.shouldWarn(
                state.group,
                state.usedMillis
            )
        )
    }

}
