package com.example.stopscrolling.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

// How many hours before midnight we read the events (6 hours)
private const val LOOKBACK_MILLIS = 6 * 60 * 60 * 1000L

/*
 * Reads how long each app was used today, using UsageStatsManager.
 *
 * The user must grant the "Usage access" special permission (PACKAGE_USAGE_STATS),
 * otherwise the system returns no events.
 *
 * Call these functions from a background thread (for example Dispatchers.IO),
 * not from the main thread, to avoid ANRs.
 */
class UsageTracker(context: Context) {

    private val usageStatsManager =
        context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /*
     * Foreground time of every app today (since 00:00 local time), in milliseconds.
     * The key of the map is the package name.
     */
    fun getTodayUsagePerPackage(nowMillis: Long = System.currentTimeMillis()): Map<String, Long> {
        val startOfDay = DayBoundary.startOfDayMillis(nowMillis)
        // We also read some hours before midnight, to know if an app was already open.
        // The calculator counts only the time of today.
        val events = readForegroundEvents(startOfDay - LOOKBACK_MILLIS, nowMillis)
        return UsageCalculator.sumForegroundTime(events, startOfDay, nowMillis)
    }

    /*
     * Total foreground time today of a list of apps (for example all the apps of a group),
     * in milliseconds.
     */
    fun getTodayUsageForPackages(
        packageNames: Collection<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val usagePerPackage = getTodayUsagePerPackage(nowMillis)
        return packageNames.sumOf { usagePerPackage[it] ?: 0L }
    }

    // US-13: the past days of the week are read at most once per minute
    private val weeklyCache = WeeklyUsageCache { weekStart, todayStart ->
        // The calculator also needs the events before the start to know if an app was
        // already open, as for the day. It counts only the time between the two limits.
        val events = readForegroundEvents(weekStart - LOOKBACK_MILLIS, todayStart)
        UsageCalculator.sumForegroundTime(events, weekStart, todayStart)
    }

    /*
     * Foreground time of every app this week (since Monday 00:00 local time), in milliseconds.
     * US-13: past days (cached for 1 minute) + today (read live).
     */
    fun getWeekUsagePerPackage(nowMillis: Long = System.currentTimeMillis()): Map<String, Long> {
        val weekStart = WeekBoundary.startOfWeekMillis(nowMillis)
        val todayStart = DayBoundary.startOfDayMillis(nowMillis)

        val pastDays = weeklyCache.getPastDays(nowMillis, weekStart, todayStart)
        val today = getTodayUsagePerPackage(nowMillis)

        val total = pastDays.toMutableMap()
        for ((packageName, millis) in today) {
            total[packageName] = (total[packageName] ?: 0L) + millis
        }
        return total
    }

    /*
     * Total foreground time this week of a list of apps (for example all the apps
     * of a group), in milliseconds.
     */
    fun getWeekUsageForPackages(
        packageNames: Collection<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val usagePerPackage = getWeekUsagePerPackage(nowMillis)
        return packageNames.sumOf { usagePerPackage[it] ?: 0L }
    }

    /*
     * Reads the system events between [startMillis] and [endMillis] and keeps only
     * "app to foreground" and "app to background". The events are sorted by time.
     * Also used by the monitor service.
     */
    fun readForegroundEvents(startMillis: Long, endMillis: Long): List<ForegroundEvent> {
        val result = mutableListOf<ForegroundEvent>()
        val usageEvents = usageStatsManager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    result.add(
                        ForegroundEvent(event.packageName, event.timeStamp, true, event.className ?: "")
                    )
                // Sometimes Android sends only "stopped" and no "paused", so both events can
                // end a session. The calculator uses the class name to ignore late events
                // of a screen that is not the open one.
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED ->
                    result.add(
                        ForegroundEvent(event.packageName, event.timeStamp, false, event.className ?: "")
                    )
            }
        }
        return result
    }
}