package com.example.stopscrolling

import android.app.Application
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import com.example.stopscrolling.data.repository.CapabilityPreferencesRepository
import com.example.stopscrolling.data.repository.CapabilityPreferencesRepositoryImpl
import com.example.stopscrolling.data.repository.InstalledAppsRepository
import com.example.stopscrolling.data.repository.RoomTimeExtensionRepository
import com.example.stopscrolling.data.repository.TimeExtensionRepository

class StopScrollingApp : Application() {

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val appGroupRepository: AppGroupRepository by lazy {
        AppGroupRepositoryImpl(database)
    }

    val installedAppsRepository: InstalledAppsRepository by lazy {
        InstalledAppsRepository(this)
    }

    val extensionRepository: TimeExtensionRepository by lazy {
        RoomTimeExtensionRepository(database.timeExtensionDao())
    }

    // US-03: Capability preferences repository
    val capabilityPreferencesRepository: CapabilityPreferencesRepository by lazy {
        CapabilityPreferencesRepositoryImpl(this)
    }
}
