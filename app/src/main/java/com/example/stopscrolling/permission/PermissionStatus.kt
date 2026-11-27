package com.example.stopscrolling.permission

/** State of the permission needed by a challenge (US-04). */
enum class PermissionStatus {
    /** The challenge needs no permission on this Android version. */
    NOT_NEEDED,

    /** The permission is granted. */
    GRANTED,

    /** The permission is needed and the user has not been asked yet. */
    NOT_GRANTED,

    /** The user said no, but Android will still show the question again. */
    DENIED,

    /** The user chose "don't ask again": only the Android settings can change it. */
    DENIED_PERMANENTLY
}

/** The rules that decide the [PermissionStatus]. Pure code, easy to test. */
object PermissionStatusRules {

    /**
     * Status right after the system question was answered.
     * If the answer is "no" and Android no longer wants to show a rationale,
     * the user chose "don't ask again".
     */
    fun statusAfterRequest(
        granted: Boolean,
        shouldShowRationale: Boolean
    ): PermissionStatus = when {
        granted -> PermissionStatus.GRANTED
        shouldShowRationale -> PermissionStatus.DENIED
        else -> PermissionStatus.DENIED_PERMANENTLY
    }

    /**
     * Status when the screen is shown again. The user can grant or revoke
     * the permission from the Android settings, so we always look at the real value.
     * A "no" is remembered, because Android cannot tell us about it without asking.
     */
    fun statusOnRefresh(
        needed: Boolean,
        granted: Boolean,
        previous: PermissionStatus
    ): PermissionStatus = when {
        !needed -> PermissionStatus.NOT_NEEDED
        granted -> PermissionStatus.GRANTED
        previous == PermissionStatus.DENIED ||
            previous == PermissionStatus.DENIED_PERMANENTLY -> previous
        else -> PermissionStatus.NOT_GRANTED
    }

    /** True if we can still show the permission question to the user. */
    fun canAsk(status: PermissionStatus): Boolean =
        status == PermissionStatus.NOT_GRANTED || status == PermissionStatus.DENIED
}
