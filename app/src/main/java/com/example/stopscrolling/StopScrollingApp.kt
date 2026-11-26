package com.example.stopscrolling

import android.app.Application
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.repository.AppGroupRepository
import com.example.stopscrolling.data.repository.AppGroupRepositoryImpl
import com.example.stopscrolling.data.repository.InstalledAppsRepository
import com.example.stopscrolling.data.repository.RoomTimeExtensionRepository
import com.example.stopscrolling.data.repository.TimeExtensionRepository
import com.example.stopscrolling.notification.NotificationChannels
import com.example.stopscrolling.usage.DailyUsageProvider

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

    override fun onCreate() {
        super.onCreate()

        NotificationChannels.createHardLimitWarningChannel(this)
    }

    val dailyUsageProvider: DailyUsageProvider by lazy {
        DailyUsageProvider(this)
    }
}