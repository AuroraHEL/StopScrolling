package com.example.stopscrolling.permission

import com.example.stopscrolling.permission.PermissionStatus.DENIED
import com.example.stopscrolling.permission.PermissionStatus.DENIED_PERMANENTLY
import com.example.stopscrolling.permission.PermissionStatus.GRANTED
import com.example.stopscrolling.permission.PermissionStatus.NOT_GRANTED
import com.example.stopscrolling.permission.PermissionStatus.NOT_NEEDED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStatusRulesTest {

    // ---------- after the system question ----------

    @Test
    fun `granted answer gives GRANTED`() {
        assertEquals(GRANTED, PermissionStatusRules.statusAfterRequest(true, false))
        assertEquals(GRANTED, PermissionStatusRules.statusAfterRequest(true, true))
    }

    @Test
    fun `first denial can be asked again`() {
        assertEquals(DENIED, PermissionStatusRules.statusAfterRequest(false, true))
    }

    @Test
    fun `denial without rationale means do not ask again`() {
        assertEquals(
            DENIED_PERMANENTLY,
            PermissionStatusRules.statusAfterRequest(false, false)
        )
    }

    // ---------- when the screen is shown again ----------

    @Test
    fun `no permission needed gives NOT_NEEDED`() {
        assertEquals(
            NOT_NEEDED,
            PermissionStatusRules.statusOnRefresh(false, false, NOT_GRANTED)
        )
        assertEquals(
            NOT_NEEDED,
            PermissionStatusRules.statusOnRefresh(false, true, GRANTED)
        )
    }

    @Test
    fun `granted in the Android settings is detected`() {
        assertEquals(
            GRANTED,
            PermissionStatusRules.statusOnRefresh(true, true, DENIED_PERMANENTLY)
        )
        assertEquals(
            GRANTED,
            PermissionStatusRules.statusOnRefresh(true, true, DENIED)
        )
    }

    @Test
    fun `revoked in the Android settings is detected`() {
        assertEquals(
            NOT_GRANTED,
            PermissionStatusRules.statusOnRefresh(true, false, GRANTED)
        )
    }

    @Test
    fun `a denial is remembered when the screen is shown again`() {
        assertEquals(
            DENIED,
            PermissionStatusRules.statusOnRefresh(true, false, DENIED)
        )
        assertEquals(
            DENIED_PERMANENTLY,
            PermissionStatusRules.statusOnRefresh(true, false, DENIED_PERMANENTLY)
        )
    }

    @Test
    fun `not asked yet stays NOT_GRANTED`() {
        assertEquals(
            NOT_GRANTED,
            PermissionStatusRules.statusOnRefresh(true, false, NOT_GRANTED)
        )
    }

    // ---------- can we show the question? ----------

    @Test
    fun `the question can be shown only before a permanent denial`() {
        assertTrue(PermissionStatusRules.canAsk(NOT_GRANTED))
        assertTrue(PermissionStatusRules.canAsk(DENIED))
        assertFalse(PermissionStatusRules.canAsk(DENIED_PERMANENTLY))
        assertFalse(PermissionStatusRules.canAsk(GRANTED))
        assertFalse(PermissionStatusRules.canAsk(NOT_NEEDED))
    }
}
