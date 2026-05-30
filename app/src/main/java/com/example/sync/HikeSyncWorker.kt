package com.example.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.repo.HikePreferences
import com.example.data.repo.HikeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HikeSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        val syncStatus = HikeSyncManager.syncStatus
        val latestCsvData = HikeSyncManager.latestCsvData
        val hasPendingSync = HikeSyncManager.hasPendingSync
    }

    override suspend fun doWork(): Result {
        HikeSyncManager.performSync(applicationContext)
        return Result.success()
    }
}
