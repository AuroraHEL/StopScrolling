package com.example.stopscrolling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {

    // Existing: Insert a new group
    @Insert
    suspend fun insertGroup(group: AppGroup): Long

    // Existing: Insert apps into a group
    @Insert
    suspend fun insertGroupApps(apps: List<GroupApp>)

    // Existing: Observe all groups with their apps
    @Transaction
    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE ASC")
    fun observeGroupsWithApps(): Flow<List<GroupWithApps>>

    // Existing: Update time limit and limit type
    @Query("""
        UPDATE app_groups
        SET timeLimit = :timeLimit,
            limitType = :limitType
        WHERE id = :groupId
    """)
    suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Int

    // US-09 Task 2: Update group name and limit settings
    @Query("""
        UPDATE app_groups
        SET name = :name,
            timeLimit = :timeLimit,
            limitType = :limitType
        WHERE id = :groupId
    """)
    suspend fun updateGroup(
        groupId: Int,
        name: String,
        timeLimit: Int,
        limitType: LimitType
    ): Int

    // US-09 Task 2: Remove existing app associations
    @Query("DELETE FROM group_apps WHERE groupId = :groupId")
    suspend fun deleteGroupApps(groupId: Int): Int

    // US-09 Task 2: Delete a group
    @Query("DELETE FROM app_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Int): Int
}
