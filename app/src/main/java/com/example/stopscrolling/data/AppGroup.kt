package com.example.stopscrolling.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_groups")
data class AppGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)