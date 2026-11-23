package com.example.stopscrolling.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PendingChangeType {
    DELETE_GROUP,
    REMOVE_APP
}

@Entity(
    tableName = "pending_group_changes",
    indices = [
        Index(value = ["groupId", "packageName"], unique = true)
    ]
)
data class PendingGroupChange(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val groupId: Int,

    // DELETE_GROUP = delete the entire group
    // REMOVE_APP = remove one app from the group
    val changeType: String,

    // Null when deleting the entire group
    val packageName: String? = null,

    // Timestamp for the next local midnight
    val executeAtMillis: Long
)
