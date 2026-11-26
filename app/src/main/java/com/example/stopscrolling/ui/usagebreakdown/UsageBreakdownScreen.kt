package com.example.stopscrolling.ui.usagebreakdown

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.stopscrolling.R
import com.example.stopscrolling.ui.overlay.formatDuration
import com.example.stopscrolling.ui.theme.StopScrollingTheme
import com.example.stopscrolling.usage.AppUsageBreakdownItem
import kotlin.math.roundToInt

private const val MILLIS_PER_MINUTE = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageBreakdownScreen(
    state: UsageBreakdownUiState,
    onBack: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.usage_breakdown_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.isLoading -> {
                    LoadingBreakdownState()
                }

                !state.hasUsagePermission -> {
                    PermissionMissingState(onOpenUsageSettings = onOpenUsageSettings)
                }

                state.items.isEmpty() -> {
                    EmptyBreakdownState()
                }

                else -> {
                    UsageBreakdownList(
                        items = state.items,
                        totalDurationMillis = state.totalDurationMillis
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBreakdownState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.usage_breakdown_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionMissingState(
    onOpenUsageSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                        text = stringResource(R.string.usage_breakdown_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.usage_breakdown_permission_missing),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenUsageSettings,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.usage_breakdown_open_settings))
                }
            }
        }
    }
}

@Composable
private fun EmptyBreakdownState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.usage_breakdown_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.usage_breakdown_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UsageBreakdownList(
    items: List<AppUsageBreakdownItem>,
    totalDurationMillis: Long
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary_header") {
            TotalSummaryCard(
                totalDurationMillis = totalDurationMillis,
                appCount = items.size
            )
        }

        items(
            items = items,
            key = { it.packageName }
        ) { item ->
            AppUsageBreakdownRow(item = item)
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun TotalSummaryCard(
    totalDurationMillis: Long,
    appCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.usage_breakdown_total_label),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatUsageDurationMillis(totalDurationMillis),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.apps_count, appCount, appCount),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AppUsageBreakdownRow(
    item: AppUsageBreakdownItem
) {
    val clampedProportion = item.proportion.coerceIn(0f, 1f)
    val percent = (clampedProportion * 100f).roundToInt()
    val durationText = formatUsageDurationMillis(item.durationMillis)
    val rowDescription = stringResource(
        R.string.usage_breakdown_row_content_description,
        item.appName,
        durationText,
        percent
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconOrFallback(
            icon = item.icon,
            appName = item.appName
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { clampedProportion },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clearAndSetSemantics {},
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.usage_breakdown_percentage, percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppIconOrFallback(
    icon: Drawable?,
    appName: String
) {
    if (icon != null) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(icon)
            },
            modifier = Modifier.size(40.dp)
        )
    } else {
        val initial = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Surface(
            modifier = Modifier
                .size(40.dp)
                .clearAndSetSemantics {},
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun formatUsageDurationMillis(durationMillis: Long): String {
    if (durationMillis in 1L until MILLIS_PER_MINUTE) {
        return stringResource(R.string.duration_less_than_minute)
    }
    val totalMinutes = (durationMillis.coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt()
    return formatDuration(totalMinutes)
}

@Preview(showBackground = true)
@Composable
private fun UsageBreakdownScreenPreview() {
    StopScrollingTheme {
        UsageBreakdownScreen(
            state = UsageBreakdownUiState(
                items = listOf(
                    AppUsageBreakdownItem(
                        packageName = "com.google.android.youtube",
                        appName = "YouTube",
                        icon = null,
                        durationMillis = 75 * MILLIS_PER_MINUTE,
                        proportion = 0.56f
                    ),
                    AppUsageBreakdownItem(
                        packageName = "com.instagram.android",
                        appName = "Instagram",
                        icon = null,
                        durationMillis = 45 * MILLIS_PER_MINUTE,
                        proportion = 0.34f
                    ),
                    AppUsageBreakdownItem(
                        packageName = "com.spotify.music",
                        appName = "Spotify",
                        icon = null,
                        durationMillis = 14 * MILLIS_PER_MINUTE,
                        proportion = 0.10f
                    )
                ),
                totalDurationMillis = 134 * MILLIS_PER_MINUTE,
                isLoading = false,
                hasUsagePermission = true
            ),
            onBack = {},
            onOpenUsageSettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UsageBreakdownScreenLoadingPreview() {
    StopScrollingTheme {
        UsageBreakdownScreen(
            state = UsageBreakdownUiState(isLoading = true),
            onBack = {},
            onOpenUsageSettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UsageBreakdownScreenEmptyPreview() {
    StopScrollingTheme {
        UsageBreakdownScreen(
            state = UsageBreakdownUiState(
                items = emptyList(),
                isLoading = false,
                hasUsagePermission = true
            ),
            onBack = {},
            onOpenUsageSettings = {}
        )
    }
}