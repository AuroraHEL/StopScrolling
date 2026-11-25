package com.example.stopscrolling.data.repository

import androidx.room.withTransaction
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupApp
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.PendingChangeType
import com.example.stopscrolling.data.PendingGroupChange
import com.example.stopscrolling.data.PendingLimits
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

interface AppGroupRepository {

    fun observeGroups(): Flow<List<GroupWithApps>>

    // US-09: Observe scheduled deletions and app removals.
    fun observePendingChanges(): Flow<List<PendingGroupChange>>

    suspend fun createGroup(
        name: String,
        packageNames: List<String>,
        timeLimit: Int = 0,
        limitType: LimitType = LimitType.HARD,
        // US-13: weekly limit in minutes, 0 = no weekly limit
        weeklyLimit: Int = 0
    ): Int

    /*
     * US-15: Hard limit changes are saved as pending
     * and applied at midnight of the next day.
     */
    suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean

    suspend fun updateGroup(
        groupId: Int,
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean

    suspend fun deleteGroup(groupId: Int): Boolean

    // US-09: Apply scheduled group deletions and app removals.
    suspend fun processDueChanges(
        nowMillis: Long = System.currentTimeMillis()
    ): Int

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
    // Allows tests to provide a fixed time.
    private val currentTimeMillis: () -> Long = {
        System.currentTimeMillis()
    }
) : AppGroupRepository {

    private val dao = database.appGroupDao()

    private val pendingDao = database.pendingGroupChangeDao()

    override fun observeGroups(): Flow<List<GroupWithApps>> {
        return dao.observeGroupsWithApps()
    }

    // US-09: Send pending changes to the UI.
    override fun observePendingChanges(): Flow<List<PendingGroupChange>> {
        return pendingDao.observePendingChanges()
    }

    private fun hasActiveHardLimit(group: AppGroup): Boolean {
        return group.limitType == LimitType.HARD &&
                (group.timeLimit > 0 || group.weeklyLimit > 0)
    }

    private fun nextMidnightMillis(
        nowMillis: Long = currentTimeMillis()
    ): Long {
        val calendar = Calendar.getInstance()

        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, 1)

        return calendar.timeInMillis
    }

    // ---------- Create Group ----------

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

        require(name.isNotBlank())

        val groupId = dao.insertGroup(
            AppGroup(
                name = name.trim(),
                timeLimit = timeLimit,
                limitType = limitType,
                weeklyLimit = weeklyLimit
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

    // ---------- Update Limit ----------

    override suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean = database.withTransaction {

        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }

        val saved = dao.getGroup(groupId)
            ?: return@withTransaction false

        // A group scheduled for deletion cannot be edited.
        if (pendingDao.getPendingGroupDeletion(groupId) != null) {
            return@withTransaction false
        }

        val now = currentTimeMillis()

        // Apply any limit change that is already due.
        val current = PendingLimits.applyDue(saved, now)

        // US-15: Save hard limit edits as pending.
        val updated = PendingLimits.applyEdit(
            current,
            timeLimit,
            limitType,
            now
        )

        dao.updateGroupRow(updated) > 0
    }

    // US-13: Update the daily and the weekly limit together.
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

            // A group scheduled for deletion cannot be edited.
            if (
                saved == null ||
                pendingDao.getPendingGroupDeletion(groupId) != null
            ) {
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

    // ---------- Update Group ----------

    override suspend fun updateGroup(
        groupId: Int,
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean = database.withTransaction {

        require(name.isNotBlank())
        require(packageNames.isNotEmpty())

        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }

        val saved = dao.getGroup(groupId)
            ?: return@withTransaction false

        // A group scheduled for deletion cannot be edited.
        if (pendingDao.getPendingGroupDeletion(groupId) != null) {
            return@withTransaction false
        }

        val now = currentTimeMillis()

        // US-15: Apply any limit change that is already due.
        val current = PendingLimits.applyDue(saved, now)

        val oldApps = dao.getAppsForGroup(groupId)
            .map { it.packageName }
            .toSet()

        val newApps = packageNames.toSet()

        val addedApps = newApps - oldApps
        val removedApps = oldApps - newApps

        // US-15: Use pending limit logic instead of
        // directly updating the active hard limit.
        val updatedGroup = PendingLimits.applyEdit(
            current,
            timeLimit,
            limitType,
            now
        ).copy(
            name = name.trim()
        )

        val updated = dao.updateGroupRow(updatedGroup)

        if (updated == 0) {
            return@withTransaction false
        }

        // Cancel pending removals for apps kept in the group.
        newApps.forEach { packageName ->
            pendingDao.cancelPendingAppRemoval(
                groupId,
                packageName
            )
        }

        // Add new apps immediately.
        if (addedApps.isNotEmpty()) {
            dao.insertGroupApps(
                addedApps.map { packageName ->
                    GroupApp(
                        groupId = groupId,
                        packageName = packageName
                    )
                }
            )
        }

        if (hasActiveHardLimit(current)) {

            // US-09: Keep removed apps until the next midnight.
            val executeAt = nextMidnightMillis(now)

            removedApps.forEach { packageName ->
                pendingDao.insertPendingChange(
                    PendingGroupChange(
                        groupId = groupId,
                        changeType =
                            PendingChangeType.REMOVE_APP.name,
                        packageName = packageName,
                        executeAtMillis = executeAt
                    )
                )
            }

        } else {

            // No active hard limit: remove apps immediately.
            removedApps.forEach { packageName ->
                dao.deleteGroupApp(
                    groupId,
                    packageName
                )
            }
        }

        true
    }

    // ---------- Delete Group ----------

    override suspend fun deleteGroup(
        groupId: Int
    ): Boolean = database.withTransaction {

        val saved = dao.getGroup(groupId)
            ?: return@withTransaction false

        val now = currentTimeMillis()

        // Apply due limit changes before checking the hard limit.
        val current = PendingLimits.applyDue(saved, now)

        if (current != saved) {
            dao.updateGroupRow(current)
        }

        if (hasActiveHardLimit(current)) {

            // Schedule deletion for tomorrow at midnight.
            if (pendingDao.getPendingGroupDeletion(groupId) == null) {

                pendingDao.insertPendingChange(
                    PendingGroupChange(
                        groupId = groupId,
                        changeType =
                            PendingChangeType.DELETE_GROUP.name,
                        packageName = null,
                        executeAtMillis = nextMidnightMillis(now)
                    )
                )
            }

            true

        } else {

            // Delete immediately when no hard limit is active.
            pendingDao.deleteChangesForGroup(groupId)

            dao.deleteGroupApps(groupId)

            dao.deleteGroup(groupId) > 0
        }
    }

    // ---------- US-09: Process Pending Group Changes ----------

    override suspend fun processDueChanges(
        nowMillis: Long
    ): Int = database.withTransaction {

        val dueChanges = pendingDao.getDueChanges(nowMillis)

        var processed = 0

        // Process group deletions first.
        val groupDeletions = dueChanges.filter {
            it.changeType == PendingChangeType.DELETE_GROUP.name
        }

        for (change in groupDeletions) {

            dao.deleteGroupApps(change.groupId)
            dao.deleteGroup(change.groupId)

            pendingDao.deleteChangesForGroup(change.groupId)

            processed++
        }

        // Process scheduled app removals.
        val appRemovals = dueChanges.filter {
            it.changeType == PendingChangeType.REMOVE_APP.name
        }

        for (change in appRemovals) {

            if (dao.getGroup(change.groupId) != null) {

                change.packageName?.let { packageName ->
                    dao.deleteGroupApp(
                        change.groupId,
                        packageName
                    )

                    processed++
                }
            }

            pendingDao.deletePendingChange(change.id)
        }

        processed
    }

    // ---------- US-15: Apply Pending Limit Changes ----------

    override suspend fun applyDuePendingChanges(): Int =
        database.withTransaction {

            val now = currentTimeMillis()
            var changedGroups = 0

            for (group in dao.getGroupsWithDuePending(now)) {

                val applied = PendingLimits.applyDue(group, now)

                if (applied != group) {
                    dao.updateGroupRow(applied)
                    changedGroups++
                }
            }

            changedGroups
        }
}
