package com.example.stopscrolling.permission

import com.example.stopscrolling.challenge.ChallengeType

/**
 * Test double of [ChallengePermissionChecker]:
 * only the walking challenge needs a permission, like in the real app.
 */
class FakeChallengePermissionChecker(
    var walkingPermissionNeeded: Boolean = true,
    var walkingGranted: Boolean = false
) : ChallengePermissionChecker {

    override fun permissionFor(type: ChallengeType): String? {
        return if (type == ChallengeType.WALKING && walkingPermissionNeeded) {
            "android.permission.ACTIVITY_RECOGNITION"
        } else {
            null
        }
    }

    override fun isGranted(type: ChallengeType): Boolean {
        return permissionFor(type) == null || walkingGranted
    }
}
