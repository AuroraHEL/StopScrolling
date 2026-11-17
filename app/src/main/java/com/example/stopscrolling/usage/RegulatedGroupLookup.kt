package com.example.stopscrolling.usage

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// ---------- Groups ----------

/* A regulated group: its id and the package names of all the apps inside it. */
data class RegulatedGroup(val groupId: Int, val packageNames: List<String>)

/* Finds the regulated groups of an app. */
interface RegulatedGroupLookup {

    /*
     * Returns the groups that contain [packageName].
     * The list is empty if the app is not in any group.
     */
    suspend fun findGroupsOf(packageName: String): List<RegulatedGroup>
}

/*
 * Fake version, only for testing without the database: Chrome is the only "regulated" app.
 * The app uses [RoomRegulatedGroupLookup].
 */
class FakeRegulatedGroupLookup : RegulatedGroupLookup {

    private val group = RegulatedGroup(groupId = 1, packageNames = listOf("com.android.chrome"))

    override suspend fun findGroupsOf(packageName: String): List<RegulatedGroup> {
        return if (packageName in group.packageNames) listOf(group) else emptyList()
    }
}

// ---------- Event for the other parts of the app ----------

/* Sent when the user opens an app that is in a regulated group. */
data class RegulatedAppOpened(
    val packageName: String,
    val groupId: Int,
    // Total time used today by all the apps of the group, in milliseconds
    val groupUsageTodayMillis: Long
)

/*
 * The service publishes the events here. Other code (for example the code that shows
 * the lock screen) can collect [regulatedAppOpened].
 */
object UsageMonitorEvents {

    private val _regulatedAppOpened = MutableSharedFlow<RegulatedAppOpened>(extraBufferCapacity = 16)
    val regulatedAppOpened: SharedFlow<RegulatedAppOpened> = _regulatedAppOpened.asSharedFlow()

    fun publish(event: RegulatedAppOpened) {
        _regulatedAppOpened.tryEmit(event)
    }
}