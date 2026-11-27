package com.example.stopscrolling.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stopscrolling.R
import com.example.stopscrolling.permission.PermissionStatus
import com.example.stopscrolling.ui.onboarding.CapabilityOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // US-04: the user can change the permission in the Android settings,
    // so it is checked again every time this screen is shown
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val permission = viewModel.permissionToRequest()
        val activity = context.findActivity()

        // After a "no", Android shows no rationale only if the user chose "don't ask again"
        val shouldShowRationale = permission != null &&
            activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

        viewModel.onPermissionResult(granted, shouldShowRationale)
    }

    if (uiState.showPermissionExplanation) {
        WalkingPermissionDialog(
            onConfirm = {
                viewModel.dismissPermissionExplanation()
                viewModel.permissionToRequest()?.let { permissionLauncher.launch(it) }
            },
            onDismiss = viewModel::dismissPermissionExplanation
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.settings_challenges_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_challenges_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            CapabilityOption(
                title = stringResource(R.string.capability_can_walk),
                description = stringResource(R.string.capability_walk_description),
                checked = uiState.preferences.canWalk,
                enabled = true,
                onCheckedChange = viewModel::setCanWalk
            )

            // US-04: only when walking is on and the permission is missing
            if (uiState.preferences.canWalk) {
                WalkingPermissionNotice(
                    status = uiState.walkingPermission,
                    onAllow = viewModel::requestWalkingPermission,
                    onOpenAppSettings = { openAppSettings(context) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CapabilityOption(
                title = stringResource(R.string.capability_can_do_math),
                description = stringResource(R.string.capability_math_description),
                checked = uiState.preferences.canDoMentalArithmetic,
                enabled = true,
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

            if (uiState.hasError) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.settings_save_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }
        }
    }
}

/** Short explanation shown before the system permission question (US-04). */
@Composable
private fun WalkingPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_walking_dialog_title)) },
        text = { Text(stringResource(R.string.permission_walking_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.permission_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permission_not_now))
            }
        }
    )
}

/**
 * Tells the user why the walking challenge is not available and what to do (US-04).
 * Nothing is shown when the permission is granted or not needed.
 */
@Composable
private fun WalkingPermissionNotice(
    status: PermissionStatus,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    if (status == PermissionStatus.NOT_NEEDED || status == PermissionStatus.GRANTED) {
        return
    }

    val message = if (status == PermissionStatus.NOT_GRANTED) {
        R.string.permission_walking_needed
    } else {
        R.string.permission_walking_denied
    }

    val buttonText = when (status) {
        PermissionStatus.NOT_GRANTED -> R.string.permission_allow
        PermissionStatus.DENIED -> R.string.permission_try_again
        else -> R.string.permission_open_app_settings
    }

    // "Don't ask again": the system question cannot be shown, only the app settings can help
    val onClick = if (status == PermissionStatus.DENIED_PERMANENTLY) {
        onOpenAppSettings
    } else {
        onAllow
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(onClick = onClick) {
        Text(stringResource(buttonText))
    }
}

/** Opens the Android settings page of this app. */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings app on this phone: nothing else to do
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
