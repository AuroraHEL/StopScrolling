package com.example.stopscrolling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {

    @Insert
    suspend fun insertGroup(group: AppGroup): Long

    @Insert
    suspend fun insertGroupApps(apps: List<GroupApp>)

    @Transaction
    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE ASC")
    fun observeGroupsWithApps(): Flow<List<GroupWithApps>>

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

    // US-15: the whole group row, to read it, change it and save it in one transaction
    @Query("SELECT * FROM app_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: Int): AppGroup?

    // US-15: groups with changes that must start now (pendingFrom is in the past)
    @Query("SELECT * FROM app_groups WHERE pendingFrom IS NOT NULL AND pendingFrom <= :nowMillis")
    suspend fun getGroupsWithDuePending(nowMillis: Long): List<AppGroup>

    // US-15: saves the group row (the apps of the group are not changed)
    @Update
    suspend fun updateGroupRow(group: AppGroup): Int
}