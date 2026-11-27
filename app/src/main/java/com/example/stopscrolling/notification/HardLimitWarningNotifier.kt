package com.example.stopscrolling.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.stopscrolling.MainActivity

/**
 * Sends a notification when a hard limit
 * has five minutes or less remaining.
 */
class HardLimitWarningNotifier(
    private val context: Context
) {

    private val notificationManager =
        context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

    /**
     * Returns true if the notification was posted.
     * Returns false when notifications are not allowed.
     */
    fun showWarning(
        groupId: Int,
        groupName: String,
        limitPeriod: String = "daily"
    ): Boolean {

        // Android 13+ requires notification permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }

        // Respect notifications disabled in Android settings.
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }

        // Tapping the notification opens StopScrolling.
        val openAppIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.HARD_LIMIT_WARNING_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("5-minute hard limit warning")
            .setContentText("5 min left for $groupName")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Different IDs allow warnings for different groups.
        val notificationId =
            10_000 + groupId

        return try {
            notificationManager.notify(notificationId, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
