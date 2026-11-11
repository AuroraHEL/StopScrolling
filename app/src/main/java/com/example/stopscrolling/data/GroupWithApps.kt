package com.example.stopscrolling.data

import androidx.room.Embedded
import androidx.room.Relation

//A group with its list of apps for the dashboard
data class GroupWithApps(
    @Embedded val group: AppGroup,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val apps: List<GroupApp>
)