package com.example.stopscrolling.permission

import android.Manifest
import android.os.Build
import com.example.stopscrolling.challenge.ChallengeType

/**
 * A permission that a challenge needs, and the first Android version
 * (API level) that asks for it at runtime.
 */
data class PermissionRequirement(
    val permission: String,
    val minSdk: Int
)

/**
 * Which permission each challenge needs (US-04).
 * To add a permission for a new challenge (camera, Bluetooth...), add one line here.
 */
object ChallengePermissions {

    /** The requirement of [type], or null if the challenge needs no permission. */
    fun requirementFor(type: ChallengeType): PermissionRequirement? = when (type) {
        ChallengeType.MATH -> null
        // ACTIVITY_RECOGNITION is a runtime permission from Android 10 (API 29)
        ChallengeType.WALKING -> PermissionRequirement(
            permission = Manifest.permission.ACTIVITY_RECOGNITION,
            minSdk = Build.VERSION_CODES.Q
        )
    }

    /**
     * The permission to ask for [type] on a phone with Android [sdkInt],
     * or null if nothing must be asked (no permission, or an older Android).
     */
    fun permissionToRequest(type: ChallengeType, sdkInt: Int): String? {
        val requirement = requirementFor(type) ?: return null
        return if (sdkInt >= requirement.minSdk) requirement.permission else null
    }
}
