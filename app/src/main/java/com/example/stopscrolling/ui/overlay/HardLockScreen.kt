package com.example.stopscrolling.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stopscrolling.R
import com.example.stopscrolling.data.LimitKind
import com.example.stopscrolling.ui.theme.StopScrollingTheme

/**
 * Full-screen lock shown when a hard limit is reached (US-14).
 * It has no logic: the caller decides what "go home" does.
 */
@Composable
fun HardLockScreen(
    groupName: String,
    limitMinutes: Int,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    // US-13: says if the daily or the weekly limit was reached
    limitKind: LimitKind = LimitKind.DAILY
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(
                    if (limitKind == LimitKind.WEEKLY) {
                        R.string.overlay_hard_title_weekly
                    } else {
                        R.string.overlay_hard_title
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (limitKind == LimitKind.WEEKLY) {
                        R.string.overlay_hard_message_weekly
                    } else {
                        R.string.overlay_hard_message
                    },
                    formatDuration(limitMinutes),
                    groupName
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.overlay_go_home))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HardLockScreenPreview() {
    StopScrollingTheme {
        HardLockScreen(groupName = "Social Media", limitMinutes = 90, onGoHome = {})
    }
}