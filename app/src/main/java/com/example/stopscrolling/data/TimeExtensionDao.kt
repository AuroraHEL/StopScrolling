package com.example.stopscrolling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TimeExtensionDao {

    @Insert
    suspend fun insertExtension(extension: TimeExtension): Long

    @Query(
        """
        SELECT COALESCE(SUM(grantedMinutes), 0)
        FROM time_extensions
        WHERE groupId = :groupId AND dayStartMillis = :dayStartMillis
        """
    )
    suspend fun getTotalExtensionMinutesForDay(
        groupId: Int,
        dayStartMillis: Long
    ): Int

    // US-13: all the extensions since the start of the week (dayStartMillis >= weekStart)
    @Query(
        """
        SELECT COALESCE(SUM(grantedMinutes), 0)
        FROM time_extensions
        WHERE groupId = :groupId AND dayStartMillis >= :weekStartMillis
        """
    )
    suspend fun getTotalExtensionMinutesSince(
        groupId: Int,
        weekStartMillis: Long
    ): Int
}
