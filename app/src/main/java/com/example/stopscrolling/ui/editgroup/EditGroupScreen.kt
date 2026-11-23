package com.example.stopscrolling.ui.editgroup

import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.stopscrolling.R
import com.example.stopscrolling.data.InstalledApp
import com.example.stopscrolling.data.LimitType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    groupId: Int,
    initialName: String,
    initialPackages: List<String>,
    initialTimeLimit: Int,
    initialLimitType: LimitType,
    availableApps: List<InstalledApp>,
    onBack: () -> Unit,
    onSave: (Int, String, List<String>, Int, LimitType) -> Unit
) {
    var name by remember(groupId) {
        mutableStateOf(initialName)
    }

    var selectedPackages by remember(groupId) {
        mutableStateOf(initialPackages.toSet())
    }

    var timeLimit by remember(groupId) {
        mutableIntStateOf(initialTimeLimit)
    }

    var limitType by remember(groupId) {
        mutableStateOf(initialLimitType)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Group") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    onSave(
                        groupId,
                        name.trim(),
                        selectedPackages.toList(),
                        timeLimit,
                        limitType
                    )
                },
                enabled = false, // Enable in US-09 Task 2
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.save))
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            // Group name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(stringResource(R.string.group_name))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Time limit dropdown
            Text(
                text = stringResource(R.string.daily_time_limit),
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = timeOptions.find {
                        it.first == timeLimit
                    }?.second ?: pluralStringResource(
                        R.plurals.minutes_count,
                        timeLimit,
                        timeLimit
                    ),
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text(stringResource(R.string.time_limit))
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
                    onDismissRequest = { expanded = false }
                ) {
                    timeOptions.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                timeLimit = minutes
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Hard / Soft limit
            Text(
                text = stringResource(R.string.limit_type),
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = limitType == LimitType.HARD,
                    onClick = { limitType = LimitType.HARD },
                    label = {
                        Text(stringResource(R.string.hard_limit))
                    }
                )

                FilterChip(
                    selected = limitType == LimitType.SOFT,
                    onClick = { limitType = LimitType.SOFT },
                    label = {
                        Text(stringResource(R.string.soft_limit))
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Installed apps
            Text(
                text = stringResource(R.string.select_apps),
                style = MaterialTheme.typography.titleSmall
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = availableApps,
                    key = { it.packageName }
                ) { app ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = app.packageName in selectedPackages,
                                role = Role.Checkbox,
                                onValueChange = {
                                    selectedPackages =
                                        if (app.packageName in selectedPackages) {
                                            selectedPackages - app.packageName
                                        } else {
                                            selectedPackages + app.packageName
                                        }
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context)
                            },
                            update = { imageView ->
                                imageView.setImageDrawable(app.icon)
                            },
                            modifier = Modifier.size(40.dp)
                        )

                        Spacer(Modifier.width(16.dp))

                        Text(
                            text = app.name,
                            modifier = Modifier.weight(1f)
                        )

                        Checkbox(
                            checked = app.packageName in selectedPackages,
                            onCheckedChange = null
                        )
                    }
                }
            }
        }
    }
}
