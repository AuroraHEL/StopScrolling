package com.example.stopscrolling.usage


object DailyUsageAggregator {

    fun filterExcludedPackages(
        usagePerPackage: Map<String, Long>,
        excludedPackages: Set<String>
    ): Map<String, Long> {
        return usagePerPackage.filter { (packageName, durationMillis) ->
            packageName !in excludedPackages && durationMillis > 0L
        }
    }

    fun computeTotalUsageMillis(
        usagePerPackage: Map<String, Long>,
        excludedPackages: Set<String>
    ): Long {
        return filterExcludedPackages(usagePerPackage, excludedPackages).values.sum()
    }
}