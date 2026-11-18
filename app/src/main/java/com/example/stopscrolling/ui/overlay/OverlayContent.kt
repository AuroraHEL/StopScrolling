package com.example.stopscrolling.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.stopscrolling.R
import com.example.stopscrolling.data.LimitType

/** What the overlay must show. */
data class OverlayRequest(
    val groupId: Int,
    val groupName: String,
    val limitType: LimitType,
    val usedMinutes: Int,
    val limitMinutes: Int
)

/**
 * Picks the right screen: hard lock, or soft limit screen with the extension challenge.
 * [onExtensionGranted] must be called by the challenge once the extension is saved (US-19).
 */
@Composable
fun OverlayContent(
    request: OverlayRequest,
    onGoHome: () -> Unit,
    onExtensionGranted: () -> Unit,
    modifier: Modifier = Modifier,
    // Malak replaces this default with the math quiz (US-19)
    challengeContent: @Composable (onSolved: () -> Unit, onCancel: () -> Unit) -> Unit =
        { _, onCancel -> ChallengePlaceholder(onCancel) }
) {
    when (request.limitType) {
        LimitType.HARD -> HardLockScreen(
            groupName = request.groupName,
            limitMinutes = request.limitMinutes,
            onGoHome = onGoHome,
            modifier = modifier
        )
        LimitType.SOFT -> {
            var showChallenge by remember(request.groupId) { mutableStateOf(false) }
            if (showChallenge) {
                challengeContent(onExtensionGranted, { showChallenge = false })
            } else {
                SoftLimitScreen(
                    groupName = request.groupName,
                    usedMinutes = request.usedMinutes,
                    limitMinutes = request.limitMinutes,
                    onRequestExtension = { showChallenge = true },
                    onGoHome = onGoHome,
                    modifier = modifier
                )
            }
        }
    }
}

/** TEMPORARY screen shown until the math challenge (US-19) exists. */
@Composable
private fun ChallengePlaceholder(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.overlay_challenge_placeholder),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCancel) { Text(stringResource(R.string.back)) }
        }
    }
}