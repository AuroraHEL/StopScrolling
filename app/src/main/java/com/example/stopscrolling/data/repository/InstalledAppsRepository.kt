package com.example.stopscrolling.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.stopscrolling.data.InstalledApp
import android.os.Build

class InstalledAppsRepository(
    private val context: Context
) {
    fun getInstalledApps(): List<InstalledApp> {
        val packageManager = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(
                    PackageManager.MATCH_ALL.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL
            )
        }

        return activities.mapNotNull { activity ->
            try {
                val appInfo = activity.activityInfo.applicationInfo

                InstalledApp(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.distinctBy { it.packageName }
    }
}
