package com.example.stopscrolling.ui.newgroup

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stopscrolling.R
import com.example.stopscrolling.data.InstalledApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    viewModel: NewGroupViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    // US-12/17: Time limit options
    var expanded by remember { mutableStateOf(false) }

    val timeOptions = listOf(
        0 to "No limit",
        15 to "15 minutes",
        30 to "30 minutes",
        60 to "1 hour",
        120 to "2 hours",
        180 to "3 hours"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.new_group))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = viewModel::onSaveClick,
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

            // Group Name
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = {
                    Text(stringResource(R.string.group_name))
                },
                singleLine = true,
                isError = state.nameError,
                supportingText = {
                    if (state.nameError) {
                        Text(
                            stringResource(
                                R.string.group_name_error
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // US-12/17: Time Limit Picker
            Text(
                text = "Daily Time Limit",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = timeOptions.find {
                        it.first == state.timeLimit
                    }?.second
                        ?: "${state.timeLimit} minutes",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time Limit") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = expanded
                        )
                    },
                    modifier = Modifier
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType
                                .PrimaryNotEditable
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
                            text = { Text(label) },
                            onClick = {
                                viewModel.onTimeLimitChange(minutes)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // US-12/17: Hard / Soft Limit
            Text(
                text = "Limit Type",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.limitType == "HARD",
                    onClick = {
                        viewModel.onLimitTypeChange("HARD")
                    },
                    label = { Text("Hard Limit") }
                )

                FilterChip(
                    selected = state.limitType == "SOFT",
                    onClick = {
                        viewModel.onLimitTypeChange("SOFT")
                    },
                    label = { Text("Soft Limit") }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Select Apps
            Text(
                text = stringResource(R.string.select_apps),
                style = MaterialTheme.typography.titleSmall
            )

            if (state.appsError) {
                Text(
                    text = stringResource(
                        R.string.select_apps_error
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        state.apps,
                        key = { it.packageName }
                    ) { app ->
                        AppCheckRow(
                            app = app,
                            checked = app.packageName
                                    in state.selectedPackages,
                            onToggle = {
                                viewModel.onAppToggle(
                                    app.packageName
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCheckRow(
    app: InstalledApp,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
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
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
    }
}
