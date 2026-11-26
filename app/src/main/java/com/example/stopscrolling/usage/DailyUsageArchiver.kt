package com.example.stopscrolling.usage

import com.example.stopscrolling.data.DailyUsage
import com.example.stopscrolling.data.repository.DailyUsageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * Archives finished days' total phone usage into Room (US-27).
 *
 * Checks up to [maxBackfillDays] previous days (default 7, matching Android's UsageEvents
 * retention window) so multi-day downtime gaps are backfilled once the phone turns back on.
 * Already-archived days are skipped without re-querying UsageStatsManager.
 */
class DailyUsageArchiver(
    private val repository: DailyUsageRepository,
    private val hasPermission: () -> Boolean,
    private val computeRangeTotalMillis: (Long, Long) -> Long,
    private val maxBackfillDays: Int = DEFAULT_BACKFILL_DAYS,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Archives any unarchived finished days within the last [maxBackfillDays] days.
     * Returns the number of newly saved day records.
     */
    suspend fun archiveMissingDaysIfNeeded(
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        if (!hasPermission() || maxBackfillDays <= 0) return 0

        val zone = timeZoneProvider()
        var rangeEnd = DayBoundary.startOfDayMillis(nowMillis, zone)
        var archivedCount = 0

        repeat(maxBackfillDays) {
            val rangeStart = DayBoundary.startOfPreviousDayMillis(rangeEnd, zone)
            if (repository.getByDayStart(rangeStart) == null) {
                val totalMillis = withContext(ioDispatcher) {
                    computeRangeTotalMillis(rangeStart, rangeEnd)
                }.coerceAtLeast(0L)

                repository.save(
                    DailyUsage(
                        dayStartMillis = rangeStart,
                        totalMillis = totalMillis
                    )
                )
                archivedCount++
            }
            rangeEnd = rangeStart
        }

        return archivedCount
    }

    companion object {
        const val DEFAULT_BACKFILL_DAYS = 7
    }
}