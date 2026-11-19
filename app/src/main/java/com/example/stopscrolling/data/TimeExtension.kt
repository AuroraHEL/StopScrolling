package com.example.stopscrolling.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.stopscrolling.ui.overlay.EXTENSION_MINUTES

@Entity(tableName = "time_extensions")
data class TimeExtension(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val groupId: Int,
    val dayStartMillis: Long,
    val grantedMinutes: Int = EXTENSION_MINUTES
)