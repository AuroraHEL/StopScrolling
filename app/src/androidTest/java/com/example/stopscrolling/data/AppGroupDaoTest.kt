package com.example.stopscrolling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {

    // Create a new group
    @Insert
    suspend fun insertGroup(
        group: AppGroup
    ): Long

    // Add apps to a group
    @Insert
    suspend fun insertGroupApps(
        apps: List<GroupApp>
    )

    // Observe all groups with their apps
    @Transaction
    @Query(
        """
        SELECT * FROM app_groups
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun observeGroupsWithApps(): Flow<List<GroupWithApps>>

    // Update the time limit of an existing group
    @Query(
        """
        UPDATE app_groups
        SET timeLimit = :timeLimit,
            limitType = :limitType
        WHERE id = :groupId
        """
    )
    suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Int

    // US-09 Task 2: Update group details
    @Query(
        """
        UPDATE app_groups
        SET name = :name,
            timeLimit = :timeLimit,
            limitType = :limitType
        WHERE id = :groupId
        """
    )
    suspend fun updateGroup(
        groupId: Int,
        name: String,
        timeLimit: Int,
        limitType: LimitType
    ): Int

    // US-09 Task 3: Find a group by ID
    @Query(
        """
        SELECT * FROM app_groups
        WHERE id = :groupId
        LIMIT 1
        """
    )
    suspend fun getGroupById(
        groupId: Int
    ): AppGroup?

    // US-09 Task 3: Get all apps in a group
    @Query(
        """
        SELECT * FROM group_apps
        WHERE groupId = :groupId
        """
    )
    suspend fun getAppsForGroup(
        groupId: Int
    ): List<GroupApp>

    // US-09 Task 3: Remove one app from a group
    @Query(
        """
        DELETE FROM group_apps
        WHERE groupId = :groupId
        AND packageName = :packageName
        """
    )
    suspend fun deleteGroupApp(
        groupId: Int,
        packageName: String
    ): Int

    // US-09 Task 2: Remove all apps from a group
    @Query(
        """
        DELETE FROM group_apps
        WHERE groupId = :groupId
        """
    )
    suspend fun deleteGroupApps(
        groupId: Int
    ): Int

    // US-09 Task 2: Delete an existing group
    @Query(
        """
        DELETE FROM app_groups
        WHERE id = :groupId
        """
    )
    suspend fun deleteGroup(
        groupId: Int
    ): Int
}
