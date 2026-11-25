package com.example.stopscrolling.usage

import android.content.Context
import java.time.LocalDate

/**
 * Remembers which hard-limit warnings have already
 * been sent today, even after the app restarts.
 */
class HardLimitWarningStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(
            "hard_limit_warnings",
            Context.MODE_PRIVATE
        )

    /**
     * Returns true if the warning was already sent today.
     */
    fun wasWarnedToday(
        groupId: Int,
        limitType: String = "daily"
    ): Boolean {
        val key = warningKey(groupId, limitType)
        return preferences.getString(key, null) ==
                LocalDate.now().toString()
    }

    /**
     * Saves the date when a warning is sent.
     */
    fun markWarnedToday(
        groupId: Int,
        limitType: String = "daily"
    ) {
        val key = warningKey(groupId, limitType)

        preferences.edit()
            .putString(key, LocalDate.now().toString())
            .apply()
    }

    private fun warningKey(
        groupId: Int,
        limitType: String
    ): String {
        return "warning_${groupId}_${limitType}"
    }
}
