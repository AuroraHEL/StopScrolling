package com.example.stopscrolling.data.repository

import androidx.room.withTransaction
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.AppGroup
import com.example.stopscrolling.data.GroupApp
import com.example.stopscrolling.data.GroupWithApps
import kotlinx.coroutines.flow.Flow

interface AppGroupRepository {

    fun observeGroups(): Flow<List<GroupWithApps>>

    suspend fun createGroup(
        name: String,
        packageNames: List<String>,
        timeLimit: Int = 0,
        limitType: String = "HARD"
    ): Int
}

class AppGroupRepositoryImpl(
    private val database: AppDatabase
) : AppGroupRepository {

    private val dao = database.appGroupDao()

    override fun observeGroups() =
        dao.observeGroupsWithApps()

    override suspend fun createGroup(
        name: String,
        packageNames: List<String>,
        timeLimit: Int,
        limitType: String
    ): Int = database.withTransaction {

        require(timeLimit >= 0) {
            "Time limit cannot be negative"
        }

        require(limitType == "HARD" || limitType == "SOFT") {
            "Invalid limit type"
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
}
