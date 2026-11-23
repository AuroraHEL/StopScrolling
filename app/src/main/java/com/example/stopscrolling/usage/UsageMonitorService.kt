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
import com.example.stopscrolling.ui.mathquiz.MathQuizDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Foreground service that checks which app is on the screen.
 *
 * It tracks usage, evaluates limits, and shows the hard
 * or soft overlay when a limit is reached.
 *
 * US-09 Task 3:
 * Also processes pending group deletions and app removals.
 */
class UsageMonitorService : Service() {

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var monitorJob: Job? = null

    private lateinit var tracker: UsageTracker

    private val detector = ForegroundAppDetector()

    private val appGroupRepository by lazy {
        (application as StopScrollingApp).appGroupRepository
    }

    private val groupLookup: RegulatedGroupLookup by lazy {
        RoomRegulatedGroupLookup(
            appGroupRepository::observeGroups
        )
    }

    private val extensionLookup: ExtensionLookup by lazy {
        (application as StopScrollingApp).extensionRepository
    }

    private val overlayManager by lazy {
        OverlayManager(this)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentGroups: List<RegulatedGroup> = emptyList()

    // US-09: Remember the last opened app for refreshing groups.
    @Volatile
    private var currentPackageName: String? = null

    private var shownRequest: OverlayRequest? = null

    override fun onCreate() {
        super.onCreate()
        tracker = UsageTracker(this)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startForeground(
            NOTIFICATION_ID,
            buildNotification(DEFAULT_TEXT)
        )

        if (!hasUsageStatsPermission(this)) {
            Log.w(
                TAG,
                "Usage access is not granted: the service stops"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        if (monitorJob == null) {
            monitorJob = serviceScope.launch {
                monitorLoop()
            }
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

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as PowerManager

        var lastEventTime = System.currentTimeMillis()

        // US-09: Process overdue changes at service startup.
        try {
            processPendingChanges()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cannot process pending changes at startup", e)
        }

        // Restore the foreground app after service restart.
        try {
            val lastEvent = tracker
                .readForegroundEvents(
                    lastEventTime - STARTUP_LOOKBACK_MS,
                    lastEventTime
                )
                .lastOrNull()

            if (lastEvent != null && lastEvent.isResumed) {
                detector.process(listOf(lastEvent))
                handleOpenedApp(lastEvent.packageName)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Cannot find the app on the screen at startup",
                e
            )
        }

        var nextLimitCheckAt = 0L

        // US-09: First check runs immediately.
        var nextPendingCheckAt = 0L

        while (true) {
            try {
                val now = System.currentTimeMillis()

                // US-09: Check pending changes periodically.
                if (now >= nextPendingCheckAt) {
                    processPendingChanges()
                    nextPendingCheckAt =
                        now + PENDING_CHECK_INTERVAL_MS
                }

                val newEvents = tracker
                    .readForegroundEvents(lastEventTime, now)
                    .filter {
                        it.timestamp > lastEventTime
                    }

                if (newEvents.isNotEmpty()) {
                    lastEventTime = newEvents.last().timestamp

                    for (packageName in detector.process(newEvents)) {
                        handleOpenedApp(packageName)
                        nextLimitCheckAt = 0L
                    }
                }

                if (now >= nextLimitCheckAt) {
                    nextLimitCheckAt = now + checkLimits()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error while checking the active app",
                    e
                )
            }

            val waitTime =
                if (powerManager.isInteractive) {
                    POLL_SCREEN_ON_MS
                } else {
                    POLL_SCREEN_OFF_MS
                }

            delay(waitTime)
        }
    }

    // US-09 Task 3: Execute due group changes.
    private suspend fun processPendingChanges() {

        val processed =
            appGroupRepository.processDueChanges(
                System.currentTimeMillis()
            )

        if (processed <= 0) return

        Log.d(
            TAG,
            "Processed $processed pending group changes"
        )

        // Refresh the groups of the current app.
        val packageName = currentPackageName

        if (packageName != null) {
            handleOpenedApp(packageName)
        } else {
            currentGroups = emptyList()
        }

        // Re-evaluate the overlay after group changes.
        checkLimits()
    }

    private suspend fun handleOpenedApp(
        packageName: String
    ) {

        currentPackageName = packageName

        val groups = groupLookup.findGroupsOf(packageName)
        currentGroups = groups

        if (groups.isEmpty()) {
            updateNotification(DEFAULT_TEXT)
            return
        }

        updateNotification(
            "Regulated app opened: $packageName"
        )

        for (group in groups) {
            val usageToday =
                tracker.getTodayUsageForPackages(
                    group.packageNames
                )

            Log.d(
                TAG,
                "Regulated app opened: $packageName " +
                        "(group ${group.groupId}), " +
                        "usage today: ${usageToday / 1000} s"
            )

            UsageMonitorEvents.publish(
                RegulatedAppOpened(
                    packageName,
                    group.groupId,
                    usageToday
                )
            )
        }
    }

    // ---------- Limits and overlay ----------

    private suspend fun checkLimits(): Long {

        val groups = currentGroups

        if (groups.isEmpty()) {
            withContext(Dispatchers.Main) {
                hideOverlayNow()
            }
            return LimitEvaluator.SLOW_CHECK_MILLIS
        }

        val states = groups.map { group ->
            GroupLimitState(
                group = group,
                usedMillis =
                    tracker.getTodayUsageForPackages(
                        group.packageNames
                    ),
                extraMinutes =
                    extensionLookup.extraMinutesToday(
                        group.groupId
                    )
            )
        }

        val result = LimitEvaluator.evaluate(states)
        val decision = result.decision

        withContext(Dispatchers.Main) {
            if (decision == null) {
                hideOverlayNow()
            } else {
                showOverlayNow(decision)
            }
        }

        return result.nextCheckInMillis
    }

    private fun showOverlayNow(
        decision: BlockDecision
    ) {

        val group = decision.group

        val request = OverlayRequest(
            groupId = group.groupId,
            groupName = group.name,
            limitType = group.limitType,
            usedMinutes = decision.usedMinutes,
            limitMinutes = group.timeLimitMinutes
        )

        val shown = shownRequest

        if (
            shown != null &&
            shown.groupId == request.groupId &&
            shown.limitType == request.limitType
        ) {
            return
        }

        hideOverlayNow()

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
                                    (application as StopScrollingApp)
                                        .extensionRepository
                                        .grantExtension(
                                            groupId = request.groupId
                                        )
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
            Log.w(
                TAG,
                "Overlay not shown: is the display over other apps permission granted?"
            )
        }
    }

    private fun hideOverlayNow() {
        if (shownRequest == null) return
        overlayManager.hide()
        shownRequest = null
    }

    private fun goToHomeScreen(): Boolean {

        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            startActivity(home)
            true
        } catch (e: RuntimeException) {
            Log.e(
                TAG,
                "Cannot open the home screen",
                e
            )
            false
        }
    }

    private fun onOverlayGoHome() {
        mainHandler.post {
            if (goToHomeScreen()) {
                currentGroups = emptyList()
                currentPackageName = null
                hideOverlayNow()
            }
        }
    }

    private fun onExtensionGranted() {
        mainHandler.post {
            hideOverlayNow()
        }
    }

    // ---------- Notification ----------

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage monitoring",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        text: String
    ): Notification {

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_menu_view
            )
            .setContentTitle(
                "StopScrolling is active"
            )
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(NotificationManager::class.java)

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    companion object {

        private const val TAG = "UsageMonitor"

        private const val CHANNEL_ID = "usage_monitor"

        private const val NOTIFICATION_ID = 1

        private const val DEFAULT_TEXT =
            "Watching your app usage"

        private const val POLL_SCREEN_ON_MS = 1_000L

        private const val POLL_SCREEN_OFF_MS = 10_000L

        // US-09: Check pending changes every 30 seconds.
        private const val PENDING_CHECK_INTERVAL_MS = 30_000L

        private const val STARTUP_LOOKBACK_MS =
            6 * 60 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(
                    context,
                    UsageMonitorService::class.java
                )
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(
                    context,
                    UsageMonitorService::class.java
                )
            )
        }
    }
}
