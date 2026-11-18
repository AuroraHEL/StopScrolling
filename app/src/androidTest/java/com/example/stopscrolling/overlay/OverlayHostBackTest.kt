package com.example.stopscrolling.overlay

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayHostBackTest {

    @Test
    fun backKey_isConsumedByTheOverlay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var downConsumed = false
        var upConsumed = false

        // OverlayHost uses a LifecycleRegistry: it must run on the main thread
        instrumentation.runOnMainSync {
            val host = OverlayHost(instrumentation.targetContext)
            downConsumed = host.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
            )
            upConsumed = host.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
            )
            host.destroy()
        }

        assertTrue("Back key down must be consumed", downConsumed)
        assertTrue("Back key up must be consumed", upConsumed)
    }
}
