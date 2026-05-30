package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hike_data_points")
data class HikeDataPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val cumulativeSteps: Int,
    val altitude: Double,
    val batteryPercent: Int = 100,
    val networkStatus: String = "Online"
)
