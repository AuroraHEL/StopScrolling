package com.example.stopscrolling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.stopscrolling.ui.theme.StopScrollingTheme

// Spike SP-01: test screen. It only shows what Android lets us read.
class MainActivity : ComponentActivity() {

    // This number changes every time the user comes back to the app
    // (for example from the settings or from another app).
    // When it changes, the screen reads the data again.
    private var refreshCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StopScrollingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpikeScreen(
                        refreshCount = refreshCount,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCount++
    }
}

@Composable
fun SpikeScreen(refreshCount: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // "remember(refreshCount)" runs the code again when refreshCount changes
    val hasAccess = remember(refreshCount) { hasUsageAccess(context) }
    val todayUsage = remember(refreshCount) {
        if (hasAccess) getTodayUsage(context) else emptyList()
    }
    val foregroundApps = remember(refreshCount) {
        if (hasAccess) getRecentForegroundApps(context) else emptyList()
    }
    val launchableApps = remember(refreshCount) { getLaunchableApps(context) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "SP-01 spike: usage tracking test",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Test 1: permission
        Text(
            text = "1. Usage Access permission",
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = if (hasAccess) "Granted" else "NOT granted")
        if (!hasAccess) {
            Button(onClick = { openUsageAccessSettings(context) }) {
                Text(text = "Open Usage Access settings")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Test 2: usage time of today
        Text(
            text = "2. Apps used the most today",
            style = MaterialTheme.typography.titleMedium
        )
        if (todayUsage.isEmpty()) {
            Text(text = "No data (permission missing or no usage yet)")
        }
        todayUsage.forEach { app ->
            Text(text = "${app.label}: ${app.seconds / 60} min ${app.seconds % 60} s")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Test 3: foreground app (newest first)
        Text(
            text = "3. Apps that came to the foreground (last 10 minutes)",
            style = MaterialTheme.typography.titleMedium
        )
        if (foregroundApps.isEmpty()) {
            Text(text = "No events")
        }
        foregroundApps.forEach { line ->
            Text(text = line)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Test 4: installed apps that the user can open
        Text(
            text = "4. Installed apps that we can see",
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = "Found ${launchableApps.size} apps")
        Text(text = launchableApps.take(10).joinToString(", "))
    }
}
