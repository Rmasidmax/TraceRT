package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.db.AppDatabase
import com.example.data.db.HikeDataPoint
import com.example.data.repo.HikePreferences
import com.example.data.repo.HikeRepository
import com.example.service.HikeTrackingService
import com.example.sync.HikeSyncWorker
import android.content.SharedPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HikeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = HikeRepository(db.hikeDao())
    private val prefs = HikePreferences(application)

    // Bound parameters to static foreground service states
    val isTracking = HikeTrackingService.isTracking
    val liveSteps = HikeTrackingService.currentSteps
    val liveAltitude = HikeTrackingService.currentAltitudeFlow
    val latestLocation = HikeTrackingService.latestLocation
    val startTime = HikeTrackingService.startTime

    // Signal Restored details
    val hasSignalRestoredPoint = MutableStateFlow(prefs.hasSignalRestoredPoint)
    val signalRestoredLat = MutableStateFlow(prefs.signalRestoredLat)
    val signalRestoredLng = MutableStateFlow(prefs.signalRestoredLng)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "has_signal_restored_point" || key == "signal_restored_lat" || key == "signal_restored_lng") {
            hasSignalRestoredPoint.value = prefs.hasSignalRestoredPoint
            signalRestoredLat.value = prefs.signalRestoredLat
            signalRestoredLng.value = prefs.signalRestoredLng
        }
    }

    // Reactive Flow of all database records or active session trace segments
    val allLoggedPoints: StateFlow<List<HikeDataPoint>> = kotlinx.coroutines.flow.combine(
        repository.allDataPoints,
        HikeTrackingService.sessionPoints,
        isTracking
    ) { dbPoints, sessionPoints, tracking ->
        if (tracking) {
            sessionPoints
        } else {
            dbPoints
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Email targets
    val savedEmail = MutableStateFlow(prefs.safetyEmail)
    val useAutoSmtp = MutableStateFlow(prefs.useAutoSmtp)
    val smtpHost = MutableStateFlow(prefs.smtpHost)
    val smtpPort = MutableStateFlow(prefs.smtpPort)
    val smtpUsername = MutableStateFlow(prefs.smtpUsername)
    val smtpPassword = MutableStateFlow(prefs.smtpPassword)
    val smtpSender = MutableStateFlow(prefs.smtpSender)

    // Seconds display string ticker
    val elapsedTime = MutableStateFlow("00:00:00")

    // WorkManager status observer
    val syncStatus = HikeSyncWorker.syncStatus
    val hasPendingSync = HikeSyncWorker.hasPendingSync
    val latestCsvData = HikeSyncWorker.latestCsvData

    init {
        prefs.sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        // Run active timer loops while service tracks
        viewModelScope.launch {
            while (true) {
                if (isTracking.value && startTime.value > 0L) {
                    val ms = System.currentTimeMillis() - startTime.value
                    elapsedTime.value = formatDuration(ms)
                } else {
                    elapsedTime.value = "00:00:00"
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        prefs.sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onCleared()
    }

    fun updateSafetyEmail(email: String) {
        prefs.safetyEmail = email
        savedEmail.value = prefs.safetyEmail
    }

    fun updateSmtpSettings(
        useAuto: Boolean,
        host: String,
        port: Int,
        user: String,
        pass: String,
        senderEmail: String
    ) {
        prefs.useAutoSmtp = useAuto
        prefs.smtpHost = host
        prefs.smtpPort = port
        prefs.smtpUsername = user
        prefs.smtpPassword = pass
        prefs.smtpSender = senderEmail

        useAutoSmtp.value = useAuto
        smtpHost.value = host
        smtpPort.value = port
        smtpUsername.value = user
        smtpPassword.value = pass
        smtpSender.value = senderEmail
    }

    fun startHike() {
        val context = getApplication<Application>().applicationContext
        
        // Reset last signal restored coordinate tracker
        prefs.hasSignalRestoredPoint = false
        prefs.signalRestoredLat = 0.0f
        prefs.signalRestoredLng = 0.0f
        prefs.lastSyncedTimestamp = 0L
        
        val serviceIntent = Intent(context, HikeTrackingService::class.java).apply {
            action = HikeTrackingService.ACTION_START
        }
        context.startService(serviceIntent)
    }

    fun endHike() {
        val context = getApplication<Application>().applicationContext
        val serviceIntent = Intent(context, HikeTrackingService::class.java).apply {
            action = HikeTrackingService.ACTION_STOP
        }
        context.startService(serviceIntent)

        // Force work trigger when Ending Hike
        triggerSyncWork()
    }

    fun triggerSyncWork() {
        val context = getApplication<Application>().applicationContext
        
        // Try direct, immediate sync in foreground coroutine if online (bypasses WorkManager delays)
        viewModelScope.launch {
            com.example.sync.HikeSyncManager.performSync(context)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<HikeSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllDataPoints()
            HikeTrackingService.sessionPoints.value = emptyList()
            HikeSyncWorker.hasPendingSync.value = false
            HikeSyncWorker.latestCsvData.value = null
            HikeSyncWorker.syncStatus.value = "Cleared"
            
            // Clear last signal restored coordinates
            prefs.hasSignalRestoredPoint = false
            prefs.signalRestoredLat = 0.0f
            prefs.signalRestoredLng = 0.0f
            prefs.lastSyncedTimestamp = 0L
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSecs = millis / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
