package com.example.stopscrolling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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
}