package com.example.stopscrolling.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppDetectorTest {

    private fun resumed(packageName: String, time: Long) = ForegroundEvent(packageName, time, true)
    private fun paused(packageName: String, time: Long) = ForegroundEvent(packageName, time, false)

    @Test
    fun firstApp_isOpened() {
        val detector = ForegroundAppDetector()

        val result = detector.process(listOf(resumed("app.a", 1_000)))

        assertEquals(listOf("app.a"), result)
    }

    @Test
    fun sameApp_resumedAgain_isNotOpenedAgain() {
        // Example: the user goes to another screen of the same app
        val detector = ForegroundAppDetector()

        val result = detector.process(
            listOf(
                resumed("app.a", 1_000),
                paused("app.a", 2_000),
                resumed("app.a", 2_100)
            )
        )

        assertEquals(listOf("app.a"), result)
    }

    @Test
    fun switchingApps_returnsEveryOpenedApp_inOrder() {
        val detector = ForegroundAppDetector()

        val result = detector.process(
            listOf(
                resumed("app.a", 1_000),
                resumed("app.b", 2_000),
                resumed("app.a", 3_000)
            )
        )

        assertEquals(listOf("app.a", "app.b", "app.a"), result)
    }

    @Test
    fun backgroundEvents_areIgnored() {
        val detector = ForegroundAppDetector()

        val result = detector.process(listOf(paused("app.a", 1_000)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun lastApp_isRememberedBetweenCalls() {
        val detector = ForegroundAppDetector()

        detector.process(listOf(resumed("app.a", 1_000)))
        val sameApp = detector.process(listOf(resumed("app.a", 2_000)))
        val otherApp = detector.process(listOf(resumed("app.b", 3_000)))

        assertTrue(sameApp.isEmpty())
        assertEquals(listOf("app.b"), otherApp)
    }
}