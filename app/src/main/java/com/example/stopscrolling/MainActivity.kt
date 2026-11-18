package com.example.stopscrolling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.stopscrolling.ui.navigation.AppNavHost
import com.example.stopscrolling.ui.theme.StopScrollingTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.stopscrolling.usage.UsageMonitorService

class MainActivity : ComponentActivity() {

    // Asks the user to allow notifications (needed from Android 13 to see the service notification)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()
        enableEdgeToEdge()
        setContent {
            StopScrollingTheme {
                AppNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start the monitoring service when Usage Access is granted.
        // Starting it again is safe: the service does not start a second loop.
        if (hasUsageStatsPermission(this)) {
            UsageMonitorService.start(this)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}