package com.example.stopscrolling.data

/**
 * Stores the user's physical and cognitive capabilities.
 * All capabilities are enabled by default.
 */
data class CapabilityPreferences(
    val canWalk: Boolean = true,
    val canDoMentalArithmetic: Boolean = true
) {
    /**
     * Checks whether at least one challenge remains enabled.
     */
    fun hasEnabledChallenge(): Boolean {
        return canWalk || canDoMentalArithmetic
    }

    /**
     * Copy with [canWalk] changed, or null if the change would leave
     * no challenge enabled (US-03: at least one challenge must stay available).
     */
    fun withCanWalk(enabled: Boolean): CapabilityPreferences? {
        return copy(canWalk = enabled).takeIf { it.hasEnabledChallenge() }
    }

    /**
     * Same as [withCanWalk], for the mental arithmetic capability.
     */
    fun withCanDoMentalArithmetic(enabled: Boolean): CapabilityPreferences? {
        return copy(canDoMentalArithmetic = enabled)
            .takeIf { it.hasEnabledChallenge() }
    }
}
