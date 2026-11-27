package com.example.stopscrolling.permission

import com.example.stopscrolling.challenge.ChallengeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengePermissionsTest {

    private val activityRecognition = "android.permission.ACTIVITY_RECOGNITION"

    @Test
    fun `math needs no permission`() {
        assertNull(ChallengePermissions.requirementFor(ChallengeType.MATH))
        assertNull(ChallengePermissions.permissionToRequest(ChallengeType.MATH, 34))
    }

    @Test
    fun `walking needs the activity recognition permission`() {
        val requirement = ChallengePermissions.requirementFor(ChallengeType.WALKING)

        assertNotNull(requirement)
        assertEquals(activityRecognition, requirement!!.permission)
        assertEquals(29, requirement.minSdk)
    }

    @Test
    fun `walking is asked on Android 10 and later`() {
        assertEquals(
            activityRecognition,
            ChallengePermissions.permissionToRequest(ChallengeType.WALKING, 29)
        )
        assertEquals(
            activityRecognition,
            ChallengePermissions.permissionToRequest(ChallengeType.WALKING, 36)
        )
    }

    @Test
    fun `walking is not asked below Android 10`() {
        assertNull(ChallengePermissions.permissionToRequest(ChallengeType.WALKING, 28))
        assertNull(ChallengePermissions.permissionToRequest(ChallengeType.WALKING, 24))
    }

    @Test
    fun `every challenge has an answer`() {
        // A new challenge must say if it needs a permission: this does not crash
        ChallengeType.entries.forEach { type ->
            ChallengePermissions.requirementFor(type)
        }
    }
}
