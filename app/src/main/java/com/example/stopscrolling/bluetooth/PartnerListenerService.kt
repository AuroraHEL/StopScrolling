package com.example.stopscrolling.bluetooth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/*
 * SP-02 (spike): the partner phone waits for requests in a foreground service.
 *
 * A foreground service (type "connectedDevice") keeps the process of the app alive when the
 * app is in the background, so that the Bluetooth server socket can accept a connection.
 * When a message arrives, the service shows a notification.
 *
 * Start it only after the Bluetooth permission is granted (see [BtSpikePermissions]):
 * on Android 14 and higher, the type "connectedDevice" needs it.
 */
class PartnerListenerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var server: Rfcomm.Server? = null
    private var nextNotificationId = FIRST_REQUEST_NOTIFICATION_ID

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BtSpikePermissions.hasBluetooth(this)) {
            SpikeState.add("Bluetooth permission is missing: the service stops")
            stopSelf()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, ONGOING_NOTIFICATION_ID, buildOngoingNotification(), type)

        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            SpikeState.add("Bluetooth is off or not available: the service stops")
            stopSelf()
            return START_NOT_STICKY
        }

        // If the service is started again, we do not start a second server
        if (server == null) {
            server = Rfcomm.Server(adapter, ::onMessage).also { it.start(serviceScope) }
            SpikeState.setListening(true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        serviceScope.cancel()
        SpikeState.setListening(false)
        SpikeState.add("Stopped listening")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Called by the server (not on the main thread)
    private fun onMessage(from: String, text: String) {
        SpikeState.add("Received from $from: $text")
        showRequestNotification(from, text)
    }

    // ---------- Notifications ----------

    private fun createChannels() {
        // Notification channels exist from Android 8
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Partner service", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REQUESTS, "Requests from friends", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openScreenIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, BluetoothSpikeActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildOngoingNotification() =
        NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Waiting for requests from friends")
            .setContentIntent(openScreenIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun showRequestNotification(from: String, text: String) {
        // Without the permission (Android 13+), Android ignores the notification: we do not crash
        if (!BtSpikePermissions.hasNotifications(this)) {
            SpikeState.add("Notification permission is missing: no notification shown")
            return
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_REQUESTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Request from $from")
            .setContentText(text)
            .setContentIntent(openScreenIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(nextNotificationId++, notification)
    }

    companion object {
        private const val CHANNEL_ONGOING = "spike_partner_service"
        private const val CHANNEL_REQUESTS = "spike_requests"
        private const val ONGOING_NOTIFICATION_ID = 100
        private const val FIRST_REQUEST_NOTIFICATION_ID = 200

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PartnerListenerService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PartnerListenerService::class.java))
        }
    }
}
