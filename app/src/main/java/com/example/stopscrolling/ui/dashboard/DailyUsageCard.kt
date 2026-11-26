package com.example.stopscrolling.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stopscrolling.R
import com.example.stopscrolling.ui.overlay.formatDuration
import com.example.stopscrolling.ui.theme.StopScrollingTheme

/**
 * Dashboard card displaying today's total phone usage (US-27),
 * or a revoked-permission fallback with a Settings button.
 */
@Composable
fun DailyUsageCard(
    state: DailyUsageUiState,
    onOpenUsageSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (!state.hasUsagePermission) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.daily_usage_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.daily_usage_permission_missing),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenUsageSettings,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.daily_usage_open_settings))
                }
            }
        }
    } else {
        val cardColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                DailyUsageCardContent(state = state)
            }
        } else {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = cardColors
            ) {
                DailyUsageCardContent(state = state)
            }
        }
    }
}

@Composable
private fun DailyUsageCardContent(state: DailyUsageUiState) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.daily_usage_card_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (state.isLoading) {
                stringResource(R.string.daily_usage_loading)
            } else {
                formatDuration(state.totalMinutes)
            },
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.daily_usage_card_subtitle),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyUsageCardPreview() {
    StopScrollingTheme {
        DailyUsageCard(
            state = DailyUsageUiState(totalMinutes = 134, isLoading = false),
            onOpenUsageSettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyUsageCardPermissionRevokedPreview() {
    StopScrollingTheme {
        DailyUsageCard(
            state = DailyUsageUiState(isLoading = false, hasUsagePermission = false),
            onOpenUsageSettings = {}
        )
    }
}