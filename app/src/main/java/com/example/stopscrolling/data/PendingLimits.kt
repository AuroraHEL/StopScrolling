package com.example.stopscrolling.data

import com.example.stopscrolling.usage.DayBoundary
import java.util.TimeZone

/*
 * Rules for the pending limit changes (US-15).
 *
 * A hard limit protects the user from impulsive decisions, so a change to it does not
 * work at once. The change waits in the database and the current limit stays active
 * until 00:00 of the next day.
 *
 * These functions only work on [AppGroup] objects and never touch the database,
 * so they are easy to test.
 */
object PendingLimits {

    /* True if the group has an active hard limit (a limit of 0 means no limit). */
    fun isUnderHardLimit(group: AppGroup): Boolean {
        return group.limitType == LimitType.HARD && group.timeLimit > 0
    }

    /* True if the group has a limit change that is waiting for midnight. */
    fun hasPendingLimit(group: AppGroup): Boolean {
        return group.pendingTimeLimit != null && group.pendingLimitType != null
    }

    /*
     * Returns the group after the user edited its daily limit.
     * - Group with a hard limit: the edit is saved as pending and the current limit stays.
     *   A second edit replaces the first pending one, so only the latest is kept.
     * - Any other group (first limit, soft limit, no limit): the edit works at once.
     */
    fun applyEdit(
        group: AppGroup,
        newTimeLimit: Int,
        newLimitType: LimitType,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AppGroup {
        if (!isUnderHardLimit(group)) {
            val changed = group.copy(
                timeLimit = newTimeLimit,
                limitType = newLimitType,
                pendingTimeLimit = null,
                pendingLimitType = null
            )
            return clearPendingFromIfUnused(changed)
        }

        // The user went back to the current limit: there is nothing to wait for
        if (newTimeLimit == group.timeLimit && newLimitType == group.limitType) {
            val unchanged = group.copy(
                pendingTimeLimit = null,
                pendingLimitType = null
            )
            return clearPendingFromIfUnused(unchanged)
        }

        return group.copy(
            pendingTimeLimit = newTimeLimit,
            pendingLimitType = newLimitType,
            pendingFrom = DayBoundary.startOfNextDayMillis(nowMillis, timeZone)
        )
    }

    /* True if the pending changes of the group must start now. */
    fun isDue(group: AppGroup, nowMillis: Long): Boolean {
        val from = group.pendingFrom ?: return false
        return nowMillis >= from
    }

    /*
     * Returns the group with its pending limit changes applied, if they are due.
     * If they are not due yet, the group is returned as it is.
     * It also works days later: if the phone was off at midnight, the change is applied
     * the next time this function runs.
     */
    fun applyDue(
        group: AppGroup,
        nowMillis: Long = System.currentTimeMillis()
    ): AppGroup {
        if (!isDue(group, nowMillis)) return group

        val applied = group.copy(
            timeLimit = group.pendingTimeLimit ?: group.timeLimit,
            limitType = group.pendingLimitType ?: group.limitType,
            // US-13: the weekly limit waits for the same moment (0 removes the weekly limit)
            weeklyLimit = group.pendingWeeklyLimit ?: group.weeklyLimit,
            pendingTimeLimit = null,
            pendingLimitType = null,
            pendingWeeklyLimit = null
        )
        return clearPendingFromIfUnused(applied)
    }

    /*
     * pendingFrom is shared by the pending limit and the pending weekly limit.
     * We clear it only when nothing else is waiting for it.
     */
    private fun clearPendingFromIfUnused(group: AppGroup): AppGroup {
        val somethingWaiting = group.pendingTimeLimit != null ||
            group.pendingWeeklyLimit != null
        return if (somethingWaiting) group else group.copy(pendingFrom = null)
    }

    // ----- US-13: weekly limit -----

    /* True if the group has an active weekly HARD limit (a weekly limit of 0 means none). */
    fun isUnderWeeklyHardLimit(group: AppGroup): Boolean {
        return group.limitType == LimitType.HARD && group.weeklyLimit > 0
    }

    /*
     * Returns the group after the user edited its weekly limit (0 = remove the weekly limit).
     * Same rule as [applyEdit] for the daily limit:
     * - Group with a weekly hard limit: the edit is saved as pending until 00:00 of the next day.
     *   A second edit replaces the first one.
     * - Any other group (first weekly limit, soft limit): the edit works at once.
     */
    fun applyWeeklyEdit(
        group: AppGroup,
        newWeeklyLimit: Int,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AppGroup {
        if (!isUnderWeeklyHardLimit(group)) {
            val changed = group.copy(
                weeklyLimit = newWeeklyLimit,
                pendingWeeklyLimit = null
            )
            return clearPendingFromIfUnused(changed)
        }

        // The user went back to the current weekly limit: there is nothing to wait for
        if (newWeeklyLimit == group.weeklyLimit) {
            return clearPendingFromIfUnused(group.copy(pendingWeeklyLimit = null))
        }

        return group.copy(
            pendingWeeklyLimit = newWeeklyLimit,
            pendingFrom = DayBoundary.startOfNextDayMillis(nowMillis, timeZone)
        )
    }
}
