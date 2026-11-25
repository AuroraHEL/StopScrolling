package com.example.stopscrolling.usage

/** Minutes of extension granted today to a group (written by US-19). */
interface ExtensionLookup {
    suspend fun extraMinutesToday(groupId: Int): Int

    /*
     * US-13: minutes of extension granted since Monday 00:00 (today included).
     * They are added to the weekly limit of a soft limit.
     */
    suspend fun extraMinutesThisWeek(groupId: Int): Int
}

/** TEMPORARY: no extension exists until the US-19 database write is merged. */
object NoExtensionLookup : ExtensionLookup {
    override suspend fun extraMinutesToday(groupId: Int): Int = 0

    override suspend fun extraMinutesThisWeek(groupId: Int): Int = 0
}