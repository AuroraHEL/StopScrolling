package com.example.stopscrolling.usage

import com.example.stopscrolling.data.repository.AppGroupRepository
import kotlinx.coroutines.flow.first

/*
 * Finds the groups of an app using the database (through the repository).
 */
class RoomRegulatedGroupLookup(
    private val repository: AppGroupRepository
) : RegulatedGroupLookup {

    override suspend fun findGroupsOf(packageName: String): List<RegulatedGroup> {
        // observeGroups() is a Flow: first() takes the current list and stops
        val allGroups = repository.observeGroups().first()

        // An app can be in more than one group, so we keep all of them
        return allGroups
            .filter { item -> item.apps.any { it.packageName == packageName } }
            .map { item ->
                RegulatedGroup(
                    groupId = item.group.id,
                    packageNames = item.apps.map { it.packageName }
                )
            }
    }
}