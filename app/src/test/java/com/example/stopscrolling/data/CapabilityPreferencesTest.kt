package com.example.stopscrolling.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityPreferencesTest {

    @Test
    fun `all capabilities are enabled by default`() {
        val preferences = CapabilityPreferences()

        assertTrue(preferences.canWalk)
        assertTrue(preferences.canDoMentalArithmetic)
        assertTrue(preferences.hasEnabledChallenge())
    }

    @Test
    fun `no enabled challenge is detected`() {
        val preferences = CapabilityPreferences(
            canWalk = false,
            canDoMentalArithmetic = false
        )

        assertFalse(preferences.hasEnabledChallenge())
    }

    @Test
    fun `walking can be turned off when math is enabled`() {
        val updated = CapabilityPreferences().withCanWalk(false)

        assertEquals(
            CapabilityPreferences(canWalk = false, canDoMentalArithmetic = true),
            updated
        )
    }

    @Test
    fun `math can be turned off when walking is enabled`() {
        val updated = CapabilityPreferences().withCanDoMentalArithmetic(false)

        assertEquals(
            CapabilityPreferences(canWalk = true, canDoMentalArithmetic = false),
            updated
        )
    }

    @Test
    fun `the last enabled challenge cannot be turned off`() {
        val onlyMath = CapabilityPreferences(canWalk = false)
        val onlyWalking = CapabilityPreferences(canDoMentalArithmetic = false)

        assertNull(onlyMath.withCanDoMentalArithmetic(false))
        assertNull(onlyWalking.withCanWalk(false))
    }

    @Test
    fun `turning a capability on is always allowed`() {
        val onlyMath = CapabilityPreferences(canWalk = false)

        assertEquals(CapabilityPreferences(), onlyMath.withCanWalk(true))
    }
}
