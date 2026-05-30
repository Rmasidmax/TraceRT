package com.example.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.repo.HikePreferences
import com.example.data.repo.HikeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HikeSyncManager {
    private const val TAG = "HikeSyncManager"

    val syncStatus = MutableStateFlow("Idle")
    val latestCsvData = MutableStateFlow<String?>(null)
    val hasPendingSync = MutableStateFlow(false)

    private var isSyncing = false
    private var lastSuccessNotificationTime = 0L

    suspend fun performSync(context: Context): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        synchronized(this@HikeSyncManager) {
            if (isSyncing) {
                Log.d(TAG, "Sync is already in progress, skipping duplicate call.")
                return@withContext false
            }
            isSyncing = true
        }

        try {
            syncStatus.value = "Syncing..."

            val db = AppDatabase.getDatabase(context)
            val repository = HikeRepository(db.hikeDao())
            val points = repository.getAllDataPointsSnapshot()

            if (points.isEmpty()) {
                syncStatus.value = "No logs to sync"
                hasPendingSync.value = false
                synchronized(this@HikeSyncManager) { isSyncing = false }
                return@withContext true
            }

            // SMART SYNC & BATCHING:
            // Bundle coordinates collected over the last 3 to 5 minutes.
            // If active tracking is running, batch updates to avoid flooding the safety contact's email inbox.
            // We wait until we have at least 12 points OR a 3-minute span (180,000 ms) before dispatching.
            // When not tracking (or during a forced finish/sync), we send immediately.
            val oldestPoint = points.firstOrNull()
            val newestPoint = points.lastOrNull()
            val timeSpanMs = if (oldestPoint != null && newestPoint != null) {
                newestPoint.timestamp - oldestPoint.timestamp
            } else {
                0L
            }

            val isTrackingActive = com.example.service.HikeTrackingService.isTracking.value
            if (isTrackingActive && points.size < 12 && timeSpanMs < 180000L) {
                Log.d(TAG, "Batching coordinates: only ${points.size} points spanning ${timeSpanMs / 1000}s. Waiting for a 3-5m bundle to avoid email flooding.")
                syncStatus.value = "Batching (Waiting for 3-5m of data)"
                hasPendingSync.value = true
                synchronized(this@HikeSyncManager) { isSyncing = false }
                return@withContext true
            }

            // Build CSV Data with Battery% and NetworkStatus columns
            val csvBuilder = StringBuilder()
            csvBuilder.append("Timestamp,Latitude,Longitude,Steps,Altitude,Battery%,NetworkStatus\n")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            for (p in points) {
                val timeStr = dateFormat.format(Date(p.timestamp))
                csvBuilder.append("$timeStr,${p.latitude},${p.longitude},${p.cumulativeSteps},${p.altitude},${p.batteryPercent}%,${p.networkStatus}\n")
            }

            val csvResult = csvBuilder.toString()
            latestCsvData.value = csvResult

            val prefs = HikePreferences(context)
            val toEmails = prefs.safetyEmails
            val useAuto = prefs.useAutoSmtp
            val host = prefs.smtpHost
            val port = prefs.smtpPort
            val username = prefs.smtpUsername
            val password = prefs.smtpPassword
            val sender = prefs.smtpSender

            var smtpSuccess = false
            if (useAuto && toEmails.isNotEmpty() && host.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                syncStatus.value = "Sending Automatically..."
                
                // Build interactive trail trace link for Google Maps
                val mapsLink = generateGoogleMapsLink(points)
                val bodyBuilder = java.lang.StringBuilder().apply {
                    append("🏕️ TRACERT AUTOMATIC SAFETY BROADCAST\n")
                    append("=========================================\n\n")
                    append("Hello Safety Contact,\n\n")
                    append("This automated emergency notification has been sent because the hiker's device has successfully restored wireless signal and uploaded the trail data.\n\n")
                    
                    append("🗺️ LIVE INTERACTIVE GOOGLE MAPS TRAIL:\n")
                    append("Click the link below to load and trace the entire physical trail path directly in Google Maps:\n")
                    append("$mapsLink\n\n")
                    
                    append("📍 TRAIL MARKERS & TELEMETRY:\n")
                    val firstPt = points.firstOrNull()
                    if (firstPt != null) {
                        append("🟢 Hike Start Spot (Lat, Lng): ${firstPt.latitude}, ${firstPt.longitude}\n")
                        append("   Open: https://www.google.com/maps/search/?api=1&query=${firstPt.latitude},${firstPt.longitude}\n")
                    }
                    val lastPt = points.lastOrNull()
                    if (lastPt != null) {
                        append("🛰️ Signal Restored Location (Lat, Lng): ${lastPt.latitude}, ${lastPt.longitude}\n")
                        append("   Open: https://www.google.com/maps/search/?api=1&query=${lastPt.latitude},${lastPt.longitude}\n")
                        append("   Altitude: ${lastPt.altitude} meters\n")
                        append("   Total Accumulated Steps: ${lastPt.cumulativeSteps} steps\n")
                    }
                    append("   Total GPS Coordinate Checkpoints Captured: ${points.size}\n\n")
                    
                    val apiKey = com.example.BuildConfig.MAPS_API_KEY
                    if (apiKey.isNotEmpty() && apiKey != "YOUR_GOOGLE_MAPS_API_KEY") {
                        try {
                            val startLoc = points.first()
                            val endLoc = points.last()
                            val downsampled = downsamplePoints(points, 8)
                            val pathPointsStr = downsampled.joinToString("|") { "${it.latitude},${it.longitude}" }
                            val staticMapUrl = "https://maps.googleapis.com/maps/api/staticmap?size=600x400&maptype=hybrid&markers=color:green|label:S|${startLoc.latitude},${startLoc.longitude}&markers=color:red|label:E|${endLoc.latitude},${endLoc.longitude}&path=color:0x00FF66FF|weight:5|$pathPointsStr&key=$apiKey"
                            
                            append("🖼️ STATIC GPS MAP SNAPSHOT:\n")
                            append("Below is the URL to a visual image of the trail satellite background mapping:\n")
                            append("$staticMapUrl\n\n")
                        } catch (e: Exception) {
                            // Suppress
                        }
                    }
                    
                    append("🕒 Sync Completed At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
                    append("This automated safety dispatch helps monitor backcountry treks in near real-time. Please examine the attached CSV file for detailed step-by-step route data.")
                }

                smtpSuccess = SmtpClient.sendEmail(
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    fromEmail = sender.ifEmpty { username },
                    toEmails = toEmails,
                    subject = "🛰️ TraceRT Automatic Trail Logs - ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
                    bodyText = bodyBuilder.toString(),
                    csvFileName = "trail_log_${System.currentTimeMillis()}.csv",
                    csvContent = csvResult
                )
            } else {
                Log.d(TAG, "SMTP auto-config parameters are not fully set; skipping mailing.")
            }

            if (smtpSuccess) {
                syncStatus.value = "Sent Automatically"
                hasPendingSync.value = false
                
                val latestPoint = points.lastOrNull()
                if (latestPoint != null) {
                    prefs.hasSignalRestoredPoint = true
                    prefs.signalRestoredLat = latestPoint.latitude.toFloat()
                    prefs.signalRestoredLng = latestPoint.longitude.toFloat()
                    prefs.lastSyncedTimestamp = latestPoint.timestamp
                }
                
                // TRANSACTIONAL SYNC: ONLY delete those specific records from local SQLite database after successful confirmation
                try {
                    val ids = points.map { it.id }
                    repository.deleteDataPointsByIds(ids)
                    Log.i(TAG, "Successfully cleared ${ids.size} synced records from the transactional local queue.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error performing transactional database deletions for synced points", e)
                }

                showSuccessNotification(context, toEmails)
                synchronized(this@HikeSyncManager) { isSyncing = false }
                return@withContext true
            } else {
                hasPendingSync.value = true
                syncStatus.value = "Sync Pending (Waiting for Network)"
                synchronized(this@HikeSyncManager) { isSyncing = false }
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing sync operation", e)
            syncStatus.value = "Sync Failed"
            synchronized(this@HikeSyncManager) { isSyncing = false }
            return@withContext false
        }
    }

    private fun showSuccessNotification(context: Context, emails: List<String>) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSuccessNotificationTime < 45000L) {
            return
        }
        lastSuccessNotificationTime = currentTime

        val channelId = "HikeSyncChannel"
        val notificationId = 8822

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val destText = emails.joinToString(", ")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Hike Sync Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Logs Auto-Sent!")
            .setContentText("Emergency tracking log has been emailed safely to: $destText.")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun generateGoogleMapsLink(points: List<com.example.data.db.HikeDataPoint>): String {
        if (points.isEmpty()) return ""
        if (points.size == 1) {
            return "https://www.google.com/maps/search/?api=1&query=${points[0].latitude},${points[0].longitude}"
        }

        val start = points.first()
        val end = points.last()

        val intermediate = if (points.size > 2) {
            val sub = points.subList(1, points.size - 1)
            downsamplePoints(sub, 20)
        } else {
            emptyList()
        }

        val originStr = "${start.latitude},${start.longitude}"
        val destinationStr = "${end.latitude},${end.longitude}"
        
        return if (intermediate.isNotEmpty()) {
            val waypointsStr = intermediate.joinToString("%7C") { "${it.latitude},${it.longitude}" }
            "https://www.google.com/maps/dir/?api=1&origin=$originStr&destination=$destinationStr&waypoints=$waypointsStr&travelmode=walking"
        } else {
            "https://www.google.com/maps/dir/?api=1&origin=$originStr&destination=$destinationStr&travelmode=walking"
        }
    }

    private fun downsamplePoints(list: List<com.example.data.db.HikeDataPoint>, maxCount: Int): List<com.example.data.db.HikeDataPoint> {
        if (list.size <= maxCount) return list
        val result = mutableListOf<com.example.data.db.HikeDataPoint>()
        val step = list.size.toDouble() / maxCount
        for (i in 0 until maxCount) {
            val idx = (i * step).toInt().coerceIn(0, list.size - 1)
            val item = list[idx]
            if (!result.contains(item)) {
                result.add(item)
            }
        }
        return result
    }

    suspend fun hasUnsentPoints(context: Context): Boolean {
        try {
            val db = AppDatabase.getDatabase(context)
            val repository = HikeRepository(db.hikeDao())
            val points = repository.getAllDataPointsSnapshot()
            return points.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }
}
