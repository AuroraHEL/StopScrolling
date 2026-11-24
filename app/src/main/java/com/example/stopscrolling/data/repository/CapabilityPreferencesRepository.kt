package com.example.stopscrolling.data.repository

import com.example.stopscrolling.data.CapabilityPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository for reading and updating the user's capabilities.
 */
interface CapabilityPreferencesRepository {

    /**
     * Observes the saved capability preferences.
     */
    val preferences: Flow<CapabilityPreferences>

    /**
     * Observes whether capability onboarding has been completed.
     */
    val isOnboardingCompleted: Flow<Boolean>

    /**
     * Saves the user's capability preferences.
     */
    suspend fun updatePreferences(
        preferences: CapabilityPreferences
    )

    /**
     * Saves capability preferences and completes onboarding.
     */
    suspend fun completeOnboarding(
        preferences: CapabilityPreferences
    )
}
