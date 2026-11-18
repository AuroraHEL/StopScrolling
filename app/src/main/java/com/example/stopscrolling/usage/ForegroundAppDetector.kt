package com.example.stopscrolling.usage

/*
 * Finds out when a new app comes to the foreground.
 *
 * It remembers the last app that came to the foreground. An app is "opened" when it comes
 * to the foreground and it is different from the previous one. Going to another screen
 * of the same app is not a new opening.
 *
 * It does not use Android classes, so it can be unit tested.
 */
class ForegroundAppDetector {

    // The last app that came to the foreground
    private var lastForegroundPackage: String? = null

    /*
     * Reads new events (sorted by time) and returns the apps that were opened, in order.
     * Events of apps that go to the background are ignored.
     */
    fun process(events: List<ForegroundEvent>): List<String> {
        val openedApps = mutableListOf<String>()

        for (event in events) {
            if (event.isResumed && event.packageName != lastForegroundPackage) {
                lastForegroundPackage = event.packageName
                openedApps.add(event.packageName)
            }
        }

        return openedApps
    }
}
