package com.example.stopscrolling.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stopscrolling.ui.theme.StopScrollingTheme
import kotlinx.coroutines.launch

/*
 * SP-02 (spike): test screen for the Bluetooth message between two phones.
 * It has its own icon on the home screen ("BT Spike"), so the main app is not changed.
 * This is test code on the branch spike/SP-02-bluetooth, it is not merged into main.
 */
class BluetoothSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StopScrollingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BluetoothSpikeScreen()
                }
            }
        }
    }
}

// The permission is checked with [BtSpikePermissions] before each call
@SuppressLint("MissingPermission")
@Composable
private fun BluetoothSpikeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    var permissionsGranted by remember { mutableStateOf(BtSpikePermissions.hasBluetooth(context)) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var partner by remember { mutableStateOf<BluetoothDevice?>(null) }
    var message by remember { mutableStateOf("Can I have 15 more minutes?") }
    var sending by remember { mutableStateOf(false) }

    val log by SpikeState.log.collectAsStateWithLifecycle()
    val listening by SpikeState.listening.collectAsStateWithLifecycle()

    fun refreshDevices() {
        pairedDevices = if (adapter != null && BtSpikePermissions.hasBluetooth(context)) {
            adapter.bondedDevices.sortedBy { it.name ?: it.address }
        } else {
            emptyList()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsGranted = BtSpikePermissions.hasBluetooth(context)
        refreshDevices()
    }

    LaunchedEffect(permissionsGranted) { refreshDevices() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("SP-02 Bluetooth spike", style = MaterialTheme.typography.titleLarge)

        // ----- Status -----
        val status = when {
            adapter == null -> "This phone has no Bluetooth"
            !adapter.isEnabled -> "Bluetooth is OFF: turn it on"
            !permissionsGranted -> "Permission missing"
            else -> "Bluetooth ON, permission granted. Android ${android.os.Build.VERSION.SDK_INT}"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!permissionsGranted || !BtSpikePermissions.hasNotifications(context)) {
                Button(onClick = {
                    val needed = BtSpikePermissions.required()
                    if (needed.isEmpty()) refreshDevices() else permissionLauncher.launch(needed)
                }) { Text("Grant permissions") }
            }
            OutlinedButton(onClick = {
                // The pairing is done by Android, not by our app
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }) { Text("Pair (settings)") }
            OutlinedButton(onClick = { refreshDevices() }) { Text("Refresh") }
        }

        HorizontalDivider()

        // ----- Partner side: wait for requests -----
        Text("1. This phone is the PARTNER", style = MaterialTheme.typography.titleMedium)
        Button(
            enabled = permissionsGranted && adapter != null,
            onClick = {
                if (listening) PartnerListenerService.stop(context) else PartnerListenerService.start(context)
            }
        ) { Text(if (listening) "Stop waiting for requests" else "Wait for requests") }

        HorizontalDivider()

        // ----- Requester side: send a message -----
        Text("2. This phone is the REQUESTER", style = MaterialTheme.typography.titleMedium)
        Text("Paired phones (tap one to choose the partner):", style = MaterialTheme.typography.bodySmall)
        LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
            items(pairedDevices, key = { it.address }) { device ->
                val chosen = device.address == partner?.address
                Text(
                    text = (if (chosen) "> " else "") + (device.name ?: "Unknown") + "  (" + device.address + ")",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { partner = device }
                        .padding(vertical = 10.dp)
                )
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = permissionsGranted && partner != null && adapter != null && !sending,
            onClick = {
                val target = partner ?: return@Button
                val bluetooth = adapter ?: return@Button
                scope.launch {
                    sending = true
                    SpikeState.add("Sending to ${target.name}...")
                    val result = Rfcomm.send(bluetooth, target, message)
                    SpikeState.add(
                        if (result.ok) {
                            "Sent. ACK after ${result.millis} ms"
                        } else {
                            "FAILED after ${result.millis} ms: ${result.error}"
                        }
                    )
                    sending = false
                }
            }
        ) { Text(if (sending) "Sending..." else "Send to partner") }

        HorizontalDivider()

        // ----- Log -----
        Text("Log", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
