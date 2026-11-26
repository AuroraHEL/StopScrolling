package com.example.stopscrolling.usage

import com.example.stopscrolling.data.DailyUsage
import com.example.stopscrolling.data.repository.DailyUsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class DailyUsageArchiverTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zone = TimeZone.getTimeZone("Europe/Helsinki")

    private fun timeOf(day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(zone).apply {
            set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun archiveMissingDays_savesFinishedDayTotalAtMidnight() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        val queriedRanges = mutableListOf<Pair<Long, Long>>()
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { true },
            computeRangeTotalMillis = { start, end ->
                queriedRanges.add(start to end)
                2 * 60 * 60_000L
            },
            maxBackfillDays = 1,
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        val archivedCount = archiver.archiveMissingDaysIfNeeded(timeOf(26, 0, 1))

        val expectedStart = timeOf(25, 0, 0)
        val expectedEnd = timeOf(26, 0, 0)
        assertEquals(1, archivedCount)
        assertEquals(listOf(expectedStart to expectedEnd), queriedRanges)
        assertEquals(
            DailyUsage(dayStartMillis = expectedStart, totalMillis = 2 * 60 * 60_000L),
            fakeRepo.getByDayStart(expectedStart)
        )
    }

    @Test
    fun archiveMissingDays_backfillsMultiDayGapAndSkipsAlreadyArchivedDays() =
        runTest(testDispatcher) {
            val fakeRepo = FakeDailyUsageRepository()
            val day23Start = timeOf(23, 0, 0)
            val day24Start = timeOf(24, 0, 0)
            val day25Start = timeOf(25, 0, 0)

            // Day 23 was archived before the phone was off for 2 days (days 24 and 25).
            fakeRepo.save(DailyUsage(dayStartMillis = day23Start, totalMillis = 45 * 60_000L))

            val queriedStarts = mutableListOf<Long>()
            val totalsByDay = mapOf(
                day24Start to 20 * 60_000L,
                day25Start to 70 * 60_000L
            )
            val archiver = DailyUsageArchiver(
                repository = fakeRepo,
                hasPermission = { true },
                computeRangeTotalMillis = { start, _ ->
                    queriedStarts.add(start)
                    totalsByDay[start] ?: 0L
                },
                maxBackfillDays = 3,
                timeZoneProvider = { zone },
                ioDispatcher = testDispatcher
            )

            val archivedCount = archiver.archiveMissingDaysIfNeeded(timeOf(26, 9, 0))

            assertEquals(2, archivedCount)
            assertEquals(listOf(day25Start, day24Start), queriedStarts)
            assertEquals(45 * 60_000L, fakeRepo.getByDayStart(day23Start)?.totalMillis)
            assertEquals(20 * 60_000L, fakeRepo.getByDayStart(day24Start)?.totalMillis)
            assertEquals(70 * 60_000L, fakeRepo.getByDayStart(day25Start)?.totalMillis)
        }

    @Test
    fun archiveMissingDays_skipsWhenAllDaysInWindowAlreadyArchived() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        val day24Start = timeOf(24, 0, 0)
        val day25Start = timeOf(25, 0, 0)
        fakeRepo.save(DailyUsage(dayStartMillis = day24Start, totalMillis = 30 * 60_000L))
        fakeRepo.save(DailyUsage(dayStartMillis = day25Start, totalMillis = 45 * 60_000L))

        var queryCount = 0
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { true },
            computeRangeTotalMillis = { _, _ ->
                queryCount++
                999_000L
            },
            maxBackfillDays = 2,
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        val archivedCount = archiver.archiveMissingDaysIfNeeded(timeOf(26, 8, 30))

        assertEquals(0, archivedCount)
        assertEquals(0, queryCount)
    }

    @Test
    fun archiveMissingDays_skipsWithoutPermission() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        var queryCount = 0
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { false },
            computeRangeTotalMillis = { _, _ ->
                queryCount++
                60_000L
            },
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        val archivedCount = archiver.archiveMissingDaysIfNeeded(timeOf(26, 0, 5))

        assertEquals(0, archivedCount)
        assertEquals(0, queryCount)
        assertTrue(fakeRepo.getAll().isEmpty())
    }

    @Test
    fun archiveMissingDays_archivesNewDayAfterMidnightRollover() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        val day25Start = timeOf(25, 0, 0)
        val day26Start = timeOf(26, 0, 0)
        val totalsByDay = mapOf(
            day25Start to 30 * 60_000L,
            day26Start to 90 * 60_000L
        )
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { true },
            computeRangeTotalMillis = { start, _ -> totalsByDay[start] ?: 0L },
            maxBackfillDays = 1,
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        assertEquals(1, archiver.archiveMissingDaysIfNeeded(timeOf(26, 0, 0)))
        assertEquals(0, archiver.archiveMissingDaysIfNeeded(timeOf(26, 12, 0)))
        assertEquals(1, archiver.archiveMissingDaysIfNeeded(timeOf(27, 0, 0)))

        assertEquals(2, fakeRepo.getAll().size)
        assertEquals(30 * 60_000L, fakeRepo.getByDayStart(day25Start)?.totalMillis)
        assertEquals(90 * 60_000L, fakeRepo.getByDayStart(day26Start)?.totalMillis)
    }

    @Test
    fun archiveMissingDays_clampsNegativeUsageToZero() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { true },
            computeRangeTotalMillis = { _, _ -> -5_000L },
            maxBackfillDays = 1,
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        assertEquals(1, archiver.archiveMissingDaysIfNeeded(timeOf(26, 0, 1)))
        assertEquals(0L, fakeRepo.getByDayStart(timeOf(25, 0, 0))?.totalMillis)
    }

    private class FakeDailyUsageRepository : DailyUsageRepository {
        private val records = mutableMapOf<Long, DailyUsage>()

        override suspend fun getByDayStart(dayStartMillis: Long): DailyUsage? =
            records[dayStartMillis]

        override suspend fun save(dailyUsage: DailyUsage) {
            records[dailyUsage.dayStartMillis] = dailyUsage
        }

        override suspend fun getAll(): List<DailyUsage> =
            records.values.sortedByDescending { it.dayStartMillis }
    }
}