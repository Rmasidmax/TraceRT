package com.example.data.repo

import com.example.data.db.HikeDao
import com.example.data.db.HikeDataPoint
import kotlinx.coroutines.flow.Flow

class HikeRepository(private val hikeDao: HikeDao) {
    val allDataPoints: Flow<List<HikeDataPoint>> = hikeDao.getAllDataPoints()

    suspend fun insertDataPoint(dataPoint: HikeDataPoint) {
        hikeDao.insertDataPoint(dataPoint)
    }

    suspend fun getAllDataPointsSnapshot(): List<HikeDataPoint> {
        return hikeDao.getAllDataPointsSnapshot()
    }

    suspend fun clearAllDataPoints() {
        hikeDao.clearAllDataPoints()
    }

    suspend fun deleteDataPointsByIds(ids: List<Long>) {
        hikeDao.deleteDataPointsByIds(ids)
    }
}
