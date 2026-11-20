package com.example.stopscrolling.usage

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
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
 * The overlay tests use the dialer, not Settings: Settings asks Android to hide the overlays
 * of the other apps (a protection against tapjacking), so no overlay can be seen over it.
 *
 * The extension of a soft limit (US-19) is tested too: the user solves the math quiz
 * on the overlay, and the service saves the extension and removes the overlay.
 *
 * Not tested here: the restart after a phone reboot (not implemented).
 *
 */
@RunWith(AndroidJUnit4::class)
class BackgroundServiceE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val appPackage = context.packageName

    private var scenario: ActivityScenario<MainActivity>? = null

    // The regulated app of the overlay tests (its package depends on the emulator image)
    private val dialerPackage: String by lazy { findDialerPackage() }

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
        // We do not leave a limit in the database: it would block an app later
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
        showOverlayForDialer(LimitType.HARD)

        assertTrue(hardTitle() in visibleTexts())
        assertFalse("The soft screen must not appear", softTitle() in visibleTexts())
    }

    @Test
    fun softLimitReached_showsTheSoftLimitOverlay() {
        showOverlayForDialer(LimitType.SOFT)

        assertTrue(softTitle() in visibleTexts())
        assertTrue(
            "The button to ask for more time is missing",
            extendButtonText() in visibleTexts()
        )
        assertFalse("The hard screen must not appear", hardTitle() in visibleTexts())
    }

    @Test
    fun appUnderTheLimit_showsNoOverlay() {
        val usedMinutes = dialerUsedMinutesAtLeastOne()
        val groupId = createGroup(
            timeLimitMinutes = usedMinutes + 60,
            limitType = LimitType.HARD,
            packageName = dialerPackage
        )

        val event = openAppAndWaitForEvent(OPEN_DIALER, 15_000) {
            it.packageName == dialerPackage && it.groupId == groupId
        }
        assertNotNull("No event for the dialer app", event)

        // The limits are checked right after the opening: we wait and then look at the screen
        Thread.sleep(5_000)
        assertFalse("An overlay appeared but the limit is not reached", hardTitle() in visibleTexts())
        assertFalse("An overlay appeared but the limit is not reached", softTitle() in visibleTexts())
    }

    @Test
    fun leavingTheBlockedApp_removesTheOverlay() {
        showOverlayForDialer(LimitType.HARD)

        // The user goes to the home screen (not a regulated app): the block must go away
        shell("input keyevent KEYCODE_HOME")

        assertTrue(
            "The overlay is still on the screen after leaving the blocked app",
            waitUntil(15_000) { hardTitle() !in visibleTexts() }
        )
    }

    // ---------- Extension of a soft limit with the math quiz (US-19) ----------

    @Test
    fun solvingTheMathQuiz_grantsTheExtension_andRemovesTheOverlay() {
        val groupId = showTheMathQuiz()
        assertEquals("No extension must exist before the quiz", 0, extraMinutesToday(groupId))

        // The user reads the problem on the screen, solves it and sends the answer
        typeAnswer(correctAnswerOnScreen())
        clickOnOverlay(submitText())

        assertTrue(
            "The overlay is still on the screen after the right answer",
            waitUntil(10_000) {
                val texts = visibleTexts()
                quizTitle() !in texts && softTitle() !in texts
            }
        )
        assertEquals(
            "The extension was not saved",
            EXTENSION_MINUTES,
            extraMinutesToday(groupId)
        )

        // The limit is now higher: the service must not block the app again
        Thread.sleep(5_000)
        assertFalse("The soft screen came back", softTitle() in visibleTexts())
        assertFalse("The quiz came back", quizTitle() in visibleTexts())
    }

    @Test
    fun wrongAnswerInTheMathQuiz_grantsNoExtension_andKeepsTheOverlay() {
        val groupId = showTheMathQuiz()

        typeAnswer(correctAnswerOnScreen() + 1)
        clickOnOverlay(submitText())

        // We give the app some time to react, then we look at the screen
        Thread.sleep(3_000)
        assertTrue("The quiz disappeared after a wrong answer", quizTitle() in visibleTexts())
        assertEquals("A wrong answer must not give minutes", 0, extraMinutesToday(groupId))
    }

    @Test
    fun cancellingTheMathQuiz_goesBackToTheSoftScreen_withoutExtension() {
        val groupId = showTheMathQuiz()

        clickOnOverlay(cancelText())

        assertTrue(
            "The soft screen did not come back after Cancel",
            waitUntil(5_000) {
                val texts = visibleTexts()
                softTitle() in texts && quizTitle() !in texts
            }
        )
        assertEquals("Cancel must not give minutes", 0, extraMinutesToday(groupId))
    }

    // ---------- Helpers: scenarios ----------

    // Opens the dialer when its limit is already reached and waits for the overlay of [type].
    // We do not use Settings here: it asks Android to hide the overlays of the other apps.
    // Returns the id of the group.
    private fun showOverlayForDialer(type: LimitType): Int {
        // The limit is the minutes already used: the limit is reached as soon as the dialer opens
        val usedMinutes = dialerUsedMinutesAtLeastOne()
        val groupId = createGroup(
            timeLimitMinutes = usedMinutes,
            limitType = type,
            packageName = dialerPackage
        )

        openApp(OPEN_DIALER)

        val expectedTitle = if (type == LimitType.HARD) hardTitle() else softTitle()
        val shown = waitUntil(20_000) { expectedTitle in visibleTexts() }
        if (!shown) {
            throw AssertionError(
                "The overlay \"$expectedTitle\" did not appear " +
                    "(limit: $usedMinutes min, type: $type)\n${overlayDiagnostics(dialerPackage)}"
            )
        }
        return groupId
    }

    // The soft limit is reached and the user asks for more time: waits for the math quiz.
    // Returns the id of the group.
    private fun showTheMathQuiz(): Int {
        val groupId = showOverlayForDialer(LimitType.SOFT)

        clickOnOverlay(extendButtonText())

        if (!waitUntil(10_000) { quizTitle() in visibleTexts() }) {
            throw AssertionError(
                "The math quiz did not appear after the tap on \"${extendButtonText()}\"\n" +
                    "${windowsDiagnostics()}\n" +
                    "warnings and errors of the app: ${appWarnings()}"
            )
        }
        return groupId
    }

    // Information for the error message when the overlay does not appear:
    // is the permission granted, which windows exist, what the screen shows, what the service logged
    private fun overlayDiagnostics(packageName: String): String {
        val windowName = Regex("Window\\{[^}]*\\}")
        val windows = shell("dumpsys window windows").lines()
            .filter {
                (it.contains("Window #") && it.contains(appPackage)) ||
                    it.contains("ty=APPLICATION_OVERLAY")
            }
            .map { windowName.find(it)?.value ?: it.trim().take(80) }
        val logs = shell("logcat -d -s UsageMonitor:V").lines()
            .filter { it.isNotBlank() }
            .takeLast(8)
            .map { it.trim().take(120) }

        // The usage of the app, as the service sees it, and the last events behind it
        val tracker = UsageTracker(context)
        val now = System.currentTimeMillis()
        val usedSeconds = tracker.getTodayUsageForPackages(listOf(packageName)) / 1000
        val lastEvents = tracker.readForegroundEvents(now - 2 * 60 * 60 * 1000L, now)
            .filter { it.packageName == packageName }
            .takeLast(8)
            .map {
                val kind = if (it.isResumed) "RESUMED" else "END"
                "$kind ${it.className.substringAfterLast('.')} ${(now - it.timestamp) / 1000}s ago"
            }

        return "canDrawOverlays=${Settings.canDrawOverlays(context)}\n" +
            "windows of the app and overlay windows: $windows\n" +
            "${windowsDiagnostics()}\n" +
            "last logs of the service: $logs\n" +
            "warnings and errors of the app: ${appWarnings()}\n" +
            "usage of $packageName now: $usedSeconds s\n" +
            "last events of $packageName: $lastEvents"
    }

    // Every window that the accessibility service can see: type, package, focus and some texts
    private fun windowsDiagnostics(): String {
        val windows = instrumentation.uiAutomation.windows.map { window ->
            val texts = mutableListOf<String>()
            collectTexts(window.root, texts)
            "type=${window.type} layer=${window.layer} active=${window.isActive} " +
                "focused=${window.isFocused} package=${window.root?.packageName} " +
                "texts=${texts.filter { it.isNotBlank() }.distinct().take(6)}"
        }
        return "accessibility windows:\n  " + windows.joinToString("\n  ") +
            "\noverlay window of the app (dumpsys): ${overlayWindowState()}"
    }

    // What Android knows about the overlay window of the app: visible, hidden by the system...
    private fun overlayWindowState(): String {
        val lines = shell("dumpsys window windows").lines()
        // The overlay has no title, so its name ends with the package
        val start = lines.indexOfFirst { it.contains("Window #") && it.contains("$appPackage}") }
        if (start < 0) return "no overlay window"

        val block = lines.drop(start + 1).takeWhile { !it.contains("Window #") }
        val interesting = Regex("hide|hidden|visib|surface|drawstate|onscreen|alpha", RegexOption.IGNORE_CASE)
        val state = block.filter { interesting.containsMatchIn(it) }
            .map { it.trim().take(300) }
            .take(16)
        return "$state\n  who hides the overlays: ${overlayHiders()}"
    }

    // Windows that ask Android to hide the overlays of the other apps, and related lines
    private fun overlayHiders(): String {
        val hiding = mutableListOf<String>()
        var currentWindow = ""
        for (line in shell("dumpsys window windows").lines()) {
            if (line.contains("Window #")) currentWindow = line.trim().take(100)
            val asksToHide = line.contains("HIDE_NON_SYSTEM_OVERLAY", ignoreCase = true) ||
                line.contains("HIDE_OVERLAY", ignoreCase = true)
            if (asksToHide && currentWindow !in hiding) hiding.add(currentWindow)
        }
        val related = shell("dumpsys window").lines()
            .filter { it.contains("NonSystemOverlay", ignoreCase = true) }
            .map { it.trim().take(250) }
            .take(5)
        return "windows with the hide flag=$hiding, other lines=$related"
    }

    // Warnings and errors written by the app (the test runs in the same process as the app)
    private fun appWarnings(): List<String> =
        shell("logcat -d --pid=${android.os.Process.myPid()} *:W").lines()
            .filter { it.isNotBlank() }
            .takeLast(10)
            .map { it.trim().take(160) }

    // A limit is at least 1 minute, so the dialer needs at least 1 minute of use today.
    // If it has less, we use it and wait (about one minute, only the first time).
    // Returns the minutes used today by the dialer.
    private fun dialerUsedMinutesAtLeastOne(): Int {
        if (usedMinutes(dialerPackage) < 1) {
            openApp(OPEN_DIALER)
            // The time of an open app counts until now, so we only wait
            assertTrue(
                "The dialer did not reach 1 minute of use",
                waitUntil(120_000) { usedMinutes(dialerPackage) >= 1 }
            )
        }
        // Back to the home screen: the next opening of the dialer must be a new opening
        shell("input keyevent KEYCODE_HOME")
        Thread.sleep(2_000)
        closeApp(dialerPackage)
        return usedMinutes(dialerPackage)
    }

    // An app can open on an old screen of its back stack. We close it,
    // so the next time it opens on its first screen.
    private fun closeApp(packageName: String) {
        shell("am force-stop $packageName")
        Thread.sleep(1_000)
    }

    private fun usedMinutes(packageName: String): Int =
        (UsageTracker(context).getTodayUsageForPackages(listOf(packageName)) / 60_000L).toInt()

    // The app that opens for the dial intent. Its package depends on the emulator image.
    private fun findDialerPackage(): String {
        val output = shell("cmd package resolve-activity --brief -a android.intent.action.DIAL")
        // The last line is "package/activity"
        val component = output.lines().map { it.trim() }.lastOrNull { it.contains("/") }
            ?: throw AssertionError("Cannot find the dialer app. Output: $output")
        return component.substringBefore("/")
    }

    // ---------- Helpers: database ----------

    // Creates a group with only one app (Settings if not said otherwise).
    // Returns the id of the group.
    private fun createGroup(
        timeLimitMinutes: Int,
        limitType: LimitType = LimitType.HARD,
        packageName: String = SETTINGS_PACKAGE
    ): Int =
        runBlocking {
            val repository = (context.applicationContext as StopScrollingApp).appGroupRepository
            repository.createGroup(
                name = TEST_GROUP_NAME,
                packageNames = listOf(packageName),
                timeLimit = timeLimitMinutes,
                limitType = limitType
            )
        }

    // Minutes of extension saved today for the group, read from the real database
    private fun extraMinutesToday(groupId: Int): Int = runBlocking {
        (context.applicationContext as StopScrollingApp).extensionRepository
            .extraMinutesToday(groupId)
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
    private fun quizTitle() = context.getString(R.string.math_quiz_title, EXTENSION_MINUTES)
    private fun submitText() = context.getString(R.string.submit)
    private fun cancelText() = context.getString(R.string.cancel)

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

    // The roots of the windows of our app (the overlay is one of them).
    // Settings, the keyboard and the other apps are not included.
    private fun ourWindowRoots(): List<AccessibilityNodeInfo> {
        val automation = instrumentation.uiAutomation
        val roots = mutableListOf<AccessibilityNodeInfo?>(automation.rootInActiveWindow)
        for (window in automation.windows) {
            roots.add(window.root)
        }
        return roots.filterNotNull().filter { it.packageName?.toString() == appPackage }
    }

    // The first node of our windows that matches [condition], or null
    private fun findOverlayNode(
        condition: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        for (root in ourWindowRoots()) {
            val node = findNode(root, condition)
            if (node != null) return node
        }
        return null
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        condition: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (condition(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findNode(child, condition)
            if (found != null) return found
        }
        return null
    }

    // Taps the button of the overlay with this text, like the user does
    private fun clickOnOverlay(text: String) {
        var node: AccessibilityNodeInfo = findOverlayNode {
            it.text?.toString() == text || it.contentDescription?.toString() == text
        } ?: throw AssertionError(
            "\"$text\" is not on the overlay\n${windowsDiagnostics()}"
        )

        // The text can be inside the button: we go up until we find something to click
        while (!node.isClickable) {
            node = node.parent ?: throw AssertionError("\"$text\" cannot be clicked")
        }
        assertTrue(
            "The tap on \"$text\" was refused",
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        )
    }

    // Writes the answer in the field of the quiz
    private fun typeAnswer(answer: Int) {
        val isField = { node: AccessibilityNodeInfo ->
            node.isEditable || node.className?.toString() == "android.widget.EditText"
        }
        val field = findOverlayNode(isField)
            ?: throw AssertionError(
                "The field for the answer is not on the overlay\n${windowsDiagnostics()}"
            )

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                answer.toString()
            )
        }
        assertTrue(
            "The answer could not be written",
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        )

        // We wait until the field shows the answer (at most 2 seconds)
        waitUntil(2_000) { findOverlayNode(isField)?.text?.toString() == answer.toString() }
    }

    // The quiz shows a problem like "12 + 7 = ?". We read it from the screen and solve it.
    private fun correctAnswerOnScreen(): Int {
        val problem = Regex("""(\d+) ([+\-*]) (\d+) = \?""")
        val match = visibleTexts().firstNotNullOfOrNull { problem.matchEntire(it.trim()) }
            ?: throw AssertionError("The math problem is not on the screen\n${windowsDiagnostics()}")

        val (first, operator, second) = match.destructured
        return when (operator) {
            "+" -> first.toInt() + second.toInt()
            "-" -> first.toInt() - second.toInt()
            else -> first.toInt() * second.toInt()
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
