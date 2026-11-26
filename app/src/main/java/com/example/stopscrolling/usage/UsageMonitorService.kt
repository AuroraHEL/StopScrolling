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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.stopscrolling.notification.HardLimitWarningNotifier
import com.example.stopscrolling.data.LimitType

/*
 * Foreground service that checks which app is on the screen.
 *
 * It tracks usage, evaluates limits, and shows the hard
 * or soft overlay when a limit is reached.
 *
 * US-09: Processes pending group deletions and app removals.
 * US-15: Applies pending hard limit changes.
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

    private val warningStore by lazy {
        HardLimitWarningStore(this)
    }

    private val warningNotifier by lazy {
        HardLimitWarningNotifier(this)
    }

    // The groups come from the database, through the repository of the app
    private val groupLookup: RegulatedGroupLookup by lazy {
        RoomRegulatedGroupLookup(
            appGroupRepository::observeGroups
        )
    }

    private val extensionLookup: ExtensionLookup by lazy {
        (application as StopScrollingApp).extensionRepository
    }

    private val dailyUsageArchiver by lazy {
        (application as StopScrollingApp).dailyUsageArchiver
    }

    private val overlayManager by lazy {
        OverlayManager(this)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Prevent simultaneous pending change processing.
    private val pendingChangesMutex = Mutex()

    // US-09 and US-15: Process pending changes at midnight.
    private val dateChangeReceiver = DateChangeReceiver {
        serviceScope.launch {
            processPendingChanges()
        }
    }

    @Volatile
    private var currentGroups: List<RegulatedGroup> = emptyList()

    @Volatile
    private var currentPackageName: String? = null

    private var shownRequest: OverlayRequest? = null

    override fun onCreate() {
        super.onCreate()

        tracker = UsageTracker(this)

        createNotificationChannel()

        dateChangeReceiver.register(this)
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

        dateChangeReceiver.unregister(this)

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

        // US-09 and US-15:
        // Process overdue changes at service startup.
        processPendingChanges()

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

        // Periodic fallback for pending changes.
        var nextPendingCheckAt =
            System.currentTimeMillis() + PENDING_CHECK_INTERVAL_MS

        while (true) {

            try {

                val now = System.currentTimeMillis()

                // US-09 and US-15:
                // Check pending changes periodically.
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

    // ---------- Pending changes (US-09 and US-15) ----------

    private suspend fun processPendingChanges() {

        pendingChangesMutex.withLock {

            try {

                // US-09:
                // Execute scheduled group deletions and app removals.
                val processedGroupChanges =
                    appGroupRepository.processDueChanges(
                        System.currentTimeMillis()
                    )

                // US-15:
                // Apply scheduled hard limit changes.
                val processedLimitChanges =
                    appGroupRepository.applyDuePendingChanges()

                if (
                    processedGroupChanges > 0 ||
                    processedLimitChanges > 0
                ) {

                    Log.d(
                        TAG,
                        "Processed $processedGroupChanges pending group changes " +
                                "and $processedLimitChanges pending limit changes"
                    )

                    // Refresh the groups of the current app.
                    refreshCurrentGroups()

                    // Re-evaluate the overlay after changes.
                    checkLimits()
                }

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Cannot process pending changes",
                    e
                )
            }

            // US-27: Archive yesterday's total phone usage once after midnight.
            try {
                val archived = dailyUsageArchiver.archiveYesterdayIfNeeded()
                if (archived) {
                    Log.d(TAG, "Archived previous day's total usage into Room")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cannot archive previous day's usage", e)
            }
        }
    }

    // Refresh groups so that deletions, app removals,
    // and new limits are reflected immediately.
    private suspend fun refreshCurrentGroups() {

        val packageName = currentPackageName

        if (packageName == null) {

            currentGroups = emptyList()

            updateNotification(DEFAULT_TEXT)

            return
        }

        val groups = groupLookup.findGroupsOf(packageName)

        // The foreground app may have changed during the lookup.
        if (currentPackageName != packageName) return

        currentGroups = groups

        if (groups.isEmpty()) {

            updateNotification(DEFAULT_TEXT)

        } else {

            updateNotification(
                "Regulated app opened: $packageName"
            )
        }
    }

    private suspend fun handleOpenedApp(
        packageName: String
    ) {

        currentPackageName = packageName

        val groups = groupLookup.findGroupsOf(packageName)

        if (currentPackageName != packageName) return

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
            // US-13: the weekly values are read only if the group has a weekly limit
            // (the past days of the week come from a cache refreshed once per minute)
            val hasWeeklyLimit = group.weeklyLimitMinutes > 0
            GroupLimitState(
                group = group,
                usedMillis = tracker.getTodayUsageForPackages(group.packageNames),
                extraMinutes = extensionLookup.extraMinutesToday(group.groupId),
                weeklyUsedMillis = if (hasWeeklyLimit) {
                    tracker.getWeekUsageForPackages(group.packageNames)
                } else {
                    0L
                },
                weeklyExtraMinutes = if (hasWeeklyLimit) {
                    extensionLookup.extraMinutesThisWeek(group.groupId)
                } else {
                    0
                }
            )
        }

        val result = LimitEvaluator.evaluate(states)

        val decision = result.decision
        // US-16: Send a warning when a daily hard limit
        // has five minutes or less remaining.
        checkHardLimitWarnings(states)

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
            // US-13: the limit that was reached (today or this week), not always the daily one
            limitMinutes = decision.limitMinutes,
            limitKind = decision.limitKind
        )

        val shown = shownRequest
        // Same screen already visible: nothing to do
        // (the daily and the weekly screens of the same group are two different screens)
        if (
            shown != null &&
            shown.groupId == request.groupId &&
            shown.limitType == request.limitType &&
            shown.limitKind == request.limitKind &&
            shown.limitMinutes == request.limitMinutes
        ) {

            return
        }

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

    private fun checkHardLimitWarnings(states: List<GroupLimitState>) {
        for (state in states) {
            val group = state.group

            if (group.limitType != LimitType.HARD) continue

            if (!HardLimitWarningEvaluator.shouldWarn(
                    group = group,
                    usedMillis = state.usedMillis
                )
            ) {
                continue
            }

            if (warningStore.wasWarnedToday(group.groupId)) {
                continue
            }

            val sent = warningNotifier.showWarning(
                groupId = group.groupId,
                groupName = group.name
            )

            if (sent) {
                warningStore.markWarnedToday(group.groupId)
                Log.d(TAG, "Hard limit warning sent for group ${group.groupId}")
            }
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

        // Periodic fallback for US-09 and US-15.
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
