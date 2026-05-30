package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.db.HikeDataPoint
import com.example.data.repo.HikeRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HikeTrackingService : Service(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    
    private var stepCounterSensor: Sensor? = null
    private var barometerSensor: Sensor? = null

    private var baselineSteps = -1
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var currentSessionSteps = 0
    private var currentAltitude = 0.0
    private var lastRecordedSteps = -1
    private var lastRecordedLocation: Location? = null
    private var receivedStepEvent = false
    private var lastNotificationUpdateTime = 0L
    private var currentIntervalMillis = 15000L

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { HikeRepository(database.hikeDao()) }

    companion object {
        const val CHANNEL_ID = "HikeTrackingChannel"
        const val NOTIFICATION_ID = 4591
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        // Static tracking state for real-time Jetpack Compose observations
        val isTracking = MutableStateFlow(false)
        val currentSteps = MutableStateFlow(0)
        val currentAltitudeFlow = MutableStateFlow(0.0)
        val latestLocation = MutableStateFlow<Location?>(null)
        val startTime = MutableStateFlow(0L)
        val sessionPoints = MutableStateFlow<List<HikeDataPoint>>(emptyList())
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        barometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (isTracking.value) return
        isTracking.value = true
        startTime.value = System.currentTimeMillis()
        baselineSteps = -1
        currentSessionSteps = 0
        currentSteps.value = 0
        currentAltitude = 0.0
        currentAltitudeFlow.value = 0.0
        lastRecordedSteps = -1
        lastRecordedLocation = null
        receivedStepEvent = false
        lastNotificationUpdateTime = 0L
        currentIntervalMillis = 15000L
        sessionPoints.value = emptyList()
        serviceScope.launch {
            try {
                val initialPoints = repository.getAllDataPointsSnapshot()
                sessionPoints.value = initialPoints
            } catch (e: Exception) {
                android.util.Log.e("HikeTrackingService", "Error loading initial data points on start", e)
            }
        }

        // Register network callback to automatically sync when signal restores in background state
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    serviceScope.launch {
                        val online = com.example.sync.NetworkUtils.isOnline(applicationContext)
                        android.util.Log.d("HikeTrackingService", "Network onAvailable! isOnline=$online")
                        if (online) {
                            android.util.Log.i("HikeTrackingService", "Internet restored while tracking in background! Initiating auto email dispatch.")
                            com.example.sync.HikeSyncManager.performSync(applicationContext)
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                    serviceScope.launch {
                        val online = com.example.sync.NetworkUtils.isOnline(applicationContext)
                        android.util.Log.d("HikeTrackingService", "Network onCapabilitiesChanged! isOnline=$online")
                        if (online) {
                            android.util.Log.i("HikeTrackingService", "Network became active with internet validating! Triggering auto email dispatch.")
                            com.example.sync.HikeSyncManager.performSync(applicationContext)
                        }
                    }
                }
            }
            networkCallback = callback
            try {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } catch (e: Exception) {
                android.util.Log.e("HikeTrackingService", "Error registering default network callback", e)
            }
        }

        // Continuous automatic sync check loop running every 12 seconds in service background
        serviceScope.launch {
            while (isTracking.value) {
                try {
                    val online = com.example.sync.NetworkUtils.isOnline(applicationContext)
                    if (online) {
                        android.util.Log.i("HikeTrackingService", "Continuous auto-sync check is online! Dispatching safety email.")
                        com.example.sync.HikeSyncManager.performSync(applicationContext)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HikeTrackingService", "Error in continuous auto-sync daemon", e)
                }
                kotlinx.coroutines.delay(12000) // 12 seconds intervals for quick, low-battery continuous retries
            }
        }

        // Register sensors
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        barometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        startLocationTracking()

        // Start Foreground Service
        val notification = buildNotification("Starting GPS tracking...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopTracking() {
        isTracking.value = false
        sensorManager.unregisterListener(this)
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: SecurityException) {
            // handle permission restriction elegantly
        }

        // Unregister network callback safely
        networkCallback?.let { callback ->
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore
            }
            networkCallback = null
        }

        stopForeground(true)
        stopSelf()
    }

    private fun startLocationTracking() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            15000L // 15 seconds to save battery
        ).apply {
            setMinUpdateIntervalMillis(10000L) // 10 seconds min
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            // No permissions, handled by UI
            stopTracking()
        }
    }

    private fun getBatteryPercentage(context: Context): Int {
        try {
            val batteryStatus: Intent? = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            return if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                100
            }
        } catch (e: Exception) {
            return 100
        }
    }

    private fun getNetworkStatus(context: Context): String {
        return if (com.example.sync.NetworkUtils.isOnline(context)) "Online" else "Offline"
    }

    private fun updateLocationRequestInterval(newInterval: Long) {
        if (newInterval == currentIntervalMillis) return
        currentIntervalMillis = newInterval
        android.util.Log.d("HikeTrackingService", "Updating location request interval to: ${newInterval}ms")
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                newInterval
            ).apply {
                setMinUpdateIntervalMillis(newInterval / 2)
            }.build()
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: Exception) {
            android.util.Log.e("HikeTrackingService", "Exception during updateLocationRequestInterval", e)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            latestLocation.value = location

            // If barometer isn't available or hasn't recorded yet, fall back to GPS altitude
            val altToSave = if (currentAltitude != 0.0) currentAltitude else location.altitude

            // Update live flow
            currentAltitudeFlow.value = altToSave

            // Update notification
            updateNotification("Steps: $currentSessionSteps | Alt: ${altToSave.toInt()}m")

            // Scale location update intervals adaptively based on speed or movement
            val speed = if (location.hasSpeed()) location.speed else 0.0f
            val targetInterval = when {
                speed >= 3.0f -> 8000L      // Moving fast (Running/Cycling/Driving): 8s higher frequency
                speed >= 0.5f -> 15000L     // Walking/Hiking: 15s intermediate frequency
                else -> 30000L               // Stationary/Very slow: 30s lower frequency
            }
            updateLocationRequestInterval(targetInterval)

            // Determine if the coordinate point should actually be registered/saved to avoid jitter and redundant stationary records
            var shouldRecord = false
            val isFirstPoint = (lastRecordedSteps == -1 || lastRecordedLocation == null)
            
            if (isFirstPoint) {
                shouldRecord = true
            } else {
                val lastLoc = lastRecordedLocation!!
                val distance = location.distanceTo(lastLoc)
                
                // Drop redundant updates if stationary using a distance threshold of 10 meters
                if (distance < 10.0f) {
                    shouldRecord = false
                } else {
                    shouldRecord = true
                }
            }

            if (shouldRecord) {
                lastRecordedSteps = currentSessionSteps
                lastRecordedLocation = location

                val battery = getBatteryPercentage(applicationContext)
                val netStatus = getNetworkStatus(applicationContext)

                val newPoint = HikeDataPoint(
                    timestamp = System.currentTimeMillis(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    cumulativeSteps = currentSessionSteps,
                    altitude = altToSave,
                    batteryPercent = battery,
                    networkStatus = netStatus
                )

                // Persist locally
                serviceScope.launch {
                    repository.insertDataPoint(newPoint)
                    sessionPoints.value = sessionPoints.value + newPoint

                    // Check connectivity state on either WiFi or mobile cell data using robust NetworkUtils
                    val isOnline = com.example.sync.NetworkUtils.isOnline(applicationContext)

                    if (isOnline) {
                        // Dispatch automated safety audit email instantly! Bypasses WorkManager delays on WiFi/Mobile Data
                        com.example.sync.HikeSyncManager.performSync(applicationContext)
                    } else {
                        com.example.sync.HikeSyncManager.hasPendingSync.value = true
                        com.example.sync.HikeSyncManager.syncStatus.value = "Sync Pending (Waiting for Network)"
                    }

                    // Enqueue background auto sync worker to trigger sending when network connection recovers or is validated later
                    val constraints = androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()

                    val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.sync.HikeSyncWorker>()
                        .setConstraints(constraints)
                        .build()

                    androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "HikeSyncWorkUnique",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        syncRequest
                    )
                }
            }
        }
    }

    // SensorEventListener overrides
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                receivedStepEvent = true
                val totalSteps = event.values[0].toInt()
                if (baselineSteps == -1) {
                    baselineSteps = totalSteps
                }
                currentSessionSteps = totalSteps - baselineSteps
                currentSteps.value = currentSessionSteps
                updateNotificationContent()
            }
            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values[0]
                // Compute altitude based on pressure
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure).toDouble()
                currentAltitude = alt
                currentAltitudeFlow.value = alt
                updateNotificationContent()
            }
        }
    }

    private fun updateNotificationContent() {
        val altDisplay = if (currentAltitude != 0.0) currentAltitude else (latestLocation.value?.altitude ?: 0.0)
        updateNotification("Steps: $currentSessionSteps | Alt: ${altDisplay.toInt()}m")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(content: String): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hike Tracking Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationUpdateTime < 4000L) {
            return
        }
        lastNotificationUpdateTime = currentTime
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
        } catch (e: Exception) {
            // Guard against system level exceptions
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Hike Tracking Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays live tracking details for safety"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
