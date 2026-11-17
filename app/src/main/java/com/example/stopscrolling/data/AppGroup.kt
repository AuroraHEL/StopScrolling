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
    val limitType: LimitType = LimitType.HARD
)
