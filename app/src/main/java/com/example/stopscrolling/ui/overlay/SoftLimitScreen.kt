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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stopscrolling.R
import com.example.stopscrolling.ui.theme.StopScrollingTheme

/** Minutes granted by one extension (US-19 uses the same value). */
const val EXTENSION_MINUTES = 15

/**
 * Lock screen shown when a soft limit is reached (US-18).
 * [onRequestExtension] is called when the user wants more time: the caller
 * shows the challenge (US-19). [onGoHome] is the "stop here" choice.
 */
@Composable
fun SoftLimitScreen(
    groupName: String,
    usedMinutes: Int,
    limitMinutes: Int,
    onRequestExtension: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    extensionMinutes: Int = EXTENSION_MINUTES
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
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.overlay_soft_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.overlay_soft_message,
                    formatDuration(usedMinutes),
                    groupName,
                    formatDuration(limitMinutes)
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
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRequestExtension,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.overlay_soft_extend,
                        extensionMinutes,
                        extensionMinutes
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SoftLimitScreenPreview() {
    StopScrollingTheme {
        SoftLimitScreen(
            groupName = "Social Media",
            usedMinutes = 65,
            limitMinutes = 60,
            onRequestExtension = {},
            onGoHome = {}
        )
    }
}