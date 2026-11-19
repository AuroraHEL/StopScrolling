package com.example.stopscrolling.usage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.stopscrolling.MainActivity
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.hasUsageStatsPermission
import com.example.stopscrolling.overlay.OverlayManager
import com.example.stopscrolling.ui.overlay.OverlayContent
import com.example.stopscrolling.ui.overlay.OverlayRequest
import com.example.stopscrolling.ui.theme.StopScrollingTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.stopscrolling.ui.mathquiz.MathQuizDialog

/*
 * Foreground service that checks which app is on the screen.
 *
 * Every second it reads the new usage events. When an app is opened, it looks for the
 * groups of that app and remembers them. It then checks the limits of these groups
 * (see [LimitEvaluator]) and shows the hard or soft overlay when a limit is reached.
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

    private val extensionLookup: ExtensionLookup by lazy {
        (application as StopScrollingApp).extensionRepository
    }

    private val overlayManager by lazy { OverlayManager(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    // Groups of the app that is on the screen (empty if it is not regulated)
    @Volatile
    private var currentGroups: List<RegulatedGroup> = emptyList()

    // What the overlay shows now, null if there is no overlay. Main thread only.
    private var shownRequest: OverlayRequest? = null

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
        hideOverlayNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Checking the active app ----------

    private suspend fun monitorLoop() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        var lastEventTime = System.currentTimeMillis()

        // Android can restart this service while a regulated app is open (the overlay is lost
        // with the process). The last usage event tells us which app is on the screen.
        try {
            val lastEvent = tracker
                .readForegroundEvents(lastEventTime - STARTUP_LOOKBACK_MS, lastEventTime)
                .lastOrNull()
            if (lastEvent != null && lastEvent.isResumed) {
                detector.process(listOf(lastEvent))
                handleOpenedApp(lastEvent.packageName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cannot find the app on the screen at startup", e)
        }
        // When the limits must be checked again (0 = as soon as possible)
        var nextLimitCheckAt = 0L

        while (true) {
            try {
                val now = System.currentTimeMillis()
                val newEvents = tracker.readForegroundEvents(lastEventTime, now)
                    .filter { it.timestamp > lastEventTime }

                if (newEvents.isNotEmpty()) {
                    lastEventTime = newEvents.last().timestamp
                    for (packageName in detector.process(newEvents)) {
                        handleOpenedApp(packageName)
                        nextLimitCheckAt = 0L // a new app is open: check right away
                    }
                }

                if (now >= nextLimitCheckAt) {
                    nextLimitCheckAt = now + checkLimits()
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

    // Looks for the groups of the opened app and tells the rest of the app
    private suspend fun handleOpenedApp(packageName: String) {
        val groups = groupLookup.findGroupsOf(packageName)
        currentGroups = groups
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

    // ---------- Limits and overlay ----------

    /* Checks the limits of the open app. Returns the time to wait before the next check. */
    private suspend fun checkLimits(): Long {
        val groups = currentGroups
        if (groups.isEmpty()) {
            withContext(Dispatchers.Main) { hideOverlayNow() }
            return LimitEvaluator.SLOW_CHECK_MILLIS
        }

        val states = groups.map { group ->
            GroupLimitState(
                group = group,
                usedMillis = tracker.getTodayUsageForPackages(group.packageNames),
                extraMinutes = extensionLookup.extraMinutesToday(group.groupId)
            )
        }
        val result = LimitEvaluator.evaluate(states)
        val decision = result.decision

        withContext(Dispatchers.Main) {
            if (decision == null) hideOverlayNow() else showOverlayNow(decision)
        }
        return result.nextCheckInMillis
    }

    // The three functions below must run on the main thread
    private fun showOverlayNow(decision: BlockDecision) {
        val group = decision.group
        val request = OverlayRequest(
            groupId = group.groupId,
            groupName = group.name,
            limitType = group.limitType,
            usedMinutes = decision.usedMinutes,
            limitMinutes = group.timeLimitMinutes
        )

        val shown = shownRequest
        // Same screen already visible: nothing to do
        if (shown != null && shown.groupId == request.groupId && shown.limitType == request.limitType) return
        hideOverlayNow() // another group or type: replace it

        val ok = overlayManager.show {
            StopScrollingTheme {
                OverlayContent(
                    request = request,
                    onGoHome = ::onOverlayGoHome,
                    onExtensionGranted = ::onExtensionGranted,
                    challengeContent = { onSolved, onCancel ->
                        MathQuizDialog(
                            onDismiss = onCancel,
                            onQuizSolved = {
                                serviceScope.launch {
                                    (application as StopScrollingApp).extensionRepository
                                        .grantExtension(groupId = request.groupId)
                                    onSolved()
                                }
                            }
                        )
                    }
                )
            }
        }
        if (ok) {
            shownRequest = request
        } else {
            Log.w(TAG, "Overlay not shown: is the display over other apps permission granted?")
        }
    }

    private fun hideOverlayNow() {
        if (shownRequest == null) return
        overlayManager.hide()
        shownRequest = null
    }

    /* Opens the home screen. Returns false if Android refused. */
    private fun goToHomeScreen(): Boolean {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(home)
            true
        } catch (e: RuntimeException) { // ActivityNotFoundException, SecurityException...
            Log.e(TAG, "Cannot open the home screen", e)
            false
        }
    }

    // Called by the overlay buttons. post{} so that the view is not removed during its own click.
    private fun onOverlayGoHome() {
        mainHandler.post {
            // If the home screen cannot be opened, the overlay stays: the user must not stay
            // in the app without a block.
            if (goToHomeScreen()) {
                // Forget the open app: otherwise the next check shows the overlay again
                // before the launcher event arrives
                currentGroups = emptyList()
                hideOverlayNow()
            }
        }
    }

    // The challenge saved the extension: hide now, the next check (within 1 s) re-evaluates
    private fun onExtensionGranted() {
        mainHandler.post { hideOverlayNow() }
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

        // How far back we look for the app on the screen when the loop starts (6 hours)
        private const val STARTUP_LOOKBACK_MS = 6 * 60 * 60 * 1000L

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