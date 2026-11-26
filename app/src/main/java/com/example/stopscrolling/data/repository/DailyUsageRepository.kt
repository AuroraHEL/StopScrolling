package com.example.stopscrolling.data.repository

import com.example.stopscrolling.data.DailyUsage
import com.example.stopscrolling.data.DailyUsageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DailyUsageRepository {
    suspend fun getByDayStart(dayStartMillis: Long): DailyUsage?
    suspend fun save(dailyUsage: DailyUsage)
    suspend fun getAll(): List<DailyUsage>
}

class RoomDailyUsageRepository(
    private val dao: DailyUsageDao
) : DailyUsageRepository {

    override suspend fun getByDayStart(dayStartMillis: Long): DailyUsage? =
        withContext(Dispatchers.IO) {
            dao.getByDayStart(dayStartMillis)
        }

    override suspend fun save(dailyUsage: DailyUsage) =
        withContext(Dispatchers.IO) {
            dao.upsert(dailyUsage)
        }

    override suspend fun getAll(): List<DailyUsage> =
        withContext(Dispatchers.IO) {
            dao.getAll()
        }
}