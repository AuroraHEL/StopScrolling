package com.example.stopscrolling.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// US-13: the weekly usage is recomputed at most once per minute
class WeeklyUsageCacheTest {

    private val monday = 1_000_000L
    private val wednesday = monday + 2 * DAY

    private var loadCount = 0
    private var loadedValue = 60_000L

    private fun newCache() = WeeklyUsageCache { _, _ ->
        loadCount++
        mapOf("app.a" to loadedValue)
    }

    @Test
    fun twoCallsInTheSameMinute_loadOnlyOnce() {
        val cache = newCache()
        cache.getPastDays(wednesday + 1_000, monday, wednesday)
        cache.getPastDays(wednesday + 30_000, monday, wednesday)
        cache.getPastDays(wednesday + 59_999, monday, wednesday)
        assertEquals(1, loadCount)
    }

    @Test
    fun afterOneMinute_loadsAgain() {
        val cache = newCache()
        cache.getPastDays(wednesday, monday, wednesday)
        loadedValue = 120_000L
        val result = cache.getPastDays(wednesday + 60_000, monday, wednesday)
        assertEquals(2, loadCount)
        assertEquals(120_000L, result["app.a"])
    }

    @Test
    fun whenTheDayChanges_loadsAgainEvenInsideTheMinute() {
        val cache = newCache()
        cache.getPastDays(wednesday + DAY - 1_000, monday, wednesday)
        // Midnight: today starts one day later, the old past days are not valid anymore
        cache.getPastDays(wednesday + DAY, monday, wednesday + DAY)
        assertEquals(2, loadCount)
    }

    @Test
    fun whenTheWeekChanges_loadsAgain() {
        val cache = newCache()
        cache.getPastDays(wednesday, monday, wednesday)
        val nextMonday = monday + 7 * DAY
        cache.getPastDays(nextMonday + DAY, nextMonday, nextMonday + DAY)
        assertEquals(2, loadCount)
    }

    @Test
    fun onMonday_thereIsNoPastDay_andNothingIsLoaded() {
        val cache = newCache()
        val result = cache.getPastDays(monday + 5_000, monday, monday)
        assertTrue(result.isEmpty())
        assertEquals(0, loadCount)
    }

    @Test
    fun clockGoingBackwards_doesNotKeepAnOldValueForever() {
        val cache = newCache()
        cache.getPastDays(wednesday + 100_000, monday, wednesday)
        cache.getPastDays(wednesday + 10_000, monday, wednesday) // the clock went back
        assertEquals(2, loadCount)
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
