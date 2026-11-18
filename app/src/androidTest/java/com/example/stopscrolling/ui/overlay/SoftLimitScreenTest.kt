package com.example.stopscrolling.ui.overlay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.R
import com.example.stopscrolling.ui.theme.StopScrollingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoftLimitScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun eachButtonCallsItsOwnCallback() {
        var homeClicks = 0
        var extensionClicks = 0
        composeRule.setContent {
            StopScrollingTheme {
                SoftLimitScreen(
                    groupName = "Social Media",
                    usedMinutes = 65,
                    limitMinutes = 60,
                    onRequestExtension = { extensionClicks++ },
                    onGoHome = { homeClicks++ }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.overlay_go_home)).performClick()
        val extendLabel = context.resources.getQuantityString(
            R.plurals.overlay_soft_extend, EXTENSION_MINUTES, EXTENSION_MINUTES
        )
        composeRule.onNodeWithText(extendLabel).performClick()

        assertEquals(1, homeClicks)
        assertEquals(1, extensionClicks)
    }
}