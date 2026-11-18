package com.example.stopscrolling.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCalculatorTest {

    // A fake "day" that goes from 0 to 100 seconds (values are in milliseconds)
    private val dayStart = 0L
    private val now = 100_000L

    @Test
    fun oneSession_isCounted() {
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 25_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(15_000L, result["app.a"])
    }

    @Test
    fun twoSessionsOfTheSameApp_areSummed() {
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 20_000, false),
            ForegroundEvent("app.a", 50_000, true),
            ForegroundEvent("app.a", 60_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(20_000L, result["app.a"])
    }

    @Test
    fun differentApps_areCountedSeparately() {
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 20_000, false),
            ForegroundEvent("app.b", 20_000, true),
            ForegroundEvent("app.b", 50_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
        assertEquals(30_000L, result["app.b"])
    }

    @Test
    fun appStillInForeground_isCountedUntilNow() {
        val events = listOf(ForegroundEvent("app.a", 90_000, true))

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
    }

    @Test
    fun appOpenedBeforeMidnight_isCountedFromStartOfDay() {
        // The app came to the foreground 5 s before the start of the day
        // and went to the background 5 s after it: only the 5 s of today are counted
        val events = listOf(
            ForegroundEvent("app.a", -5_000L, true),
            ForegroundEvent("app.a", 5_000L, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(5_000L, result["app.a"])
    }

    @Test
    fun backgroundEventWithoutForegroundEvent_isIgnored() {
        // We do not know when the app was opened, so nothing is counted
        val events = listOf(ForegroundEvent("app.a", 5_000, false))

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertTrue(result.isEmpty())
    }

    @Test
    fun sessionBeforeTheStartOfTheDay_isNotCounted() {
        val events = listOf(
            ForegroundEvent("app.a", -20_000L, true),
            ForegroundEvent("app.a", -10_000L, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertTrue(result.isEmpty())
    }

    @Test
    fun strayBackgroundEvent_isIgnored() {
        // The second "background" event has no matching "foreground" event
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 20_000, false),
            ForegroundEvent("app.a", 30_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
    }

    @Test
    fun twoForegroundEventsInARow_restartTheSession() {
        // The first session has no end event: it is not counted,
        // and the time is counted again from the second "foreground" event
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true),
            ForegroundEvent("app.a", 50_000, true),
            ForegroundEvent("app.a", 60_000, false)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
    }

    @Test
    fun movingToAnotherScreenOfTheSameApp_keepsCountingUntilTheLastPause() {
        // Real order of the Android events when the user goes from screen A to screen B:
        // PAUSED(A), RESUMED(B), STOPPED(A), PAUSED(B).
        // The STOPPED(A) is late and must not end the session of screen B.
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true, "ScreenA"),
            ForegroundEvent("app.a", 20_000, false, "ScreenA"), // PAUSED(A)
            ForegroundEvent("app.a", 20_100, true, "ScreenB"),  // RESUMED(B)
            ForegroundEvent("app.a", 20_200, false, "ScreenA"), // STOPPED(A)
            ForegroundEvent("app.a", 30_000, false, "ScreenB")  // PAUSED(B)
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        // 10 s on screen A + 9.9 s on screen B
        assertEquals(19_900L, result["app.a"])
    }

    @Test
    fun stoppedWithoutPaused_endsTheSession() {
        // Seen in the events of our own app on the emulator: "foreground" and then "stopped", with no "paused"
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true, "ScreenA"),
            ForegroundEvent("app.a", 25_000, false, "ScreenA")
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(15_000L, result["app.a"])
    }

    @Test
    fun pausedAndThenStopped_isCountedOnlyOnce() {
        val events = listOf(
            ForegroundEvent("app.a", 10_000, true, "ScreenA"),
            ForegroundEvent("app.a", 20_000, false, "ScreenA"), // PAUSED
            ForegroundEvent("app.a", 20_500, false, "ScreenA")  // STOPPED
        )

        val result = UsageCalculator.sumForegroundTime(events, dayStart, now)

        assertEquals(10_000L, result["app.a"])
    }

    @Test
    fun noEvents_giveEmptyResult() {
        val result = UsageCalculator.sumForegroundTime(emptyList(), dayStart, now)

        assertTrue(result.isEmpty())
    }
}