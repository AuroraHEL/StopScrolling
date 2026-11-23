package com.example.stopscrolling.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyUsageAggregatorTest {

    private val excluded = setOf(
        "com.example.stopscrolling",
        "com.google.android.apps.nexuslauncher"
    )

    @Test
    fun computeTotalUsageMillis_sumsAllNonExcludedApps() {
        val usage = mapOf(
            "com.instagram.android" to 20 * 60_000L,
            "com.google.android.youtube" to 45 * 60_000L
        )

        val total = DailyUsageAggregator.computeTotalUsageMillis(usage, excluded)

        assertEquals(65 * 60_000L, total)
    }

    @Test
    fun computeTotalUsageMillis_excludesLauncherAndStopScrolling() {
        val usage = mapOf(
            "com.instagram.android" to 30 * 60_000L,
            "com.example.stopscrolling" to 10 * 60_000L,
            "com.google.android.apps.nexuslauncher" to 15 * 60_000L
        )

        val total = DailyUsageAggregator.computeTotalUsageMillis(usage, excluded)

        assertEquals(30 * 60_000L, total)
    }

    @Test
    fun filterExcludedPackages_dropsZeroAndNegativeDurations() {
        val usage = mapOf(
            "com.instagram.android" to 5_000L,
            "com.example.unused" to 0L,
            "com.example.invalid" to -1_000L,
            "com.example.stopscrolling" to 10_000L
        )

        val filtered = DailyUsageAggregator.filterExcludedPackages(usage, excluded)

        assertEquals(mapOf("com.instagram.android" to 5_000L), filtered)
    }

    @Test
    fun computeTotalUsageMillis_returnsZeroWhenOnlyExcludedAppsUsed() {
        val usage = mapOf(
            "com.example.stopscrolling" to 12_000L,
            "com.google.android.apps.nexuslauncher" to 8_000L
        )

        val total = DailyUsageAggregator.computeTotalUsageMillis(usage, excluded)

        assertEquals(0L, total)
        assertTrue(DailyUsageAggregator.filterExcludedPackages(usage, excluded).isEmpty())
    }

    @Test
    fun dailyTotal_resetsAtMidnightBoundary() {
        val zone = TimeZone.getTimeZone("Europe/Helsinki")
        fun timeOf(day: Int, hour: Int, minute: Int): Long {
            return Calendar.getInstance(zone).apply {
                set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val yesterdayStart = timeOf(22, 23, 50)
        val todayEnd = timeOf(23, 0, 10)
        val now = timeOf(23, 0, 15)
        val todayMidnight = DayBoundary.startOfDayMillis(now, zone)

        val events = listOf(
            ForegroundEvent("com.instagram.android", yesterdayStart, true),
            ForegroundEvent("com.instagram.android", todayEnd, false),
            ForegroundEvent("com.google.android.apps.nexuslauncher", todayEnd, true),
            ForegroundEvent("com.google.android.apps.nexuslauncher", now, false)
        )

        val todayUsage = UsageCalculator.sumForegroundTime(events, todayMidnight, now)
        val total = DailyUsageAggregator.computeTotalUsageMillis(todayUsage, excluded)

        assertEquals(10 * 60_000L, total)
    }
}