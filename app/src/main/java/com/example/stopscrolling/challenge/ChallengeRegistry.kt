package com.example.stopscrolling.challenge

import com.example.stopscrolling.data.CapabilityPreferences

/**
 * Knows which challenges exist and which ones the user can get right now (US-03).
 * The overlay asks the registry instead of deciding by itself.
 */
object ChallengeRegistry {

    /**
     * Challenges that are really built in the app.
     * US-20 adds ChallengeType.WALKING here when the pedometer challenge is ready.
     */
    val implemented: Set<ChallengeType> = setOf(ChallengeType.MATH)

    /** True if the user said, in the capability preferences, that they can do [type]. */
    fun isEnabledBy(
        type: ChallengeType,
        preferences: CapabilityPreferences
    ): Boolean = when (type) {
        ChallengeType.MATH -> preferences.canDoMentalArithmetic
        ChallengeType.WALKING -> preferences.canWalk
    }

    /**
     * Challenges to offer on the soft limit overlay, in a fixed order.
     *
     * A challenge is offered only if it is built ([implemented]), the user
     * enabled it ([preferences]) and its permission is granted.
     * [isPermissionGranted] is the hook for US-04: by default nothing is blocked.
     */
    fun availableChallenges(
        preferences: CapabilityPreferences,
        isPermissionGranted: (ChallengeType) -> Boolean = { true },
        implemented: Set<ChallengeType> = ChallengeRegistry.implemented
    ): List<ChallengeType> {
        return ChallengeType.entries.filter { type ->
            type in implemented &&
                isEnabledBy(type, preferences) &&
                isPermissionGranted(type)
        }
    }
}
