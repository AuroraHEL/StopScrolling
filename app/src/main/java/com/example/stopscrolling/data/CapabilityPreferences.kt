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
}
