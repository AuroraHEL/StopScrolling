package com.example.stopscrolling.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingGroupChangeDao {

    // Save a pending change
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingChange(
        change: PendingGroupChange
    ): Long

    // Observe all pending changes
    @Query(
        """
        SELECT * FROM pending_group_changes
        ORDER BY executeAtMillis ASC
        """
    )
    fun observePendingChanges(): Flow<List<PendingGroupChange>>

    // Get changes that are ready to be executed
    @Query(
        """
        SELECT * FROM pending_group_changes
        WHERE executeAtMillis <= :nowMillis
        ORDER BY executeAtMillis ASC
        """
    )
    suspend fun getDueChanges(
        nowMillis: Long
    ): List<PendingGroupChange>

    // Check whether a group is pending deletion
    @Query(
        """
        SELECT * FROM pending_group_changes
        WHERE groupId = :groupId
        AND changeType = 'DELETE_GROUP'
        LIMIT 1
        """
    )
    suspend fun getPendingGroupDeletion(
        groupId: Int
    ): PendingGroupChange?

    // Get all pending changes for one group
    @Query(
        """
        SELECT * FROM pending_group_changes
        WHERE groupId = :groupId
        """
    )
    suspend fun getChangesForGroup(
        groupId: Int
    ): List<PendingGroupChange>

    // Cancel a pending removal when the app is selected again
    @Query(
        """
        DELETE FROM pending_group_changes
        WHERE groupId = :groupId
        AND packageName = :packageName
        AND changeType = 'REMOVE_APP'
        """
    )
    suspend fun cancelPendingAppRemoval(
        groupId: Int,
        packageName: String
    ): Int

    // Remove one pending change
    @Query(
        """
        DELETE FROM pending_group_changes
        WHERE id = :id
        """
    )
    suspend fun deletePendingChange(
        id: Int
    )

    // Remove all pending changes for a group
    @Query(
        """
        DELETE FROM pending_group_changes
        WHERE groupId = :groupId
        """
    )
    suspend fun deleteChangesForGroup(
        groupId: Int
    )
}
