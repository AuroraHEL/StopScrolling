package com.example.stopscrolling

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign

@Composable
fun PermissionScreen(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasRequested by remember { mutableStateOf(false) }
    var showDeniedMessage by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasUsageStatsPermission(context)) {
                    showDeniedMessage = false

                    onPermissionGranted()
                } else if (hasRequested) {
                    showDeniedMessage = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "StopScrolling needs Usage Access to track your screen time and enforce limits.",
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            hasRequested = true
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            context.startActivity(intent)
        }) {
            Text(text = "Grant Permission")
        }

        if (showDeniedMessage) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Permission required. Please find StopScrolling in the list and allow access.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}