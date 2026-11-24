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
     * Saves the user's capability preferences.
     */
    suspend fun updatePreferences(
        preferences: CapabilityPreferences
    )
}
