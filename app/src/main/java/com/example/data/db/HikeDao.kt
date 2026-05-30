package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HikeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataPoint(dataPoint: HikeDataPoint)

    @Query("SELECT * FROM hike_data_points ORDER BY timestamp ASC")
    fun getAllDataPoints(): Flow<List<HikeDataPoint>>

    @Query("SELECT * FROM hike_data_points ORDER BY timestamp ASC")
    suspend fun getAllDataPointsSnapshot(): List<HikeDataPoint>

    @Query("DELETE FROM hike_data_points")
    suspend fun clearAllDataPoints()

    @Query("DELETE FROM hike_data_points WHERE id IN (:ids)")
    suspend fun deleteDataPointsByIds(ids: List<Long>)
}
