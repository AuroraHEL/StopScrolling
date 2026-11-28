package com.example.stopscrolling.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchFilterTest {

    // Same order as the real list: alphabetical
    private val names = listOf(
        "Calendar",
        "Chrome",
        "Instagram",
        "Maps",
        "Messages",
        "YouTube"
    )

    private fun search(query: String): List<String> =
        AppSearchFilter.filterByName(names, query) { it }

    @Test
    fun emptyQuery_returnsAllAppsInTheSameOrder() {
        assertEquals(names, search(""))
    }

    @Test
    fun blankQuery_returnsAllApps() {
        assertEquals(names, search("   "))
    }

    @Test
    fun query_returnsAppsWhoseNameContainsTheText() {
        assertEquals(listOf("Instagram"), search("gram"))
    }

    @Test
    fun query_canMatchInTheMiddleOfTheName() {
        assertEquals(listOf("YouTube"), search("tube"))
    }

    @Test
    fun query_ignoresUpperAndLowerCase() {
        assertEquals(listOf("Instagram"), search("INSTA"))
        assertEquals(listOf("Instagram"), search("insta"))
        assertEquals(listOf("Instagram"), search("InStA"))
    }

    @Test
    fun query_ignoresSpacesAtTheStartAndAtTheEnd() {
        assertEquals(listOf("Maps"), search("  maps "))
    }

    @Test
    fun query_withManyMatches_keepsTheOriginalOrder() {
        // "me" is in "Chrome" and in "Messages"
        assertEquals(listOf("Chrome", "Messages"), search("me"))
    }

    @Test
    fun query_withoutMatch_returnsEmptyList() {
        assertTrue(search("xyz").isEmpty())
    }

    @Test
    fun query_usesOnlyTheNameGivenByTheFunction() {
        data class Item(val name: String, val packageName: String)

        val items = listOf(
            Item("Maps", "com.google.android.apps.maps"),
            Item("Chrome", "com.android.chrome")
        )

        // "google" is only in the package name, not in the app name
        val result = AppSearchFilter.filterByName(items, "google") { it.name }

        assertTrue(result.isEmpty())
    }
}
