package com.example.stopscrolling.ui.overlay

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.R
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.ui.theme.StopScrollingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayContentIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun request(type: LimitType) = OverlayRequest(
        groupId = 1,
        groupName = "Social Media",
        limitType = type,
        usedMinutes = 65,
        limitMinutes = 60
    )

    @Test
    fun hardLimit_showsHardLockInsteadOfSoftExtension() {
        composeRule.setContent {
            StopScrollingTheme {
                OverlayContent(
                    request = request(LimitType.HARD),
                    onGoHome = {},
                    onExtensionGranted = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.overlay_hard_title)
        ).assertIsDisplayed()

        val extendLabel = context.resources.getQuantityString(
            R.plurals.overlay_soft_extend,
            EXTENSION_MINUTES,
            EXTENSION_MINUTES
        )

        composeRule.onNodeWithText(extendLabel)
            .assertDoesNotExist()
    }

    @Test
    fun softLimit_requestExtension_opensChallenge() {
        composeRule.setContent {
            StopScrollingTheme {
                OverlayContent(
                    request = request(LimitType.SOFT),
                    onGoHome = {},
                    onExtensionGranted = {},
                    challengeContent = { _, _, _ ->
                        Text("Test Math Challenge")
                    }
                )
            }
        }

        val extendLabel = context.resources.getQuantityString(
            R.plurals.overlay_soft_extend,
            EXTENSION_MINUTES,
            EXTENSION_MINUTES
        )

        composeRule.onNodeWithText(extendLabel)
            .performClick()

        composeRule.onNodeWithText("Test Math Challenge")
            .assertIsDisplayed()
    }

    @Test
    fun softLimit_cancelChallenge_returnsToSoftLimit() {
        composeRule.setContent {
            StopScrollingTheme {
                OverlayContent(
                    request = request(LimitType.SOFT),
                    onGoHome = {},
                    onExtensionGranted = {},
                    challengeContent = { _, _, onCancel ->
                        Button(onClick = onCancel) {
                            Text("Cancel Test Challenge")
                        }
                    }
                )
            }
        }

        val extendLabel = context.resources.getQuantityString(
            R.plurals.overlay_soft_extend,
            EXTENSION_MINUTES,
            EXTENSION_MINUTES
        )

        composeRule.onNodeWithText(extendLabel)
            .performClick()

        composeRule.onNodeWithText("Cancel Test Challenge")
            .performClick()

        composeRule.onNodeWithText(extendLabel)
            .assertIsDisplayed()
    }

    @Test
    fun softLimit_solvedChallenge_callsExtensionGranted() {
        var extensionGranted = 0

        composeRule.setContent {
            StopScrollingTheme {
                OverlayContent(
                    request = request(LimitType.SOFT),
                    onGoHome = {},
                    onExtensionGranted = {
                        extensionGranted++
                    },
                    challengeContent = { _, onSolved, _ ->
                        Button(onClick = onSolved) {
                            Text("Solve Test Challenge")
                        }
                    }
                )
            }
        }

        val extendLabel = context.resources.getQuantityString(
            R.plurals.overlay_soft_extend,
            EXTENSION_MINUTES,
            EXTENSION_MINUTES
        )

        composeRule.onNodeWithText(extendLabel)
            .performClick()

        composeRule.onNodeWithText("Solve Test Challenge")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, extensionGranted)
        }
    }
}
