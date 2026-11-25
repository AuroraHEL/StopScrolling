package com.example.stopscrolling.usage

import com.example.stopscrolling.data.LimitType

/**
 * Checks whether a hard-limit group is within its
 * five-minute warning window.
 *
 * Notification deduplication is handled separately.
 */
object HardLimitWarningEvaluator {

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val WARNING_WINDOW_MILLIS = 5 * MILLIS_PER_MINUTE

    fun shouldWarn(
        group: RegulatedGroup,
        usedMillis: Long
    ): Boolean {
        if (group.limitType != LimitType.HARD) return false
        if (group.timeLimitMinutes <= 0) return false

        val limitMillis =
            group.timeLimitMinutes.toLong() * MILLIS_PER_MINUTE

        val remainingMillis = limitMillis - usedMillis

        return remainingMillis > 0 &&
                remainingMillis <= WARNING_WINDOW_MILLIS
    }
}
