package com.example.stopscrolling.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/*
 * SP-02 (spike): the runtime permissions of the Bluetooth test.
 *
 * - Android 12 (API 31) and higher: BLUETOOTH_CONNECT is a runtime permission. We need it to
 *   list the paired devices, to listen for requests and to connect to a partner.
 *   We do not scan, so we do not need BLUETOOTH_SCAN, and we do not need the location.
 * - Android 11 (API 30) and lower: BLUETOOTH is a normal permission (no dialog).
 * - Android 13 (API 33) and higher: POST_NOTIFICATIONS, to show the notification of a request.
 */
object BtSpikePermissions {

    /* The permissions to ask the user at runtime on this version of Android. */
    fun required(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    fun hasBluetooth(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
