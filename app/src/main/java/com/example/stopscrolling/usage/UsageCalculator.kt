package com.example.stopscrolling.usage

/*
 * A simplified usage event: a screen (activity) of an app came to the foreground
 * or went to the background.
 *
 * We use our own class instead of UsageEvents.Event so that the calculation
 * can be unit tested without Android.
 */
data class ForegroundEvent(
    val packageName: String,
    val timestamp: Long,
    // true = the screen came to the foreground, false = the screen went to the background
    val isResumed: Boolean,
    // Name of the screen (activity) of the event
    val className: String = ""
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
        // For each app: when its current session started (only while the app is open)
        val foregroundSince = mutableMapOf<String, Long>()
        // For each app: the screen (activity) that is open now
        val currentScreen = mutableMapOf<String, String>()

        for (event in events) {
            val pkg = event.packageName

            if (event.isResumed) {
                // A screen of the app is visible now. If the app was already marked as open,
                // we missed the end of the old session: we do not count it and start again.
                foregroundSince[pkg] = event.timestamp
                currentScreen[pkg] = event.className
            } else {
                // An end event counts only if it is about the screen that is open now.
                // Example: PAUSED(A), RESUMED(B), STOPPED(A): the STOPPED(A) comes late
                // and must not end the session of screen B.
                val since = foregroundSince[pkg]
                if (since != null && currentScreen[pkg] == event.className) {
                    addTime(totals, pkg, since, event.timestamp, startMillis, endMillis)
                    foregroundSince.remove(pkg)
                    currentScreen.remove(pkg)
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