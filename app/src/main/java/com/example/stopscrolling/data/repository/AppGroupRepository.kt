package com.example.stopscrolling.data.repository

import androidx.room.withTransaction
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupApp
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.PendingLimits
import kotlinx.coroutines.flow.Flow

interface AppGroupRepository {

    fun observeGroups(): Flow<List<GroupWithApps>>

    suspend fun createGroup(
        name: String,
        packageNames: List<String>,
        timeLimit: Int = 0,
        limitType: LimitType = LimitType.HARD,
        // US-13: weekly limit in minutes, 0 = no weekly limit
        weeklyLimit: Int = 0
    ): Int

    /*
     * Saves a new daily limit for the group.
     * US-15: if the group has a hard limit, the change is saved as pending and starts
     * at 00:00 of the next day. In all other cases it starts at once.
     * Returns false if the group does not exist.
     */
    suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean

    /*
     * US-13: saves the daily limit AND the weekly limit of a group in one transaction
     * (weeklyLimit 0 = no weekly limit). Both follow the rule of US-15: a change to a hard
     * limit starts at 00:00 of the next day. Returns false if the group does not exist.
     */
    suspend fun updateLimits(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType,
        weeklyLimit: Int
    ): Boolean

    /*
     * US-15: applies the pending changes whose time has come.
     * Returns how many groups were changed.
     */
    suspend fun applyDuePendingChanges(): Int
}

class AppGroupRepositoryImpl(
    private val database: AppDatabase,
    // Gives the current time. A test can give a fixed time.
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) : AppGroupRepository {

    private val dao = database.appGroupDao()

    override fun observeGroups() =
        dao.observeGroupsWithApps()

    override suspend fun createGroup(
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: LimitType,
        weeklyLimit: Int
    ): Int = database.withTransaction {

        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }
        require(weeklyLimit >= 0) {
            "Weekly limit cannot be negative"
        }

        val groupId = dao.insertGroup(
            AppGroup(
                name = name.trim(),
                timeLimit = timeLimit,
                limitType = limitType,
                weeklyLimit = weeklyLimit
            )
        ).toInt()

        dao.insertGroupApps(
            packageNames.distinct().map {
                GroupApp(
                    groupId = groupId,
                    packageName = it
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

        return database.withTransaction {
            val saved = dao.getGroup(groupId)
            if (saved == null) {
                false
            } else {
                val now = currentTimeMillis()
                // First apply a change that is already due (for example the service
                // did not run at midnight), so that we edit the real current limit
                val current = PendingLimits.applyDue(saved, now)
                val updated = PendingLimits.applyEdit(current, timeLimit, limitType, now)
                dao.updateGroupRow(updated) > 0
            }
        }
    }

    override suspend fun updateLimits(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType,
        weeklyLimit: Int
    ): Boolean {
        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }
        require(weeklyLimit >= 0) {
            "Weekly limit cannot be negative"
        }

        return database.withTransaction {
            val saved = dao.getGroup(groupId)
            if (saved == null) {
                false
            } else {
                val now = currentTimeMillis()
                val current = PendingLimits.applyDue(saved, now)
                // The weekly edit goes first: both edits must look at the limit as it was
                // before this edit (for example a soft group that becomes hard now)
                val weeklyEdited = PendingLimits.applyWeeklyEdit(current, weeklyLimit, now)
                val updated = PendingLimits.applyEdit(weeklyEdited, timeLimit, limitType, now)
                dao.updateGroupRow(updated) > 0
            }
        }
    }

    override suspend fun applyDuePendingChanges(): Int = database.withTransaction {
        val now = currentTimeMillis()
        var changedGroups = 0

        for (group in dao.getGroupsWithDuePending(now)) {
            val applied = PendingLimits.applyDue(group, now)
            // Skip the groups where applying the due change did not modify anything
            if (applied != group) {
                dao.updateGroupRow(applied)
                changedGroups++
            }
        }

        changedGroups
    }

}
