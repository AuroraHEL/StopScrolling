package com.example.stopscrolling.usage

import com.example.stopscrolling.data.LimitType

enum class LimitAction { SHOW_HARD, SHOW_SOFT }

/** Usage of one group today, and the minutes of extension granted today. */
data class GroupLimitState(
    val group: RegulatedGroup,
    val usedMillis: Long,
    val extraMinutes: Int
)

data class BlockDecision(
    val group: RegulatedGroup,
    val action: LimitAction,
    val usedMinutes: Int
)

/** [decision] is null when nothing must be blocked. */
data class LimitCheckResult(
    val decision: BlockDecision?,
    val nextCheckInMillis: Long
)

object LimitEvaluator {

    const val FAST_CHECK_MILLIS = 1_000L
    const val SLOW_CHECK_MILLIS = 10_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val NEAR_LIMIT_MILLIS = 2 * MILLIS_PER_MINUTE

    /** Time allowed today. An extension only counts for a soft limit. */
    private fun allowedMillis(group: RegulatedGroup, extraMinutes: Int): Long {
        val extra = if (group.limitType == LimitType.SOFT) extraMinutes else 0
        return (group.timeLimitMinutes + extra) * MILLIS_PER_MINUTE
    }

    fun evaluate(states: List<GroupLimitState>): LimitCheckResult {
        var blocking: BlockDecision? = null
        var minRemaining = Long.MAX_VALUE

        for (state in states) {
            val group = state.group
            if (group.timeLimitMinutes <= 0) continue

            val remaining = allowedMillis(group, state.extraMinutes) - state.usedMillis
            if (remaining > 0) {
                minRemaining = minOf(minRemaining, remaining)
                continue
            }

            val action = if (group.limitType == LimitType.HARD) {
                LimitAction.SHOW_HARD
            } else {
                LimitAction.SHOW_SOFT
            }
            val current = blocking
            if (current == null ||
                (action == LimitAction.SHOW_HARD && current.action == LimitAction.SHOW_SOFT)
            ) {
                blocking = BlockDecision(group, action, (state.usedMillis / MILLIS_PER_MINUTE).toInt())
            }
        }

        val next = if (blocking != null || minRemaining <= NEAR_LIMIT_MILLIS) {
            FAST_CHECK_MILLIS
        } else {
            SLOW_CHECK_MILLIS
        }
        return LimitCheckResult(blocking, next)
    }
}