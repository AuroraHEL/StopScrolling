package com.example.stopscrolling.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stopscrolling.data.CapabilityPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapabilityPreferencesRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: CapabilityPreferencesRepository
    private lateinit var originalPreferences: CapabilityPreferences

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        repository = CapabilityPreferencesRepositoryImpl(context)
        originalPreferences = repository.preferences.first()
    }

    @After
    fun tearDown() = runBlocking {
        repository.updatePreferences(originalPreferences)
    }

    @Test
    fun defaultCapabilitiesAreEnabled() {
        val preferences = CapabilityPreferences()

        assertTrue(preferences.canWalk)
        assertTrue(preferences.canDoMentalArithmetic)
    }

    @Test
    fun updateWalkingCapability() = runBlocking {
        repository.updatePreferences(
            CapabilityPreferences(
                canWalk = false,
                canDoMentalArithmetic = true
            )
        )

        val saved = repository.preferences.first()

        assertFalse(saved.canWalk)
        assertTrue(saved.canDoMentalArithmetic)
    }

    @Test
    fun updateMentalArithmeticCapability() = runBlocking {
        repository.updatePreferences(
            CapabilityPreferences(
                canWalk = true,
                canDoMentalArithmetic = false
            )
        )

        val saved = repository.preferences.first()

        assertTrue(saved.canWalk)
        assertFalse(saved.canDoMentalArithmetic)
    }

    @Test
    fun preferencesRemainAvailableInNewRepository() = runBlocking {
        val expected = CapabilityPreferences(
            canWalk = false,
            canDoMentalArithmetic = true
        )

        repository.updatePreferences(expected)

        val newRepository =
            CapabilityPreferencesRepositoryImpl(context)

        assertEquals(
            expected,
            newRepository.preferences.first()
        )
    }

    @Test
    fun atLeastOneChallengeIsEnabled() {
        assertTrue(
            CapabilityPreferences(
                canWalk = true,
                canDoMentalArithmetic = true
            ).hasEnabledChallenge()
        )

        assertTrue(
            CapabilityPreferences(
                canWalk = true,
                canDoMentalArithmetic = false
            ).hasEnabledChallenge()
        )

        assertTrue(
            CapabilityPreferences(
                canWalk = false,
                canDoMentalArithmetic = true
            ).hasEnabledChallenge()
        )

        assertFalse(
            CapabilityPreferences(
                canWalk = false,
                canDoMentalArithmetic = false
            ).hasEnabledChallenge()
        )
    }
}
