package com.example.stopscrolling.util

/**
 * US-07: search in the list of installed apps.
 * It does not use Android classes, so it can be tested with simple JVM tests.
 */
object AppSearchFilter {

    /**
     * Returns the items whose name contains [query].
     * - Upper and lower case are ignored: "insta" finds "Instagram".
     * - Spaces at the start and at the end of the query are ignored.
     * - An empty query returns the whole list, in the same order.
     *   The order of the result is always the order of [items].
     */
    fun <T> filterByName(
        items: List<T>,
        query: String,
        nameOf: (T) -> String
    ): List<T> {
        val text = query.trim()
        if (text.isEmpty()) return items

        return items.filter { nameOf(it).contains(text, ignoreCase = true) }
    }
}
