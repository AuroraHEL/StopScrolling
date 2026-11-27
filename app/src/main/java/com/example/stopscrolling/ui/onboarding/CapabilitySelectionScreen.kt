package com.example.stopscrolling.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stopscrolling.R

@Composable
fun CapabilitySelectionScreen(
    viewModel: CapabilitySelectionViewModel,
    onCompleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onCompleted()
        }
    }

    if (uiState.isLoading || uiState.isCompleted) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.capability_selection_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.capability_selection_description),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        CapabilityOption(
            title = stringResource(R.string.capability_can_walk),
            description = stringResource(R.string.capability_walk_description),
            checked = uiState.preferences.canWalk,
            enabled = !uiState.isSaving,
            onCheckedChange = viewModel::setCanWalk
        )

        Spacer(modifier = Modifier.height(16.dp))

        CapabilityOption(
            title = stringResource(R.string.capability_can_do_math),
            description = stringResource(R.string.capability_math_description),
            checked = uiState.preferences.canDoMentalArithmetic,
            enabled = !uiState.isSaving,
            onCheckedChange = viewModel::setCanDoMentalArithmetic
        )

        if (uiState.showNoChallengeMessage) {
            Spacer(modifier = Modifier.height(16.dp))

            // Read by screen readers as soon as it appears
            Text(
                text = stringResource(R.string.capability_at_least_one),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.capability_change_later),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.hasError) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.capability_error),
                color = MaterialTheme.colorScheme.error
            )

            TextButton(
                onClick = viewModel::loadPreferences
            ) {
                Text(stringResource(R.string.capability_retry))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = viewModel::completeOnboarding,
            enabled = !uiState.isSaving && !uiState.hasError,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.capability_continue))
            }
        }
    }
}

/** One capability card with a switch. Also used by the Settings screen. */
@Composable
internal fun CapabilityOption(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

