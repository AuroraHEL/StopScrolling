package com.example.stopscrolling

import android.app.Application
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import com.example.stopscrolling.data.repository.InstalledAppsRepository

class StopScrollingApp : Application() {

    val appGroupRepository: AppGroupRepository by lazy {
        AppGroupRepositoryImpl(AppDatabase.getInstance(this))
    }

    val installedAppsRepository: InstalledAppsRepository by lazy {
        InstalledAppsRepository(this)
    }
}