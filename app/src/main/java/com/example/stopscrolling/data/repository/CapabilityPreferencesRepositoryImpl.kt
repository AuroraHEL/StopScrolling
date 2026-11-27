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
}
