package com.example.stopscrolling.usage

/*
 * Keeps the usage of the PAST days of the week in memory (US-13).
 *
 * Reading the events of a whole week is slow. The service checks the limits every second,
 * so it must not do it at each check (ANR and battery). The cache gives the same answer
 * for [ttlMillis] (1 minute): the week is recomputed at most once per minute.
 *
 * Only the days BEFORE today are cached. The time of today changes every second, so it is
 * read live by the caller and added to the cached part. In this way the weekly limit is
 * not late by up to one minute when the user is scrolling.
 *
 * [loadPastDays] reads the usage between the start of the week and the start of today,
 * for each package. It is a parameter so that the cache can be tested without Android.
 */
class WeeklyUsageCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val loadPastDays: (weekStartMillis: Long, todayStartMillis: Long) -> Map<String, Long>
) {

    private var cachedUsage: Map<String, Long> = emptyMap()
    private var cachedAtMillis: Long = 0L
    private var cachedWeekStart: Long = -1L
    private var cachedTodayStart: Long = -1L

    /*
     * Usage of each package from [weekStartMillis] to [todayStartMillis], in milliseconds.
     * It is empty on Monday (no past day yet in the week).
     * Many threads can call it: the service and the dashboard.
     */
    @Synchronized
    fun getPastDays(
        nowMillis: Long,
        weekStartMillis: Long,
        todayStartMillis: Long
    ): Map<String, Long> {
        // Monday: nothing to read
        if (todayStartMillis <= weekStartMillis) return emptyMap()

        val age = nowMillis - cachedAtMillis
        val stillValid = cachedWeekStart == weekStartMillis &&
            cachedTodayStart == todayStartMillis &&
            age in 0 until ttlMillis

        if (!stillValid) {
            cachedUsage = loadPastDays(weekStartMillis, todayStartMillis)
            cachedAtMillis = nowMillis
            cachedWeekStart = weekStartMillis
            cachedTodayStart = todayStartMillis
        }
        return cachedUsage
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 60_000L
    }
}
