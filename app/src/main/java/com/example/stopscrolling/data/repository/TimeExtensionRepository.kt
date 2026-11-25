package com.example.stopscrolling.data.repository

import com.example.stopscrolling.data.TimeExtension
import com.example.stopscrolling.data.TimeExtensionDao
import com.example.stopscrolling.ui.overlay.EXTENSION_MINUTES
import com.example.stopscrolling.usage.DayBoundary
import com.example.stopscrolling.usage.ExtensionLookup
import com.example.stopscrolling.usage.WeekBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

interface TimeExtensionRepository : ExtensionLookup {
    suspend fun grantExtension(
        groupId: Int,
        minutes: Int = EXTENSION_MINUTES,
        nowMillis: Long = System.currentTimeMillis()
    )
}

/**
 * Persists daily soft-limit extensions in Room and exposes today's total
 * via [ExtensionLookup], resetting automatically at local midnight.
 */
class RoomTimeExtensionRepository(
    private val dao: TimeExtensionDao,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() }
) : TimeExtensionRepository {

    override suspend fun grantExtension(
        groupId: Int,
        minutes: Int,
        nowMillis: Long
    ) = withContext(Dispatchers.IO) {
        require(minutes > 0) { "Extension minutes must be positive" }
        val dayStart = DayBoundary.startOfDayMillis(nowMillis, timeZoneProvider())
        dao.insertExtension(
            TimeExtension(
                groupId = groupId,
                dayStartMillis = dayStart,
                grantedMinutes = minutes
            )
        )
        Unit
    }

    override suspend fun extraMinutesToday(groupId: Int): Int =
        extraMinutesOnDay(groupId, System.currentTimeMillis())

    suspend fun extraMinutesOnDay(groupId: Int, nowMillis: Long): Int =
        withContext(Dispatchers.IO) {
            val dayStart = DayBoundary.startOfDayMillis(nowMillis, timeZoneProvider())
            dao.getTotalExtensionMinutesForDay(
                groupId = groupId,
                dayStartMillis = dayStart
            )
        }

    // US-13: an extension also adds time to the weekly limit of a soft limit
    override suspend fun extraMinutesThisWeek(groupId: Int): Int =
        extraMinutesInWeek(groupId, System.currentTimeMillis())

    suspend fun extraMinutesInWeek(groupId: Int, nowMillis: Long): Int =
        withContext(Dispatchers.IO) {
            val weekStart = WeekBoundary.startOfWeekMillis(nowMillis, timeZoneProvider())
            dao.getTotalExtensionMinutesSince(
                groupId = groupId,
                weekStartMillis = weekStart
            )
        }
}
