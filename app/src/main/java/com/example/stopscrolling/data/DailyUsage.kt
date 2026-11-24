package com.example.stopscrolling.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// US-27: the total phone usage of one finished day.
// It is saved once at midnight, so that Sprint 4 can draw weekly and monthly charts (US-29).
@Entity(tableName = "daily_usage")
data class DailyUsage(
    // Start of the day (00:00 local time) in milliseconds since the epoch.
    // There is only one row for each day.
    @PrimaryKey
    val dayStartMillis: Long,
    // Foreground time of all apps in that day, in milliseconds
    val totalMillis: Long
)
