package com.example.stopscrolling.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageBreakdownTest {

    private val excluded = setOf(
        "com.example.stopscrolling",
        "com.google.android.apps.nexuslauncher"
    )

    @Test
    fun buildSortedUsageBreakdown_sortsByDurationDescending() {
        val usage = mapOf(
            "com.spotify.music" to 15 * 60_000L,
            "com.google.android.youtube" to 60 * 60_000L,
            "com.instagram.android" to 25 * 60_000L
        )

        val breakdown = DailyUsageAggregator.buildSortedUsageBreakdown(usage, excluded)

        assertEquals(
            listOf(
                "com.google.android.youtube",
                "com.instagram.android",
                "com.spotify.music"
            ),
            breakdown.map { it.packageName }
        )
    }

    @Test
    fun buildSortedUsageBreakdown_excludesLauncherStopScrollingAndNonPositiveDurations() {
        val usage = mapOf(
            "com.instagram.android" to 30 * 60_000L,
            "com.example.stopscrolling" to 20 * 60_000L,
            "com.google.android.apps.nexuslauncher" to 10 * 60_000L,
            "com.example.unused" to 0L,
            "com.example.negative" to -5_000L
        )

        val breakdown = DailyUsageAggregator.buildSortedUsageBreakdown(usage, excluded)

        assertEquals(1, breakdown.size)
        assertEquals("com.instagram.android", breakdown[0].packageName)
        assertEquals(30 * 60_000L, breakdown[0].durationMillis)
        assertEquals(1.0f, breakdown[0].proportion, 0.0001f)
    }

    @Test
    fun buildSortedUsageBreakdown_computesProportionsAndMatchesDailyTotal() {
        val usage = mapOf(
            "com.google.android.youtube" to 60 * 60_000L,
            "com.instagram.android" to 30 * 60_000L,
            "com.spotify.music" to 10 * 60_000L,
            "com.example.stopscrolling" to 50 * 60_000L
        )

        val breakdown = DailyUsageAggregator.buildSortedUsageBreakdown(usage, excluded)
        val dailyTotal = DailyUsageAggregator.computeTotalUsageMillis(usage, excluded)

        assertEquals(dailyTotal, breakdown.sumOf { it.durationMillis })
        assertEquals(0.6f, breakdown[0].proportion, 0.0001f)
        assertEquals(0.3f, breakdown[1].proportion, 0.0001f)
        assertEquals(0.1f, breakdown[2].proportion, 0.0001f)
    }

    @Test
    fun buildSortedUsageBreakdown_breaksEqualDurationsByPackageName() {
        val usage = mapOf(
            "com.zeta.app" to 10 * 60_000L,
            "com.alpha.app" to 10 * 60_000L
        )

        val breakdown = DailyUsageAggregator.buildSortedUsageBreakdown(usage, excluded)

        assertEquals(
            listOf("com.alpha.app", "com.zeta.app"),
            breakdown.map { it.packageName }
        )
    }

    @Test
    fun buildSortedUsageBreakdown_returnsEmptyListWhenNoEligibleUsageExists() {
        val usage = mapOf(
            "com.example.stopscrolling" to 15 * 60_000L,
            "com.google.android.apps.nexuslauncher" to 5 * 60_000L,
            "com.example.zero" to 0L
        )

        val breakdown = DailyUsageAggregator.buildSortedUsageBreakdown(usage, excluded)

        assertTrue(breakdown.isEmpty())
    }
}