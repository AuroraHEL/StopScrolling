package com.example.stopscrolling.usage

import com.example.stopscrolling.data.GroupWithApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/*
 * Finds the groups of an app using the database.
 *
 * It receives only the function that gives the groups, not the whole repository
 * (in the app: appGroupRepository::observeGroups). In this way it does not change
 * when other people add functions to the repository, and it is easy to test.
 */
class RoomRegulatedGroupLookup(
    private val observeGroups: () -> Flow<List<GroupWithApps>>
) : RegulatedGroupLookup {

    override suspend fun findGroupsOf(packageName: String): List<RegulatedGroup> {
        // observeGroups() is a Flow: first() takes the current list and stops
        val allGroups = observeGroups().first()

        // An app can be in more than one group, so we keep all of them
        return allGroups
            .filter { item -> item.apps.any { it.packageName == packageName } }
            .map { item ->
                RegulatedGroup(
                    groupId = item.group.id,
                    packageNames = item.apps.map { it.packageName },
                    name = item.group.name,
                    timeLimitMinutes = item.group.timeLimit,
                    limitType = item.group.limitType,
                    weeklyLimitMinutes = item.group.weeklyLimit
                )
            }
    }
}