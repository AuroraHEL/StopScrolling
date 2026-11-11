package com.example.stopscrolling

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresPermission
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Spike SP-01: small helper functions to test what Android allows us to do.
// This is test code. It will not be part of the final app.

// One app and how many seconds it was used today
data class AppUsage(val label: String, val seconds: Long)

// ---------- Question 1: permission and usage time ----------

// Usage Access is a special permission. The user must turn it on in the Android settings.
// We check it with AppOpsManager, because there is no normal permission dialog for it.
@Suppress("DEPRECATION")
fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

// Opens the Android screen where the user can give the Usage Access permission
fun openUsageAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
}

// Time of today at 00:00
private fun startOfToday(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

// Name of the app that the user sees (for example "Chrome").
// If we cannot see the app, we show the package name.
private fun getLabel(context: Context, packageName: String): String {
    return try {
        val packageManager = context.packageManager
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}

// The apps that were used the most today, with the time in the foreground
@RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
fun getTodayUsage(context: Context, howMany: Int = 5): List<AppUsage> {
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val stats = manager.queryAndAggregateUsageStats(startOfToday(), System.currentTimeMillis())
    return stats.values
        .filter { it.totalTimeInForeground > 0 }
        .sortedByDescending { it.totalTimeInForeground }
        .take(howMany)
        .map { AppUsage(getLabel(context, it.packageName), it.totalTimeInForeground / 1000) }
}

// ---------- Question 2: which app is in the foreground? ----------

// Android saves an event every time an app comes to the foreground.
// Here we read the events of the last minutes and keep only those ones.
// The first line of the list is the app that is in the foreground now.
@RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
@Suppress("DEPRECATION")
fun getRecentForegroundApps(context: Context, minutes: Int = 10): List<String> {
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val events = manager.queryEvents(now - minutes * 60_000L, now)
    val event = UsageEvents.Event()
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val result = mutableListOf<String>()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            val time = formatter.format(Date(event.timeStamp))
            result.add("$time  ${getLabel(context, event.packageName)}")
        }
    }
    // Newest first
    return result.reversed()
}

// ---------- Question 4: list of installed apps ----------

// All the apps that the user can open from the home screen.
// On Android 11 and newer this needs the <queries> element in AndroidManifest.xml.
// Without it, the list is almost empty.
@Suppress("DEPRECATION")
fun getLaunchableApps(context: Context): List<String> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val packageManager = context.packageManager
    return packageManager.queryIntentActivities(intent, 0)
        .map { it.loadLabel(packageManager).toString() }
        .distinct()
        .sorted()
}
