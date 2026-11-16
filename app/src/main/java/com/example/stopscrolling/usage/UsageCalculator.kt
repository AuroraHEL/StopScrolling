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
     *
     * [events] must be sorted by time and must only contain events between
     * [startMillis] (start of the day) and [endMillis] (now).
     */

    fun sumForegroundTime(
        events: List<ForegroundEvent>,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        // For each app: the moment when it came to the foreground (only while it is open)
        val foregroundSince = mutableMapOf<String, Long>()
        // Apps that already had at least one event in this time window
        val seenPackages = mutableSetOf<String>()

        for (event in events) {
            val pkg = event.packageName
            val isFirstEventOfApp = seenPackages.add(pkg)

            if (event.isResumed) {
                // Keep the first start if the app is already marked as open
                if (!foregroundSince.containsKey(pkg)) {
                    foregroundSince[pkg] = event.timestamp
                }
            } else {
                var since = foregroundSince.remove(pkg)
                if (since == null && isFirstEventOfApp) {
                    // The app was already open at the start of the day (opened before midnight)
                    since = startMillis
                }
                if (since != null) {
                    totals[pkg] = (totals[pkg] ?: 0L) + (event.timestamp - since)
                }
            }
        }

        // Apps that are still in the foreground right now
        for ((pkg, since) in foregroundSince) {
            totals[pkg] = (totals[pkg] ?: 0L) + (endMillis - since)
        }

        return totals
    }
}