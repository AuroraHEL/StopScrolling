package com.example.stopscrolling.usage

/** Minutes of extension granted today to a group (written by US-19). */
interface ExtensionLookup {
    suspend fun extraMinutesToday(groupId: Int): Int
}

/** TEMPORARY: no extension exists until the US-19 database write is merged. */
object NoExtensionLookup : ExtensionLookup {
    override suspend fun extraMinutesToday(groupId: Int): Int = 0
}