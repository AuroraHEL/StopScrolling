package com.example.stopscrolling.usage

/**
 * Pure aggregation helpers for daily phone usage (US-27 and US-28).
 * Keeps filtering and summation independent of Android APIs for JVM unit testing.
 */
object DailyUsageAggregator {

    /**
     * Removes excluded packages (such as home launchers and StopScrolling itself)
     * and drops entries with zero or negative foreground time.
     */
    fun filterExcludedPackages(
        usagePerPackage: Map<String, Long>,
        excludedPackages: Set<String>
    ): Map<String, Long> {
        return usagePerPackage.filter { (packageName, durationMillis) ->
            packageName !in excludedPackages && durationMillis > 0L
        }
    }

    /**
     * Sums today's foreground usage time (in milliseconds) across all non-excluded apps.
     */
    fun computeTotalUsageMillis(
        usagePerPackage: Map<String, Long>,
        excludedPackages: Set<String>
    ): Long {
        return filterExcludedPackages(usagePerPackage, excludedPackages).values.sum()
    }
}