package com.example.stopscrolling.overlay

import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayManagerIntegrationTest {

    private val instrumentation =
        InstrumentationRegistry.getInstrumentation()

    @Test
    fun hide_withoutShowingOverlay_doesNotCrash() {
        instrumentation.runOnMainSync {
            val manager = OverlayManager(
                instrumentation.targetContext
            )

            manager.hide()
            manager.hide()
        }
    }

    @Test
    fun show_respectsOverlayPermission() {
        val manager = OverlayManager(
            instrumentation.targetContext
        )

        val permissionGranted = manager.canDrawOverlays()
        var shown = false

        instrumentation.runOnMainSync {
            try {
                shown = manager.show {
                    Text("Integration Test Overlay")
                }
            } finally {
                manager.hide()
            }
        }

        if (permissionGranted) {
            assertTrue(
                "Overlay should be shown when permission is granted",
                shown
            )
        } else {
            assertFalse(
                "Overlay must not be shown without permission",
                shown
            )
        }
    }

    @Test
    fun hide_afterShowingOverlay_doesNotCrash() {
        val manager = OverlayManager(
            instrumentation.targetContext
        )

        instrumentation.runOnMainSync {
            try {
                val shown = manager.show {
                    Text("Test Overlay")
                }

                if (shown) {
                    manager.hide()
                    manager.hide()
                }
            } finally {
                manager.hide()
            }
        }
    }
}
