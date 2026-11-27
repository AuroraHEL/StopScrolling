package com.example.stopscrolling.usage

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * UI-ready daily usage record for a single app (US-28).
 */
data class AppUsageBreakdownItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val durationMillis: Long,
    val proportion: Float
)

/**
 * Queries today's per-app phone usage sorted descending by duration (US-28),
 * reusing [DailyUsageProvider] so exclusions match the US-27 dashboard total.
 *
 * Call [getTodayUsageBreakdown] from a background thread (e.g., Dispatchers.IO).
 */
class AppUsageBreakdownProvider(
    context: Context,
    private val dailyUsageProvider: DailyUsageProvider = DailyUsageProvider(context)
) {

    private val appContext = context.applicationContext

    /**
     * Returns today's non-excluded apps sorted from most used to least used,
     * along with each app's label, icon, duration, and share of total screen time.
     */
    fun getTodayUsageBreakdown(
        nowMillis: Long = System.currentTimeMillis()
    ): List<AppUsageBreakdownItem> {
        val filteredUsage = dailyUsageProvider.getTodayFilteredUsagePerPackage(nowMillis)
        val sortedEntries = DailyUsageAggregator.buildSortedUsageBreakdown(
            usagePerPackage = filteredUsage,
            excludedPackages = emptySet()
        )

        return sortedEntries
            .map { entry ->
                val (label, icon) = resolveAppInfo(entry.packageName)
                AppUsageBreakdownItem(
                    packageName = entry.packageName,
                    appName = label,
                    icon = icon,
                    durationMillis = entry.durationMillis,
                    proportion = entry.proportion
                )
            }
            .sortedWith(
                compareByDescending<AppUsageBreakdownItem> { it.durationMillis }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
                    .thenBy { it.packageName }
            )
    }

    private fun resolveAppInfo(packageName: String): Pair<String, Drawable?> {
        val packageManager = appContext.packageManager
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val icon = packageManager.getApplicationIcon(appInfo)
            label to icon
        } catch (_: PackageManager.NameNotFoundException) {
            packageName to null
        }
    }
}