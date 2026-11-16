package com.example.stopscrolling.usage

/*
 * A simplified usage event: an app came to the foreground or went to the background.
 *
 * We use our own class instead of UsageEvents.Event so that the calculation
 * can be unit tested without Android.
 */
data class ForegroundEvent(
    val packageName: String,
    val timestamp: Long,
    // true = the app came to the foreground, false = the app went to the background
    val isResumed: Boolean
)

object UsageCalculator {

    /*
     * Sums the foreground time (in milliseconds) of every app.
     * Only the time between [startMillis] (start of the day) and [endMillis] (now) is counted.
     *
     * [events] must be sorted by time. They can also start a few hours before [startMillis]:
     * in this way an app that was opened before midnight is counted from midnight.
     */
    fun sumForegroundTime(
        events: List<ForegroundEvent>,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        // For each app: the moment when it came to the foreground (only while it is open)
        val foregroundSince = mutableMapOf<String, Long>()

        for (event in events) {
            val pkg = event.packageName

            if (event.isResumed) {
                // If the app is already marked as open, we missed the end of the old session.
                // We do not count the old session and we start again from this event,
                // so we never count more time than the real one.
                foregroundSince[pkg] = event.timestamp
            } else {
                // A "background" event without a "foreground" event before it is ignored,
                // because we do not know when the app was opened
                val since = foregroundSince.remove(pkg)
                if (since != null) {
                    addTime(totals, pkg, since, event.timestamp, startMillis, endMillis)
                }
            }
        }

        // Apps that are still in the foreground right now
        for ((pkg, since) in foregroundSince) {
            addTime(totals, pkg, since, endMillis, startMillis, endMillis)
        }

        return totals
    }

    // Adds the time between [from] and [to], but only the part that is inside the day
    private fun addTime(
        totals: MutableMap<String, Long>,
        pkg: String,
        from: Long,
        to: Long,
        dayStart: Long,
        dayEnd: Long
    ) {
        val clippedFrom = maxOf(from, dayStart)
        val clippedTo = minOf(to, dayEnd)
        if (clippedTo > clippedFrom) {
            totals[pkg] = (totals[pkg] ?: 0L) + (clippedTo - clippedFrom)
        }
    }
}