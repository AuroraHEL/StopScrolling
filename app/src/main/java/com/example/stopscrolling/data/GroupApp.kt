package com.example.stopscrolling.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_apps")
data class GroupApp(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val groupId: Int,
    val packageName: String,
    // US-09: true means this app will be removed from the group at midnight
    val pendingRemoval: Boolean = false
)