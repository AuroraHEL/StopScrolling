package com.example.stopscrolling.usage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/*
 * Calls [onDateChanged] when the date changes (at midnight) or when the clock is set.
 *
 * The monitor service uses it to apply the pending limit changes (US-15) at 00:00,
 * even if the app is closed. It is registered in code (not in the manifest) and works
 * while the service is running. If the service was not running at midnight, the pending
 * changes are applied the next time the service starts.
 */
class DateChangeReceiver(
    private val onDateChanged: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        onDateChanged()
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED) // midnight
            addAction(Intent.ACTION_TIME_CHANGED) // the clock was set (for example by the network)
        }
        ContextCompat.registerReceiver(
            context,
            this,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            // It was not registered: nothing to do
        }
    }
}
