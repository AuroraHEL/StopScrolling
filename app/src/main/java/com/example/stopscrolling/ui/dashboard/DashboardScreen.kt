package com.example.stopscrolling.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stopscrolling.R
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.PendingChangeType
import com.example.stopscrolling.data.PendingGroupChange
import com.example.stopscrolling.data.PendingLimits
import com.example.stopscrolling.ui.theme.StopScrollingTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    groups: List<GroupWithApps>,
    onNewGroupClick: () -> Unit,

    // US-09: Pending deletions from the database.
    pendingChanges: List<PendingGroupChange> = emptyList(),

    // US-09: Navigate to Edit Group screen.
    onEditGroupClick: (Int) -> Unit = {},

    onUpdateLimit: (Int, Int, LimitType, () -> Unit) -> Unit =
        { _, _, _, _ -> },
    isUpdatingLimit: Boolean = false,
    limitUpdateError: Boolean = false,
    onClearLimitUpdateError: () -> Unit = {}
) {
    var editingGroup by remember {
        mutableStateOf<GroupWithApps?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.app_name))
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            Button(
                onClick = onNewGroupClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null
                )

                Spacer(Modifier.width(8.dp))

                Text(stringResource(R.string.new_group))
            }

            Spacer(Modifier.height(16.dp))

            if (groups.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_groups_yet),
                        textAlign = TextAlign.Center
                    )
                }

            } else {

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = groups,
                        key = { it.group.id }
                    ) { item ->

                        // US-09: Check for scheduled group deletion.
                        val isPendingDeletion = pendingChanges.any { change ->
                            change.groupId == item.group.id &&
                                    change.changeType ==
                                    PendingChangeType.DELETE_GROUP.name
                        }

                        GroupCard(
                            item = item,
                            isPendingDeletion = isPendingDeletion,
                            onEditLimitClick = {
                                onClearLimitUpdateError()
                                editingGroup = item
                            },
                            onEditGroupClick = {
                                onEditGroupClick(item.group.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // ---------- Edit Limit Dialog ----------

    editingGroup?.let { item ->

        EditLimitDialog(

            // US-15: Start with the latest pending edit if available.
            currentTimeLimit =
                item.group.pendingTimeLimit ?: item.group.timeLimit,

            currentLimitType =
                item.group.pendingLimitType ?: item.group.limitType,

            // US-15: Hard limit changes start at midnight.
            showNextDayNotice =
                PendingLimits.isUnderHardLimit(item.group),

            isSaving = isUpdatingLimit,

            hasError = limitUpdateError,

            onDismiss = {
                if (!isUpdatingLimit) {
                    editingGroup = null
                    onClearLimitUpdateError()
                }
            },

            onSave = { minutes, type ->
                onUpdateLimit(
                    item.group.id,
                    minutes,
                    type
                ) {
                    editingGroup = null
                }
            }
        )
    }
}

// ---------- Group Card ----------

@Composable
private fun GroupCard(
    item: GroupWithApps,
    isPendingDeletion: Boolean,
    onEditLimitClick: () -> Unit,
    onEditGroupClick: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = item.group.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = pluralStringResource(
                    R.plurals.apps_count,
                    item.apps.size,
                    item.apps.size
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))

            // Current daily limit.
            Text(
                text = limitSummary(
                    minutes = item.group.timeLimit,
                    limitType = item.group.limitType,
                    withTypeRes = R.string.daily_limit_with_type,
                    noLimitRes = R.string.daily_limit_no_limit
                ),
                style = MaterialTheme.typography.bodySmall
            )

            // US-15: Show pending limit changes.
            val pendingMinutes = item.group.pendingTimeLimit
            val pendingType = item.group.pendingLimitType

            if (pendingMinutes != null && pendingType != null) {

                Spacer(Modifier.height(4.dp))

                Text(
                    text = limitSummary(
                        minutes = pendingMinutes,
                        limitType = pendingType,
                        withTypeRes = R.string.pending_limit_with_type,
                        noLimitRes = R.string.pending_limit_no_limit
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = stringResource(
                        R.string.pending_takes_effect
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))

            // US-09: Show pending deletion status.
            if (isPendingDeletion) {

                Text(
                    text = "Will be deleted tomorrow at 00:00",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(8.dp))
            }

            // Edit Limit and Edit Group buttons.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = onEditLimitClick,
                    enabled = !isPendingDeletion
                ) {
                    Text(stringResource(R.string.edit_limit))
                }

                OutlinedButton(
                    onClick = onEditGroupClick,
                    enabled = !isPendingDeletion
                ) {
                    Text("Edit Group")
                }
            }
        }
    }
}

// ---------- Edit Limit Dialog ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLimitDialog(
    currentTimeLimit: Int,
    currentLimitType: LimitType,
    showNextDayNotice: Boolean,
    isSaving: Boolean,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, LimitType) -> Unit
) {

    var selectedMinutes by remember(
        currentTimeLimit,
        currentLimitType
    ) {
        mutableStateOf(currentTimeLimit)
    }

    var selectedType by remember(
        currentTimeLimit,
        currentLimitType
    ) {
        mutableStateOf(currentLimitType)
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    val timeOptions = listOf(
        0 to stringResource(R.string.no_limit),
        1 to pluralStringResource(R.plurals.minutes_count, 1, 1),
        2 to pluralStringResource(R.plurals.minutes_count, 2, 2),
        3 to pluralStringResource(R.plurals.minutes_count, 3, 3),
        5 to pluralStringResource(R.plurals.minutes_count, 5, 5),
        10 to pluralStringResource(R.plurals.minutes_count, 10, 10),
        15 to pluralStringResource(R.plurals.minutes_count, 15, 15),
        30 to pluralStringResource(R.plurals.minutes_count, 30, 30),
        45 to pluralStringResource(R.plurals.minutes_count, 45, 45),
        60 to stringResource(R.string.one_hour),
        90 to stringResource(R.string.one_hour_thirty_minutes),
        120 to stringResource(R.string.two_hours),
        180 to stringResource(R.string.three_hours),
        240 to stringResource(R.string.four_hours),
        300 to stringResource(R.string.five_hours),
        360 to stringResource(R.string.six_hours),
        480 to stringResource(R.string.eight_hours),
        600 to stringResource(R.string.ten_hours)
    )

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text(stringResource(R.string.edit_time_limit))
        },

        text = {

            Column {

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        if (!isSaving) {
                            expanded = it
                        }
                    }
                ) {

                    OutlinedTextField(
                        value = timeOptions.find {
                            it.first == selectedMinutes
                        }?.second ?: pluralStringResource(
                            R.plurals.minutes_count,
                            selectedMinutes,
                            selectedMinutes
                        ),
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSaving,
                        label = {
                            Text(
                                stringResource(
                                    R.string.daily_time_limit
                                )
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        modifier = Modifier
                            .menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryNotEditable
                            )
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {

                        timeOptions.forEach { (minutes, label) ->

                            DropdownMenuItem(
                                text = {
                                    Text(label)
                                },
                                onClick = {
                                    selectedMinutes = minutes
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.limit_type),
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    FilterChip(
                        selected = selectedType == LimitType.HARD,
                        onClick = {
                            selectedType = LimitType.HARD
                        },
                        enabled = !isSaving,
                        label = {
                            Text(stringResource(R.string.hard_limit))
                        }
                    )

                    FilterChip(
                        selected = selectedType == LimitType.SOFT,
                        onClick = {
                            selectedType = LimitType.SOFT
                        },
                        enabled = !isSaving,
                        label = {
                            Text(stringResource(R.string.soft_limit))
                        }
                    )
                }

                // US-15: Inform users about delayed hard limit edits.
                if (showNextDayNotice) {

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(
                            R.string.hard_limit_change_notice
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (hasError) {

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(
                            R.string.limit_update_error
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick = {
                    onSave(selectedMinutes, selectedType)
                },
                enabled = !isSaving
            ) {
                Text(
                    if (isSaving) {
                        stringResource(R.string.saving)
                    } else {
                        stringResource(R.string.save)
                    }
                )
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ---------- Limit Label ----------

@Composable
private fun limitLabel(minutes: Int): String {

    return when (minutes) {

        0 -> stringResource(R.string.no_limit)
        60 -> stringResource(R.string.one_hour)
        90 -> stringResource(R.string.one_hour_thirty_minutes)
        120 -> stringResource(R.string.two_hours)
        180 -> stringResource(R.string.three_hours)
        240 -> stringResource(R.string.four_hours)
        300 -> stringResource(R.string.five_hours)
        360 -> stringResource(R.string.six_hours)
        480 -> stringResource(R.string.eight_hours)
        600 -> stringResource(R.string.ten_hours)

        else -> pluralStringResource(
            R.plurals.minutes_count,
            minutes,
            minutes
        )
    }
}

// ---------- Limit Summary ----------

@Composable
private fun limitSummary(
    minutes: Int,
    limitType: LimitType,
    @StringRes withTypeRes: Int,
    @StringRes noLimitRes: Int
): String {

    return if (minutes == 0) {

        stringResource(noLimitRes)

    } else {

        val typeText =
            if (limitType == LimitType.HARD) {
                stringResource(R.string.hard)
            } else {
                stringResource(R.string.soft)
            }

        stringResource(
            withTypeRes,
            limitLabel(minutes),
            typeText
        )
    }
}

// ---------- Preview ----------

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {

    StopScrollingTheme {

        DashboardScreen(
            groups = emptyList(),
            onNewGroupClick = {}
        )
    }
}
