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
        limitType: LimitType = LimitType.HARD
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
        limitType: LimitType
    ): Int = database.withTransaction {

        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }

        val groupId = dao.insertGroup(
            AppGroup(
                name = name.trim(),
                timeLimit = timeLimit,
                limitType = limitType
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

    override suspend fun applyDuePendingChanges(): Int = database.withTransaction {
        val now = currentTimeMillis()
        var changedGroups = 0

        for (group in dao.getGroupsWithDuePending(now)) {
            val applied = PendingLimits.applyDue(group, now)
            // A group can be due only for another reason (for example a deletion): skip it
            if (applied != group) {
                dao.updateGroupRow(applied)
                changedGroups++
            }
        }

        changedGroups
    }

}
