package com.example.stopscrolling.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Reads today's phone usage via [UsageTracker] while excluding StopScrolling
 * and installed home launcher packages (US-27 and US-28).
 *
 * Call these functions from a background thread (e.g., Dispatchers.IO) to avoid ANRs.
 */
class DailyUsageProvider(
    context: Context,
    private val usageTracker: UsageTracker = UsageTracker(context)
) {

    private val appContext = context.applicationContext

    /**
     * Returns StopScrolling's own package name plus all installed home launcher packages.
     */
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

    /**
     * Returns today's foreground duration (in milliseconds) per non-excluded app.
     * Reused by US-28 so per-app totals match the dashboard daily total.
     */
    fun getTodayFilteredUsagePerPackage(
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Long> {
        val rawUsage = usageTracker.getTodayUsagePerPackage(nowMillis)
        return DailyUsageAggregator.filterExcludedPackages(rawUsage, getExcludedPackages())
    }

    /**
     * Returns today's total phone usage time across all non-excluded apps, in milliseconds.
     */
    fun getTodayTotalUsageMillis(
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val rawUsage = usageTracker.getTodayUsagePerPackage(nowMillis)
        return DailyUsageAggregator.computeTotalUsageMillis(rawUsage, getExcludedPackages())
    }
}