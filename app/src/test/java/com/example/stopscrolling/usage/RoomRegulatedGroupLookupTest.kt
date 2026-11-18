package com.example.stopscrolling.usage

import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupApp
import com.example.stopscrolling.data.GroupWithApps
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.stopscrolling.data.LimitType

class RoomRegulatedGroupLookupTest {

    private fun group(id: Int, name: String, vararg packageNames: String) = GroupWithApps(
        group = AppGroup(id = id, name = name),
        apps = packageNames.mapIndexed { index, packageName ->
            GroupApp(id = id * 100 + index, groupId = id, packageName = packageName)
        }
    )

    @Test
    fun appInAGroup_returnsThatGroupWithAllItsApps() = runBlocking {
        val groups = listOf(group(1, "Social", "app.a", "app.b"), group(2, "Games", "app.c"))
        val lookup = RoomRegulatedGroupLookup { flowOf(groups) }

        val result = lookup.findGroupsOf("app.b")

        assertEquals(
            listOf(RegulatedGroup(1, listOf("app.a", "app.b"), name = "Social")),
            result
        )
    }

    @Test
    fun appInNoGroup_returnsEmptyList() = runBlocking {
        val groups = listOf(group(1, "Social", "app.a"))
        val lookup = RoomRegulatedGroupLookup { flowOf(groups) }

        val result = lookup.findGroupsOf("app.z")

        assertTrue(result.isEmpty())
    }

    @Test
    fun appInTwoGroups_returnsBothGroups() = runBlocking {
        val groups = listOf(group(1, "Social", "app.a"), group(2, "Night", "app.a", "app.d"))
        val lookup = RoomRegulatedGroupLookup { flowOf(groups) }

        val result = lookup.findGroupsOf("app.a")

        assertEquals(listOf(1, 2), result.map { it.groupId })
    }

    @Test
    fun groupCarriesItsLimitSettings() = runBlocking {
        val soft = GroupWithApps(
            group = AppGroup(id = 3, name = "Video", timeLimit = 45, limitType = LimitType.SOFT),
            apps = listOf(GroupApp(id = 300, groupId = 3, packageName = "app.v"))
        )
        val lookup = RoomRegulatedGroupLookup { flowOf(listOf(soft)) }

        val result = lookup.findGroupsOf("app.v").single()

        assertEquals("Video", result.name)
        assertEquals(45, result.timeLimitMinutes)
        assertEquals(LimitType.SOFT, result.limitType)
    }
}