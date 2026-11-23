package com.example.stopscrolling.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build


class DailyUsageProvider(
    context: Context,
    private val usageTracker: UsageTracker = UsageTracker(context)
) {

    private val appContext = context.applicationContext


    fun getExcludedPackages(): Set<String> {
        val excluded = mutableSetOf(appContext.packageName)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val packageManager = appContext.packageManager
        val homeActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
        }

        for (resolveInfo in homeActivities) {
            resolveInfo.activityInfo?.packageName?.let { excluded.add(it) }
        }
        return excluded
    }


    fun getTodayFilteredUsagePerPackage(
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Long> {
        val rawUsage = usageTracker.getTodayUsagePerPackage(nowMillis)
        return DailyUsageAggregator.filterExcludedPackages(rawUsage, getExcludedPackages())
    }


    fun getTodayTotalUsageMillis(
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val rawUsage = usageTracker.getTodayUsagePerPackage(nowMillis)
        return DailyUsageAggregator.computeTotalUsageMillis(rawUsage, getExcludedPackages())
    }
}