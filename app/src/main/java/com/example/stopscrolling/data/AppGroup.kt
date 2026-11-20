package com.example.stopscrolling.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_groups")
data class AppGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    // Time limit in minutes.
    // 0 means no limit is set.
    val timeLimit: Int = 0,
    val limitType: LimitType = LimitType.HARD,

    // US-13: weekly time limit in minutes.
    // 0 means no weekly limit is set.
    val weeklyLimit: Int = 0,

    // US-15: changes to a hard limit wait here until [pendingFrom].
    // null means there is no pending change.
    val pendingTimeLimit: Int? = null,
    val pendingLimitType: LimitType? = null,

    // US-13: pending weekly limit in minutes.
    // null means there is no pending change, 0 means the weekly limit will be removed.
    val pendingWeeklyLimit: Int? = null,

    // Moment (milliseconds since the epoch) when the pending changes take effect.
    // It is 00:00 of the next day. null means there is no pending change.
    val pendingFrom: Long? = null,

    // US-09: true means the group will be deleted at [pendingFrom]
    val pendingDeletion: Boolean = false
)
