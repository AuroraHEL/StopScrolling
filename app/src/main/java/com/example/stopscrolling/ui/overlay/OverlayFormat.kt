package com.example.stopscrolling.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.stopscrolling.R

/** Formats a number of minutes as "45 min", "2 h" or "1 h 30 min". */
@Composable
fun formatDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, minutes)
        minutes == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, minutes)
    }
}