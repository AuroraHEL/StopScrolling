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
import com.example.stopscrolling.challenge.ChallengeType
import com.example.stopscrolling.data.LimitKind
import com.example.stopscrolling.data.LimitType

/** What the overlay must show. */
data class OverlayRequest(
    val groupId: Int,
    val groupName: String,
    val limitType: LimitType,
    val usedMinutes: Int,
    val limitMinutes: Int,
    // US-13: which limit was reached. [usedMinutes] and [limitMinutes] belong to this limit.
    val limitKind: LimitKind = LimitKind.DAILY
)

/**
 * Picks the right screen: hard lock, or soft limit screen with the extension challenge.
 * [onExtensionGranted] must be called by the challenge once the extension is saved (US-19).
 * [availableChallenges] comes from the ChallengeRegistry (US-03): with one challenge it
 * starts at once, with more the user chooses, with none a message is shown.
 */
@Composable
fun OverlayContent(
    request: OverlayRequest,
    onGoHome: () -> Unit,
    onExtensionGranted: () -> Unit,
    modifier: Modifier = Modifier,
    availableChallenges: List<ChallengeType> = listOf(ChallengeType.MATH),
    // The caller draws the screen of each challenge (math quiz, later the walking one)
    challengeContent: @Composable (
        type: ChallengeType,
        onSolved: () -> Unit,
        onCancel: () -> Unit
    ) -> Unit = { _, _, onCancel -> ChallengePlaceholder(onCancel) }
) {
    when (request.limitType) {
        LimitType.HARD -> HardLockScreen(
            groupName = request.groupName,
            limitMinutes = request.limitMinutes,
            onGoHome = onGoHome,
            modifier = modifier,
            limitKind = request.limitKind
        )
        LimitType.SOFT -> {
            var showChallenge by remember(request.groupId, request.limitKind) { mutableStateOf(false) }
            if (showChallenge) {
                ChallengeFlow(
                    availableChallenges = availableChallenges,
                    onSolved = onExtensionGranted,
                    onCancel = { showChallenge = false },
                    challengeContent = challengeContent
                )
            } else {
                SoftLimitScreen(
                    groupName = request.groupName,
                    usedMinutes = request.usedMinutes,
                    limitMinutes = request.limitMinutes,
                    onRequestExtension = { showChallenge = true },
                    onGoHome = onGoHome,
                    modifier = modifier,
                    limitKind = request.limitKind
                )
            }
        }
    }
}

/**
 * What happens after "get more time" (US-03):
 * no challenge -> message, one -> it starts at once, several -> the user chooses.
 */
@Composable
private fun ChallengeFlow(
    availableChallenges: List<ChallengeType>,
    onSolved: () -> Unit,
    onCancel: () -> Unit,
    challengeContent: @Composable (
        type: ChallengeType,
        onSolved: () -> Unit,
        onCancel: () -> Unit
    ) -> Unit
) {
    var chosen by remember { mutableStateOf<ChallengeType?>(null) }
    val current = chosen

    when {
        availableChallenges.isEmpty() -> NoChallengeAvailableScreen(onBack = onCancel)

        availableChallenges.size == 1 ->
            challengeContent(availableChallenges.first(), onSolved, onCancel)

        current == null -> ChallengeChoiceScreen(
            challenges = availableChallenges,
            onChoose = { chosen = it },
            onCancel = onCancel
        )

        // Cancelling a challenge returns to the choice
        else -> challengeContent(current, onSolved, { chosen = null })
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