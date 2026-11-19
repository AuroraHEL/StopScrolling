package com.example.stopscrolling.usage

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.MainActivity
import com.example.stopscrolling.R
import com.example.stopscrolling.StopScrollingApp
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.ui.overlay.EXTENSION_MINUTES
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * End-to-end test of the background service (usage, detection, limits and overlay).
 *
 * It runs on the emulator and uses the real app, the real database, the real
 * UsageStatsManager and the real overlay window. Nothing is faked.
 * The test does what a user does:
 *  1. the permissions are granted (Usage Access, display over other apps, notifications),
 *  2. the app is opened, so MainActivity starts the service,
 *  3. other apps are opened with the shell, and we check what the service does.
 *
 * WARNING: before and after every test, ALL the groups of the app are deleted, so that the
 * test starts from a clean database. For this reason the test only runs on an emulator.
 *
 * Not tested here: the extension with the math quiz (US-19 is not connected to the
 * service yet: the service uses NoExtensionLookup) and the restart after a phone reboot
 * (not implemented).
 *
 */
@RunWith(AndroidJUnit4::class)
class BackgroundServiceE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val appPackage = context.packageName

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        // The test deletes all the groups, so we never run it on a real phone
        assumeTrue("Run this test on an emulator: it deletes all the groups", isEmulator())

        // The permissions that the user normally gives by hand
        shell("appops set $appPackage GET_USAGE_STATS allow")
        shell("appops set $appPackage SYSTEM_ALERT_WINDOW allow")
        shell("pm grant $appPackage android.permission.POST_NOTIFICATIONS")

        // Screen on and unlocked, otherwise the other apps cannot be opened
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")

        allowReadingAllWindows()
        clearGroups()

        // Opening the app is the normal way to start the service (MainActivity.onResume)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        assertTrue(
            "The notification of the service did not appear",
            waitUntil(10_000) { findServiceNotification() != null }
        )
        // The service ignores what happened before its loop started,
        // so we give it a moment before we open other apps
        Thread.sleep(2_000)
    }

    @After
    fun tearDown() {
        // If setUp was skipped (not an emulator), there is nothing to clean
        if (!isEmulator()) return

        UsageMonitorService.stop(context) // it also removes the overlay
        shell("input keyevent KEYCODE_HOME")
        scenario?.close()
        // We do not leave a limit in the database: it would block Settings later
        clearGroups()
        // Wait until the notification is gone, so the next test starts clean
        waitUntil(5_000) { findServiceNotification() == null }
    }

    // ---------- Service and notification ----------

    @Test
    fun openingTheApp_startsTheServiceWithAnOngoingNotification() {
        val notification = findServiceNotification()
            ?: throw AssertionError("The notification of the service is missing")

        assertTrue(
            "The notification must be ongoing (the user cannot swipe it away)",
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        )
        assertEquals(DEFAULT_TEXT, notificationText())
    }

    // ---------- Detecting the opening of an app (no limit in the group) ----------

    @Test
    fun openingAnAppOfARegulatedGroup_publishesAnEventAndUpdatesTheNotification() {
        val groupId = createGroup(timeLimitMinutes = NO_LIMIT)

        val event = openAppAndWaitForEvent(OPEN_SETTINGS, 15_000) {
            it.packageName == SETTINGS_PACKAGE && it.groupId == groupId
        }

        assertNotNull("No event for the Settings app", event)
        assertTrue("The usage of the group cannot be negative", event!!.groupUsageTodayMillis >= 0)
        assertTrue(
            "The notification text was not updated",
            waitUntil(5_000) { notificationText() == "Regulated app opened: $SETTINGS_PACKAGE" }
        )
    }

    @Test
    fun openingAnAppOutsideTheGroup_publishesNoEvent_andTheServiceKeepsWorking() {
        val groupId = createGroup(timeLimitMinutes = NO_LIMIT)

        // The dialer is not in the group, so there must be no event for our group
        val eventForDialer = openAppAndWaitForEvent(OPEN_DIALER, 6_000) {
            it.groupId == groupId
        }
        assertNull("An app outside the group must not give an event", eventForDialer)

        // Proof that the silence above is not a dead service: now we open a group app
        val eventForSettings = openAppAndWaitForEvent(OPEN_SETTINGS, 15_000) {
            it.packageName == SETTINGS_PACKAGE && it.groupId == groupId
        }
        assertNotNull("The service did not react after the other app", eventForSettings)
    }

    @Test
    fun movingToAnotherScreenOfTheSameApp_publishesNoNewEvent() {
        val groupId = createGroup(timeLimitMinutes = NO_LIMIT)

        val first = openAppAndWaitForEvent(OPEN_SETTINGS, 15_000) {
            it.packageName == SETTINGS_PACKAGE && it.groupId == groupId
        }
        assertNotNull("No event for the first screen of Settings", first)

        // Another screen of the same app: same package, so it is not a new opening
        val second = openAppAndWaitForEvent(OPEN_WIFI_SETTINGS, 6_000) {
            it.packageName == SETTINGS_PACKAGE && it.groupId == groupId
        }
        assertNull("A second screen of the same app must not give a new event", second)
    }

    // ---------- Limits and overlay ----------

    @Test
    fun hardLimitReached_showsTheHardLockOverlay() {
        showOverlayForSettings(LimitType.HARD)

        assertTrue(hardTitle() in visibleTexts())
        assertFalse("The soft screen must not appear", softTitle() in visibleTexts())
    }

    @Test
    fun softLimitReached_showsTheSoftLimitOverlay() {
        showOverlayForSettings(LimitType.SOFT)

        assertTrue(softTitle() in visibleTexts())
        assertTrue(
            "The button to ask for more time is missing",
            extendButtonText() in visibleTexts()
        )
        assertFalse("The hard screen must not appear", hardTitle() in visibleTexts())
    }

    @Test
    fun appUnderTheLimit_showsNoOverlay() {
        val usedMinutes = settingsUsedMinutesAtLeastOne()
        val groupId = createGroup(timeLimitMinutes = usedMinutes + 60, limitType = LimitType.HARD)

        val event = openAppAndWaitForEvent(OPEN_SETTINGS, 15_000) {
            it.packageName == SETTINGS_PACKAGE && it.groupId == groupId
        }
        assertNotNull("No event for the Settings app", event)

        // The limits are checked right after the opening: we wait and then look at the screen
        Thread.sleep(5_000)
        assertFalse("An overlay appeared but the limit is not reached", hardTitle() in visibleTexts())
        assertFalse("An overlay appeared but the limit is not reached", softTitle() in visibleTexts())
    }

    @Test
    fun leavingTheBlockedApp_removesTheOverlay() {
        showOverlayForSettings(LimitType.HARD)

        // The user goes to an app that is not regulated: the block must go away
        openApp(OPEN_DIALER)

        assertTrue(
            "The overlay is still on the screen after leaving the blocked app",
            waitUntil(15_000) { hardTitle() !in visibleTexts() }
        )
    }

    // ---------- Helpers: scenarios ----------

    // Opens Settings when its limit is already reached and waits for the overlay of [type]
    private fun showOverlayForSettings(type: LimitType) {
        // The limit is the minutes already used: the limit is reached as soon as Settings opens
        val usedMinutes = settingsUsedMinutesAtLeastOne()
        createGroup(timeLimitMinutes = usedMinutes, limitType = type)

        openApp(OPEN_SETTINGS)

        val expectedTitle = if (type == LimitType.HARD) hardTitle() else softTitle()
        val shown = waitUntil(20_000) { expectedTitle in visibleTexts() }
        if (!shown) {
            throw AssertionError(
                "The overlay \"$expectedTitle\" did not appear " +
                    "(limit: $usedMinutes min, type: $type)\n${overlayDiagnostics()}"
            )
        }
    }

    // Information for the error message when the overlay does not appear:
    // is the permission granted, which windows exist, what the screen shows, what the service logged
    private fun overlayDiagnostics(): String {
        val windowName = Regex("Window\\{[^}]*\\}")
        val windows = shell("dumpsys window windows").lines()
            .filter {
                (it.contains("Window #") && it.contains(appPackage)) ||
                    it.contains("ty=APPLICATION_OVERLAY")
            }
            .map { windowName.find(it)?.value ?: it.trim().take(80) }
        val texts = visibleTexts().filter { it.isNotBlank() }.distinct().take(15)
        val logs = shell("logcat -d -s UsageMonitor:V OverlayManager:V").lines()
            .filter { it.isNotBlank() }
            .takeLast(8)
            .map { it.trim().take(120) }

        // The usage of Settings, as the service sees it, and the last events behind it
        val tracker = UsageTracker(context)
        val now = System.currentTimeMillis()
        val usedSeconds = tracker.getTodayUsageForPackages(listOf(SETTINGS_PACKAGE)) / 1000
        val lastEvents = tracker.readForegroundEvents(now - 2 * 60 * 60 * 1000L, now)
            .filter { it.packageName == SETTINGS_PACKAGE }
            .takeLast(8)
            .map {
                val kind = if (it.isResumed) "RESUMED" else "END"
                "$kind ${it.className.substringAfterLast('.')} ${(now - it.timestamp) / 1000}s ago"
            }

        return "canDrawOverlays=${Settings.canDrawOverlays(context)}\n" +
            "windows of the app and overlay windows: $windows\n" +
            "texts on the screen: $texts\n" +
            "last logs of the service: $logs\n" +
            "usage of Settings now: $usedSeconds s\n" +
            "last events of Settings: $lastEvents"
    }

    // A limit is at least 1 minute, so Settings needs at least 1 minute of use today.
    // If it has less, we use it and wait (about one minute, only the first time).
    // Returns the minutes used today by Settings.
    private fun settingsUsedMinutesAtLeastOne(): Int {
        if (settingsUsedMinutes() < 1) {
            openApp(OPEN_SETTINGS)
            // The time of an open app counts until now, so we only wait
            assertTrue(
                "Settings did not reach 1 minute of use",
                waitUntil(120_000) { settingsUsedMinutes() >= 1 }
            )
        }
        // Back to the home screen: the next opening of Settings must be a new opening
        shell("input keyevent KEYCODE_HOME")
        Thread.sleep(2_000)
        closeSettings()
        return settingsUsedMinutes()
    }

    // Settings can open on an old screen of its back stack, for example the page where the
    // user gave the Usage Access permission. Android hides the overlays of other apps on the
    // permission pages (a security rule), so the overlay would be there but invisible.
    // We close Settings: the next time it opens on its home screen.
    private fun closeSettings() {
        shell("am force-stop $SETTINGS_PACKAGE")
        Thread.sleep(1_000)
    }

    private fun settingsUsedMinutes(): Int =
        (UsageTracker(context).getTodayUsageForPackages(listOf(SETTINGS_PACKAGE)) / 60_000L).toInt()

    // ---------- Helpers: database ----------

    // Creates a group with only the Settings app. Returns the id of the group.
    private fun createGroup(timeLimitMinutes: Int, limitType: LimitType = LimitType.HARD): Int =
        runBlocking {
            val repository = (context.applicationContext as StopScrollingApp).appGroupRepository
            repository.createGroup(
                name = TEST_GROUP_NAME,
                packageNames = listOf(SETTINGS_PACKAGE),
                timeLimit = timeLimitMinutes,
                limitType = limitType
            )
        }

    // Deletes all the groups (and their apps). Not allowed on the main thread.
    private fun clearGroups() {
        AppDatabase.getInstance(context).clearAllTables()
    }

    // ---------- Helpers: opening apps and events ----------

    // We start to listen BEFORE we open the app, otherwise we could miss the event.
    // Returns null if no event that matches arrives before the timeout.
    private fun openAppAndWaitForEvent(
        shellCommand: String,
        timeoutMs: Long,
        matches: (RegulatedAppOpened) -> Boolean
    ): RegulatedAppOpened? = runBlocking {
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeoutMs) {
                UsageMonitorEvents.regulatedAppOpened.first { matches(it) }
            }
        }

        openApp(shellCommand)

        result.await()
    }

    private fun openApp(shellCommand: String) {
        val output = shell(shellCommand)
        assertFalse("Could not open the app: $output", output.contains("Error"))
    }

    // ---------- Helpers: notification ----------

    private fun findServiceNotification(): Notification? {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications
            .map { it.notification }
            .firstOrNull {
                it.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == NOTIFICATION_TITLE
            }
    }

    private fun notificationText(): String? =
        findServiceNotification()?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    // ---------- Helpers: reading the screen ----------

    // Texts of the overlay, from the strings of the app, so the test follows the app
    private fun hardTitle() = context.getString(R.string.overlay_hard_title)
    private fun softTitle() = context.getString(R.string.overlay_soft_title)
    private fun extendButtonText() = context.resources.getQuantityString(
        R.plurals.overlay_soft_extend, EXTENSION_MINUTES, EXTENSION_MINUTES
    )

    // All the texts on the screen, overlay included, like a screen reader sees them.
    // We compare the whole text: "Soft limit reached" must not be mistaken for "Limit reached".
    private fun visibleTexts(): List<String> {
        val automation = instrumentation.uiAutomation
        val texts = mutableListOf<String>()
        collectTexts(automation.rootInActiveWindow, texts)
        for (window in automation.windows) {
            collectTexts(window.root, texts)
        }
        return texts
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return
        node.text?.let { texts.add(it.toString()) }
        node.contentDescription?.let { texts.add(it.toString()) }
        for (index in 0 until node.childCount) {
            collectTexts(node.getChild(index), texts)
        }
    }

    // Without this flag, uiAutomation.windows is empty and we could miss the overlay window
    private fun allowReadingAllWindows() {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo ?: return
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
    }

    // ---------- Helpers: shell and waiting ----------

    // Runs a shell command (like adb shell) and waits until it is finished
    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
            it.readBytes().decodeToString()
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    private fun isEmulator(): Boolean =
        Build.HARDWARE == "ranchu" ||
            Build.HARDWARE == "goldfish" ||
            Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.contains("emu") ||
            Build.PRODUCT.contains("sdk")

    companion object {
        private const val TEST_GROUP_NAME = "E2E test group"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val NO_LIMIT = 0 // 0 minutes = no limit

        // Same texts as in UsageMonitorService
        private const val NOTIFICATION_TITLE = "StopScrolling is active"
        private const val DEFAULT_TEXT = "Watching your app usage"

        // Commands to open other apps
        private const val OPEN_SETTINGS = "am start -a android.settings.SETTINGS"
        private const val OPEN_WIFI_SETTINGS = "am start -a android.settings.WIFI_SETTINGS"
        private const val OPEN_DIALER = "am start -a android.intent.action.DIAL"
    }
}
