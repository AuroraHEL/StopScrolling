package com.example.stopscrolling.data.repository

import androidx.room.withTransaction
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupApp
import com.example.stopscrolling.data.GroupWithApps
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.PendingChangeType
import com.example.stopscrolling.data.PendingGroupChange
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
        limitType: LimitType = LimitType.HARD
    ): Int

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

    suspend fun processDueChanges(
        nowMillis: Long = System.currentTimeMillis()
    ): Int
}

class AppGroupRepositoryImpl(
    private val database: AppDatabase
) : AppGroupRepository {

    private val dao = database.appGroupDao()

    private val pendingDao = database.pendingGroupChangeDao()

    override fun observeGroups(): Flow<List<GroupWithApps>> {
        return dao.observeGroupsWithApps()
    }

    // US-09 Task 3: Send pending changes to the UI.
    override fun observePendingChanges(): Flow<List<PendingGroupChange>> {
        return pendingDao.observePendingChanges()
    }

    private fun hasActiveHardLimit(group: AppGroup): Boolean {
        return group.limitType == LimitType.HARD &&
                group.timeLimit > 0
    }

    private fun nextMidnightMillis(
        nowMillis: Long = System.currentTimeMillis()
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
        limitType: LimitType
    ): Int = database.withTransaction {

        require(timeLimit >= 0)
        require(name.isNotBlank())

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

    // ---------- Update Limit ----------

    override suspend fun updateLimit(
        groupId: Int,
        timeLimit: Int,
        limitType: LimitType
    ): Boolean = database.withTransaction {

        require(timeLimit >= 0)

        val existing = dao.getGroupById(groupId)
            ?: return@withTransaction false

        // Prevent weakening an active hard limit.
        if (
            hasActiveHardLimit(existing) &&
            (
                    limitType != LimitType.HARD ||
                            timeLimit < existing.timeLimit
                    )
        ) {
            return@withTransaction false
        }

        dao.updateLimit(
            groupId,
            timeLimit,
            limitType
        ) > 0
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
        require(timeLimit >= 0)

        val existing = dao.getGroupById(groupId)
            ?: return@withTransaction false

        // A group scheduled for deletion cannot be edited.
        if (pendingDao.getPendingGroupDeletion(groupId) != null) {
            return@withTransaction false
        }

        // Prevent weakening an active hard limit.
        if (
            hasActiveHardLimit(existing) &&
            (
                    limitType != LimitType.HARD ||
                            timeLimit < existing.timeLimit
                    )
        ) {
            return@withTransaction false
        }

        val oldApps = dao.getAppsForGroup(groupId)
            .map { it.packageName }
            .toSet()

        val newApps = packageNames.toSet()

        val addedApps = newApps - oldApps
        val removedApps = oldApps - newApps

        // Cancel pending removals for apps kept in the group.
        newApps.forEach { packageName ->
            pendingDao.cancelPendingAppRemoval(
                groupId,
                packageName
            )
        }

        val updated = dao.updateGroup(
            groupId,
            name.trim(),
            timeLimit,
            limitType
        )

        if (updated == 0) {
            return@withTransaction false
        }

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

        if (hasActiveHardLimit(existing)) {

            // Keep removed apps until the next midnight.
            val executeAt = nextMidnightMillis()

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

        val existing = dao.getGroupById(groupId)
            ?: return@withTransaction false

        if (hasActiveHardLimit(existing)) {

            // Schedule deletion for tomorrow at midnight.
            if (pendingDao.getPendingGroupDeletion(groupId) == null) {

                pendingDao.insertPendingChange(
                    PendingGroupChange(
                        groupId = groupId,
                        changeType =
                            PendingChangeType.DELETE_GROUP.name,
                        packageName = null,
                        executeAtMillis = nextMidnightMillis()
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

    // ---------- Process Pending Changes ----------

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

            if (dao.getGroupById(change.groupId) != null) {

                change.packageName?.let { packageName ->
                    dao.deleteGroupApp(
                        change.groupId,
                        packageName
                    )
                }

                processed++
            }

            pendingDao.deletePendingChange(change.id)
        }

        processed
    }
}
