package com.example.stopscrolling.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitTypeConverterTest {

    private val converter = LimitTypeConverter()

    @Test
    fun hardLimit_convertsToStringAndBack() {
        val value = LimitType.HARD

        val storedValue = converter.fromLimitType(value)
        val restoredValue = converter.toLimitType(storedValue)

        assertEquals("HARD", storedValue)
        assertEquals(LimitType.HARD, restoredValue)
    }

    @Test
    fun softLimit_convertsToStringAndBack() {
        val value = LimitType.SOFT

        val storedValue = converter.fromLimitType(value)
        val restoredValue = converter.toLimitType(storedValue)

        assertEquals("SOFT", storedValue)
        assertEquals(LimitType.SOFT, restoredValue)
    }
}
