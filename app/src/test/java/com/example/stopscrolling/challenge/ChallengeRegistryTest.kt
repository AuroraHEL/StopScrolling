package com.example.stopscrolling.challenge

import com.example.stopscrolling.data.CapabilityPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeRegistryTest {

    // Both challenges exist in these tests, even if walking is not built yet
    private val both = setOf(ChallengeType.MATH, ChallengeType.WALKING)

    @Test
    fun `all capabilities enabled offers every implemented challenge`() {
        val result = ChallengeRegistry.availableChallenges(
            preferences = CapabilityPreferences(),
            implemented = both
        )

        assertEquals(listOf(ChallengeType.MATH, ChallengeType.WALKING), result)
    }

    @Test
    fun `math is not offered when mental arithmetic is disabled`() {
        val result = ChallengeRegistry.availableChallenges(
            preferences = CapabilityPreferences(canDoMentalArithmetic = false),
            implemented = both
        )

        assertEquals(listOf(ChallengeType.WALKING), result)
    }

    @Test
    fun `walking is not offered when walking is disabled`() {
        val result = ChallengeRegistry.availableChallenges(
            preferences = CapabilityPreferences(canWalk = false),
            implemented = both
        )

        assertEquals(listOf(ChallengeType.MATH), result)
    }

    @Test
    fun `nothing is offered when every capability is disabled`() {
        val result = ChallengeRegistry.availableChallenges(
            preferences = CapabilityPreferences(
                canWalk = false,
                canDoMentalArithmetic = false
            ),
            implemented = both
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a challenge that is not implemented is never offered`() {
        val result = ChallengeRegistry.availableChallenges(
            preferences = CapabilityPreferences(),
            implemented = setOf(ChallengeType.MATH)
        )

        assertEquals(listOf(ChallengeType.MATH), result)
    }

    @Test
    fun `a challenge without its permission is not offered`() {
        val result = ChallengeRegistry.availableChallenges(
            preferences = CapabilityPreferences(),
            isPermissionGranted = { type -> type != ChallengeType.WALKING },
            implemented = both
        )

        assertEquals(listOf(ChallengeType.MATH), result)
    }

    @Test
    fun `the order of the challenges is always the same`() {
        val first = ChallengeRegistry.availableChallenges(
            CapabilityPreferences(),
            implemented = both
        )
        val second = ChallengeRegistry.availableChallenges(
            CapabilityPreferences(),
            implemented = both
        )

        assertEquals(first, second)
    }

    @Test
    fun `isEnabledBy follows the capability of each challenge`() {
        val onlyMath = CapabilityPreferences(canWalk = false)

        assertTrue(ChallengeRegistry.isEnabledBy(ChallengeType.MATH, onlyMath))
        assertFalse(ChallengeRegistry.isEnabledBy(ChallengeType.WALKING, onlyMath))
    }

    @Test
    fun `math is the only challenge built for now`() {
        assertEquals(setOf(ChallengeType.MATH), ChallengeRegistry.implemented)
    }
}
