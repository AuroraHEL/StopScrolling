package com.example.stopscrolling.usage

/**
 * Pure per-app usage entry without Android dependencies (US-28),
 * containing the app's duration and its share of today's total screen time (0f..1f).
 */
data class AppUsageEntry(
    val packageName: String,
    val durationMillis: Long,
    val proportion: Float
)

/**
 * Pure aggregation helpers for daily phone usage (US-27 and US-28).
 * Keeps filtering, summation, and sorting independent of Android APIs for JVM unit testing.
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

    /**
     * Filters out excluded and non-positive entries, computes each app's proportion
     * of total daily screen time, and returns entries sorted by duration descending.
     */
    fun buildSortedUsageBreakdown(
        usagePerPackage: Map<String, Long>,
        excludedPackages: Set<String>
    ): List<AppUsageEntry> {
        val filtered = filterExcludedPackages(usagePerPackage, excludedPackages)
        if (filtered.isEmpty()) return emptyList()

        val totalMillis = filtered.values.sum()
        if (totalMillis <= 0L) return emptyList()

        return filtered.entries
            .map { (packageName, durationMillis) ->
                AppUsageEntry(
                    packageName = packageName,
                    durationMillis = durationMillis,
                    proportion = (durationMillis.toFloat() / totalMillis.toFloat())
                        .coerceIn(0f, 1f)
                )
            }
            .sortedWith(
                compareByDescending<AppUsageEntry> { it.durationMillis }
                    .thenBy { it.packageName }
            )
    }
}