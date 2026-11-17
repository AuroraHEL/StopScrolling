package com.example.stopscrolling.usage

import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupApp
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.repository.AppGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRegulatedGroupLookupTest {

    // Fake repository: it returns a fixed list of groups, without database
    private class FakeRepository(private val groups: List<GroupWithApps>) : AppGroupRepository {
        override fun observeGroups(): Flow<List<GroupWithApps>> = flowOf(groups)
        override suspend fun createGroup(name: String, packageNames: List<String>): Int = 0
    }

    private fun group(id: Int, name: String, vararg packageNames: String) = GroupWithApps(
        group = AppGroup(id = id, name = name),
        apps = packageNames.mapIndexed { index, packageName ->
            GroupApp(id = id * 100 + index, groupId = id, packageName = packageName)
        }
    )

    @Test
    fun appInAGroup_returnsThatGroupWithAllItsApps() = runBlocking {
        val lookup = RoomRegulatedGroupLookup(
            FakeRepository(listOf(group(1, "Social", "app.a", "app.b"), group(2, "Games", "app.c")))
        )

        val result = lookup.findGroupsOf("app.b")

        assertEquals(listOf(RegulatedGroup(1, listOf("app.a", "app.b"))), result)
    }

    @Test
    fun appInNoGroup_returnsEmptyList() = runBlocking {
        val lookup = RoomRegulatedGroupLookup(FakeRepository(listOf(group(1, "Social", "app.a"))))

        val result = lookup.findGroupsOf("app.z")

        assertTrue(result.isEmpty())
    }

    @Test
    fun appInTwoGroups_returnsBothGroups() = runBlocking {
        val lookup = RoomRegulatedGroupLookup(
            FakeRepository(listOf(group(1, "Social", "app.a"), group(2, "Night", "app.a", "app.d")))
        )

        val result = lookup.findGroupsOf("app.a")

        assertEquals(listOf(1, 2), result.map { it.groupId })
    }
}