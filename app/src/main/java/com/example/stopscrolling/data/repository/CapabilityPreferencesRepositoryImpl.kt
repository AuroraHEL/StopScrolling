package com.example.stopscrolling.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.stopscrolling.data.CapabilityPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.capabilityDataStore by preferencesDataStore(
    name = "capability_preferences"
)

/**
 * DataStore implementation of CapabilityPreferencesRepository.
 */
class CapabilityPreferencesRepositoryImpl(
    context: Context
) : CapabilityPreferencesRepository {

    private val dataStore =
        context.applicationContext.capabilityDataStore

    private companion object {
        val CAN_WALK = booleanPreferencesKey("can_walk")

        val CAN_DO_MENTAL_ARITHMETIC =
            booleanPreferencesKey("can_do_mental_arithmetic")

        val ONBOARDING_COMPLETED =
            booleanPreferencesKey("onboarding_completed")
    }

    /**
     * Reads the saved capabilities.
     * All capabilities are enabled by default.
     */
    override val preferences: Flow<CapabilityPreferences> =
        dataStore.data.map { savedPreferences ->
            CapabilityPreferences(
                canWalk = savedPreferences[CAN_WALK] ?: true,
                canDoMentalArithmetic =
                    savedPreferences[CAN_DO_MENTAL_ARITHMETIC] ?: true
            )
        }

    /**
     * Onboarding is incomplete by default.
     */
    override val isOnboardingCompleted: Flow<Boolean> =
        dataStore.data.map { savedPreferences ->
            savedPreferences[ONBOARDING_COMPLETED] ?: false
        }

    /**
     * Saves the user's capability preferences locally.
     */
    override suspend fun updatePreferences(
        preferences: CapabilityPreferences
    ) {
        dataStore.edit { savedPreferences ->
            savedPreferences[CAN_WALK] = preferences.canWalk

            savedPreferences[CAN_DO_MENTAL_ARITHMETIC] =
                preferences.canDoMentalArithmetic
        }
    }

    /**
     * Saves preferences and marks onboarding as completed
     * in a single DataStore transaction.
     */
    override suspend fun completeOnboarding(
        preferences: CapabilityPreferences
    ) {
        dataStore.edit { savedPreferences ->
            savedPreferences[CAN_WALK] = preferences.canWalk

            savedPreferences[CAN_DO_MENTAL_ARITHMETIC] =
                preferences.canDoMentalArithmetic

            savedPreferences[ONBOARDING_COMPLETED] = true
        }
    }
}
