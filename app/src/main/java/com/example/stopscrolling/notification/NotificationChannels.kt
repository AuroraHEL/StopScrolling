package com.example.stopscrolling.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channels used by StopScrolling.
 */
object NotificationChannels {

    const val HARD_LIMIT_WARNING_CHANNEL_ID = "hard_limit_warning"

    fun createHardLimitWarningChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HARD_LIMIT_WARNING_CHANNEL_ID,
                "Hard limit warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Notifies you when only 5 minutes remain before a hard limit."
            }

            val manager =
                context.getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }
}
