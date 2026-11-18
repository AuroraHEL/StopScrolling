package com.example.stopscrolling.usage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.stopscrolling.MainActivity
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.hasUsageStatsPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Foreground service that checks which app is on the screen.
 *
 * Every second it reads the new usage events. When an app is opened, it looks for the
 * group of that app. If the app is in a regulated group, it publishes a
 * [RegulatedAppOpened] event in [UsageMonitorEvents].
 *
 * Start it with [UsageMonitorService.start] when the Usage Access permission is granted.
 */
class UsageMonitorService : Service() {

    // The work of the service runs here, not on the main thread (no ANR)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private lateinit var tracker: UsageTracker
    private val detector = ForegroundAppDetector()

    // The groups come from the database, through the repository of the app
    private val groupLookup: RegulatedGroupLookup by lazy {
        RoomRegulatedGroupLookup((application as StopScrollingApp).appGroupRepository::observeGroups)
    }

    override fun onCreate() {
        super.onCreate()
        tracker = UsageTracker(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android wants this call a few seconds after startForegroundService()
        startForeground(NOTIFICATION_ID, buildNotification(DEFAULT_TEXT))

        if (!hasUsageStatsPermission(this)) {
            Log.w(TAG, "Usage access is not granted: the service stops")
            stopSelf()
            return START_NOT_STICKY
        }

        // If the service is started again, we do not start a second loop
        if (monitorJob == null) {
            monitorJob = serviceScope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Checking the active app ----------

    private suspend fun monitorLoop() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        // We ignore the past: the service is started from our app, so StopScrolling
        // is the app on the screen at the beginning.
        var lastEventTime = System.currentTimeMillis()

        while (true) {
            try {
                val now = System.currentTimeMillis()
                val newEvents = tracker.readForegroundEvents(lastEventTime, now)
                    .filter { it.timestamp > lastEventTime }

                if (newEvents.isNotEmpty()) {
                    lastEventTime = newEvents.last().timestamp
                    for (packageName in detector.process(newEvents)) {
                        handleOpenedApp(packageName)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An error must not stop the monitoring
                Log.e(TAG, "Error while checking the active app", e)
            }

            // Slower checks when the screen is off, to save battery
            val waitTime = if (powerManager.isInteractive) POLL_SCREEN_ON_MS else POLL_SCREEN_OFF_MS
            delay(waitTime)
        }
    }

    // Looks for the group of the opened app and tells the rest of the app
    private suspend fun handleOpenedApp(packageName: String) {
        val groups = groupLookup.findGroupsOf(packageName)
        if (groups.isEmpty()) {
            updateNotification(DEFAULT_TEXT)
            return
        }

        // Debug text in the notification. It can be removed later.
        updateNotification("Regulated app opened: $packageName")

        // An app can be in more than one group: we send one event for each group
        for (group in groups) {
            val usageToday = tracker.getTodayUsageForPackages(group.packageNames)
            Log.d(TAG, "Regulated app opened: $packageName (group ${group.groupId}), usage today: ${usageToday / 1000} s")
            UsageMonitorEvents.publish(RegulatedAppOpened(packageName, group.groupId, usageToday))
        }
    }

    // ---------- Notification ----------

    private fun createNotificationChannel() {
        // Notification channels exist from Android 8
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage monitoring",
                NotificationManager.IMPORTANCE_LOW // no sound
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        // Tapping the notification opens the app
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view) // TODO: use an own icon
            .setContentTitle("StopScrolling is active")
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "UsageMonitor"
        private const val CHANNEL_ID = "usage_monitor"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_TEXT = "Watching your app usage"

        // How often we check the events
        private const val POLL_SCREEN_ON_MS = 1_000L
        private const val POLL_SCREEN_OFF_MS = 10_000L

        /* Starts the service. Call it when the app is on the screen. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UsageMonitorService::class.java)
            )
        }

        /* Stops the service. */
        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}