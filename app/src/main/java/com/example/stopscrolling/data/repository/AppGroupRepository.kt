package com.example.stopscrolling.data.repository

import androidx.room.withTransaction
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupApp
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import kotlinx.coroutines.flow.Flow

interface AppGroupRepository {

    fun observeGroups(): Flow<List<GroupWithApps>>

    suspend fun createGroup(
        name: String,
        packageNames: List<String>,
        timeLimit: Int = 0,
        limitType: LimitType = LimitType.HARD
    ): Int

    suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean

    // US-09 Task 2: Update an existing group
    suspend fun updateGroup(
        groupId: Int,
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean

    // US-09 Task 2: Delete a group
    suspend fun deleteGroup(groupId: Int): Boolean
}

class AppGroupRepositoryImpl(
    private val database: AppDatabase
) : AppGroupRepository {

    private val dao = database.appGroupDao()

    override fun observeGroups(): Flow<List<GroupWithApps>> =
        dao.observeGroupsWithApps()

    override suspend fun createGroup(
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: LimitType
    ): Int = database.withTransaction {

        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }

        require(name.isNotBlank()) {
            "Group name cannot be blank"
        }


        val groupId = dao.insertGroup(
            AppGroup(
                name = name.trim(),
                timeLimit = timeLimit,
                limitType = limitType
            )
        ).toInt()

        dao.insertGroupApps(
            packageNames.distinct().map { packageName ->
                GroupApp(
                    groupId = groupId,
                    packageName = packageName
                )
            }
        )

        groupId
    }

    override suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean {
        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }

        return dao.updateLimit(
            groupId = groupId,
            timeLimit = timeLimit,
            limitType = limitType
        ) > 0
    }

    // US-09 Task 2: Update group details and apps atomically
    override suspend fun updateGroup(
        groupId: Int,
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean = database.withTransaction {

        require(name.isNotBlank()) {
            "Group name cannot be blank"
        }

        require(packageNames.isNotEmpty()) {
            "At least one app must be selected"
        }

        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }

        val updatedRows = dao.updateGroup(
            groupId = groupId,
            name = name.trim(),
            timeLimit = timeLimit,
            limitType = limitType
        )

        if (updatedRows == 0) {
            return@withTransaction false
        }

        // Replace existing app associations
        dao.deleteGroupApps(groupId)

        dao.insertGroupApps(
            packageNames.distinct().map { packageName ->
                GroupApp(
                    groupId = groupId,
                    packageName = packageName
                )
            }
        )

        true
    }

    // US-09 Task 2: Delete a group and its app associations
    override suspend fun deleteGroup(
        groupId: Int
    ): Boolean = database.withTransaction {

        dao.deleteGroupApps(groupId)

        dao.deleteGroup(groupId) > 0
    }
}
