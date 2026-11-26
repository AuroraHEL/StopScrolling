package com.example.stopscrolling.usage

import com.example.stopscrolling.data.DailyUsage
import com.example.stopscrolling.data.repository.DailyUsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun archiveYesterdayIfNeeded_savesFinishedDayTotalAtMidnight() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        var queriedRange: Pair<Long, Long>? = null
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { true },
            computeRangeTotalMillis = { start, end ->
                queriedRange = start to end
                2 * 60 * 60_000L
            },
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        val rightAfterMidnight = timeOf(26, 0, 1)
        val archived = archiver.archiveYesterdayIfNeeded(rightAfterMidnight)

        val expectedStart = timeOf(25, 0, 0)
        val expectedEnd = timeOf(26, 0, 0)
        assertTrue(archived)
        assertEquals(expectedStart to expectedEnd, queriedRange)
        assertEquals(
            DailyUsage(dayStartMillis = expectedStart, totalMillis = 2 * 60 * 60_000L),
            fakeRepo.getByDayStart(expectedStart)
        )
    }

    @Test
    fun archiveYesterdayIfNeeded_skipsWhenAlreadyArchived() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        val yesterdayStart = timeOf(25, 0, 0)
        fakeRepo.save(DailyUsage(dayStartMillis = yesterdayStart, totalMillis = 45 * 60_000L))

        var queryCount = 0
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { true },
            computeRangeTotalMillis = { _, _ ->
                queryCount++
                999_000L
            },
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        val archived = archiver.archiveYesterdayIfNeeded(timeOf(26, 8, 30))

        assertFalse(archived)
        assertEquals(0, queryCount)
        assertEquals(45 * 60_000L, fakeRepo.getByDayStart(yesterdayStart)?.totalMillis)
    }

    @Test
    fun archiveYesterdayIfNeeded_skipsWithoutPermission() = runTest(testDispatcher) {
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

        val archived = archiver.archiveYesterdayIfNeeded(timeOf(26, 0, 5))

        assertFalse(archived)
        assertEquals(0, queryCount)
        assertTrue(fakeRepo.getAll().isEmpty())
    }

    @Test
    fun archiveYesterdayIfNeeded_archivesEachNewDayAfterMidnightRollover() = runTest(testDispatcher) {
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
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        assertTrue(archiver.archiveYesterdayIfNeeded(timeOf(26, 0, 0)))
        assertFalse(archiver.archiveYesterdayIfNeeded(timeOf(26, 12, 0)))
        assertTrue(archiver.archiveYesterdayIfNeeded(timeOf(27, 0, 0)))

        assertEquals(2, fakeRepo.getAll().size)
        assertEquals(30 * 60_000L, fakeRepo.getByDayStart(day25Start)?.totalMillis)
        assertEquals(90 * 60_000L, fakeRepo.getByDayStart(day26Start)?.totalMillis)
    }

    @Test
    fun archiveYesterdayIfNeeded_clampsNegativeUsageToZero() = runTest(testDispatcher) {
        val fakeRepo = FakeDailyUsageRepository()
        val archiver = DailyUsageArchiver(
            repository = fakeRepo,
            hasPermission = { true },
            computeRangeTotalMillis = { _, _ -> -5_000L },
            timeZoneProvider = { zone },
            ioDispatcher = testDispatcher
        )

        assertTrue(archiver.archiveYesterdayIfNeeded(timeOf(26, 0, 1)))
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