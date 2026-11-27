package com.example.stopscrolling.usage

import com.example.stopscrolling.data.LimitKind
import com.example.stopscrolling.data.LimitType

enum class LimitAction { SHOW_HARD, SHOW_SOFT }

/**
 * Usage of one group, and the minutes of extension granted.
 * The weekly values (US-13) are not used when the group has no weekly limit.
 */
data class GroupLimitState(
    val group: RegulatedGroup,
    val usedMillis: Long,
    val extraMinutes: Int,
    // US-13: usage since Monday 00:00 and extensions granted since Monday 00:00
    val weeklyUsedMillis: Long = 0L,
    val weeklyExtraMinutes: Int = 0
)

/**
 * What must be blocked. [limitKind] tells which limit was reached, so that the overlay can say it.
 * [usedMinutes] and [limitMinutes] are the values of that limit (today or this week).
 */
data class BlockDecision(
    val group: RegulatedGroup,
    val action: LimitAction,
    val usedMinutes: Int,
    val limitKind: LimitKind = LimitKind.DAILY,
    val limitMinutes: Int = group.timeLimitMinutes
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

    // One limit (daily or weekly) of one group, with what was used for it
    private class LimitCheck(
        val kind: LimitKind,
        val limitMinutes: Int,
        val usedMillis: Long,
        val extraMinutes: Int
    )

    /** Time allowed. An extension only counts for a soft limit. */
    private fun allowedMillis(limitMinutes: Int, limitType: LimitType, extraMinutes: Int): Long {
        val extra = if (limitType == LimitType.SOFT) extraMinutes else 0
        return (limitMinutes + extra) * MILLIS_PER_MINUTE
    }

    fun evaluate(states: List<GroupLimitState>): LimitCheckResult {
        var blocking: BlockDecision? = null
        var minRemaining = Long.MAX_VALUE

        for (state in states) {
            val group = state.group
            val checks = listOf(
                LimitCheck(LimitKind.DAILY, group.timeLimitMinutes, state.usedMillis, state.extraMinutes),
                LimitCheck(LimitKind.WEEKLY, group.weeklyLimitMinutes, state.weeklyUsedMillis, state.weeklyExtraMinutes)
            )

            for (check in checks) {
                if (check.limitMinutes <= 0) continue

                val remaining = allowedMillis(check.limitMinutes, group.limitType, check.extraMinutes) -
                    check.usedMillis
                if (remaining > 0) {
                    minRemaining = minOf(minRemaining, remaining)
                    continue
                }

                val action = if (group.limitType == LimitType.HARD) {
                    LimitAction.SHOW_HARD
                } else {
                    LimitAction.SHOW_SOFT
                }
                val candidate = BlockDecision(
                    group = group,
                    action = action,
                    usedMinutes = (check.usedMillis / MILLIS_PER_MINUTE).toInt(),
                    limitKind = check.kind,
                    limitMinutes = check.limitMinutes
                )
                if (isBetter(candidate, blocking)) blocking = candidate
            }
        }

        val next = if (blocking != null || minRemaining <= NEAR_LIMIT_MILLIS) {
            FAST_CHECK_MILLIS
        } else {
            SLOW_CHECK_MILLIS
        }
        return LimitCheckResult(blocking, next)
    }

    /*
     * Which block do we show when there are several?
     * 1. A hard block wins over a soft block (as before).
     * 2. For the same group, the weekly limit wins over the daily one: the block lasts
     *    until next Monday, so the message must tell the longest block.
     * 3. Otherwise the first one stays.
     */
    private fun isBetter(candidate: BlockDecision, current: BlockDecision?): Boolean {
        if (current == null) return true
        if (candidate.action == LimitAction.SHOW_HARD && current.action == LimitAction.SHOW_SOFT) return true
        return candidate.group.groupId == current.group.groupId &&
            candidate.action == current.action &&
            candidate.limitKind == LimitKind.WEEKLY &&
            current.limitKind == LimitKind.DAILY
    }
}
