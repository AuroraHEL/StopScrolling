package com.example.stopscrolling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyUsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dailyUsage: DailyUsage)

    @Query("SELECT * FROM daily_usage WHERE dayStartMillis = :dayStartMillis LIMIT 1")
    suspend fun getByDayStart(dayStartMillis: Long): DailyUsage?

    @Query("SELECT * FROM daily_usage ORDER BY dayStartMillis DESC")
    suspend fun getAll(): List<DailyUsage>
}