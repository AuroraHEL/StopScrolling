package com.example.stopscrolling.usage

import com.example.stopscrolling.data.DailyUsage
import com.example.stopscrolling.data.repository.DailyUsageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * Archives the previous finished day's total phone usage into Room (US-27).
 *
 * Designed to be called at midnight (via DateChangeReceiver), on service startup,
 * and during periodic checks; once a day is stored, subsequent calls return immediately.
 */
class DailyUsageArchiver(
    private val repository: DailyUsageRepository,
    private val hasPermission: () -> Boolean,
    private val computeRangeTotalMillis: (Long, Long) -> Long,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Archives yesterday's total usage if permission is granted and no row exists yet.
     * Returns true if a new record was saved, false if skipped.
     */
    suspend fun archiveYesterdayIfNeeded(
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!hasPermission()) return false

        val zone = timeZoneProvider()
        val todayStart = DayBoundary.startOfDayMillis(nowMillis, zone)
        val yesterdayStart = DayBoundary.startOfPreviousDayMillis(nowMillis, zone)

        if (repository.getByDayStart(yesterdayStart) != null) {
            return false
        }

        val totalMillis = withContext(ioDispatcher) {
            computeRangeTotalMillis(yesterdayStart, todayStart)
        }.coerceAtLeast(0L)

        repository.save(
            DailyUsage(
                dayStartMillis = yesterdayStart,
                totalMillis = totalMillis
            )
        )
        return true
    }
}