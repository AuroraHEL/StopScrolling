package com.example.stopscrolling.ui.overlay

import androidx.compose.ui.test.assertIsDisplayed
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
class HardLockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsTheGroup_andTheGoHomeButtonCallsTheCallback() {
        var goHomeClicks = 0
        composeRule.setContent {
            StopScrollingTheme {
                HardLockScreen(
                    groupName = "Social Media",
                    limitMinutes = 90,
                    onGoHome = { goHomeClicks++ }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.overlay_hard_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Social Media", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.overlay_go_home)).performClick()

        assertEquals(1, goHomeClicks)
    }
}