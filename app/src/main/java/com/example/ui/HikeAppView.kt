package com.example.ui

import com.example.data.db.HikeDataPoint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HikeAppView(
    viewModel: HikeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    // Read active tracking & database flows from VM
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val liveSteps by viewModel.liveSteps.collectAsStateWithLifecycle()
    val liveAltitude by viewModel.liveAltitude.collectAsStateWithLifecycle()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val elapsedDisplay by viewModel.elapsedTime.collectAsStateWithLifecycle()
    val loggedPoints by viewModel.allLoggedPoints.collectAsStateWithLifecycle()

    // Email targets & CSV status
    val savedEmail by viewModel.savedEmail.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val hasPendingSync by viewModel.hasPendingSync.collectAsStateWithLifecycle()
    val latestCsvData by viewModel.latestCsvData.collectAsStateWithLifecycle()

    val useAutoSmtp by viewModel.useAutoSmtp.collectAsStateWithLifecycle()
    val smtpHost by viewModel.smtpHost.collectAsStateWithLifecycle()
    val smtpPort by viewModel.smtpPort.collectAsStateWithLifecycle()
    val smtpUsername by viewModel.smtpUsername.collectAsStateWithLifecycle()
    val smtpPassword by viewModel.smtpPassword.collectAsStateWithLifecycle()
    val smtpSender by viewModel.smtpSender.collectAsStateWithLifecycle()

    // Track network state locally using extremely robust NetworkUtils
    var isOnline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var wasOffline = true
        while (true) {
            val online = com.example.sync.NetworkUtils.isOnline(context)
            isOnline = online
            
            if (online) {
                // If transition from offline to online happens, or we have outstanding unsent trail logs, sync them!
                if (wasOffline || com.example.sync.HikeSyncManager.hasUnsentPoints(context)) {
                    android.util.Log.i("HikeAppView", "Internet restored in UI loop! Automatically launching sync to safety contacts.")
                    viewModel.triggerSyncWork()
                }
                wasOffline = false
            } else {
                wasOffline = true
            }
            kotlinx.coroutines.delay(2500)
        }
    }

    // Permission handling
    val requiredPermissions = remember {
        mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(android.Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    var allPermissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        allPermissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!allPermissionsGranted) {
            launcher.launch(requiredPermissions)
        }
    }

    // Scaffold for overall navigation
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A), // Professional Polish Dark Navy-Slate style
                contentColor = Color(0xFF64748B),
                modifier = Modifier.border(width = 1.dp, color = Color(0xFF1E293B))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Home Track Tab") },
                    label = { Text("DASHBOARD", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFA3E635),
                        unselectedIconColor = Color(0xFF64748B),
                        selectedTextColor = Color(0xFFA3E635),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFFA3E635).copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.testTag("nav_tab_track")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Place, contentDescription = "Map Tab") },
                    label = { Text("MAP", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFA3E635),
                        unselectedIconColor = Color(0xFF64748B),
                        selectedTextColor = Color(0xFFA3E635),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFFA3E635).copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.testTag("nav_tab_map")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings Tab") },
                    label = { Text("SETTINGS", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFA3E635),
                        unselectedIconColor = Color(0xFF64748B),
                        selectedTextColor = Color(0xFFA3E635),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFFA3E635).copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "About Tab") },
                    label = { Text("ABOUT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFA3E635),
                        unselectedIconColor = Color(0xFF64748B),
                        selectedTextColor = Color(0xFFA3E635),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFFA3E635).copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.testTag("nav_tab_about")
                )
            }
        }
    ) { innerPadding ->
        if (!allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore, // Compass replacement
                        contentDescription = "Needs permissions",
                        tint = Color(0xFFEF4444), // Crimson accent
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "GPS & Sensors Access Denied",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This offline-first tactical app requires high-precision location logs and step telemetry to guarantee safe back-trail guidance in deep remote areas.",
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { launcher.launch(requiredPermissions) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA3E635)), // Theme neon green
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Grant Hike Safety Permissions", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val hasSignalRestored by viewModel.hasSignalRestoredPoint.collectAsStateWithLifecycle()
                val restoredLat by viewModel.signalRestoredLat.collectAsStateWithLifecycle()
                val restoredLng by viewModel.signalRestoredLng.collectAsStateWithLifecycle()

                when (selectedTab) {
                    0 -> TrackerScreen(
                        isTracking = isTracking,
                        liveSteps = liveSteps,
                        liveAltitude = liveAltitude,
                        latestLocation = latestLocation,
                        elapsedDisplay = elapsedDisplay,
                        loggedPoints = loggedPoints,
                        isOnline = isOnline,
                        onStart = { viewModel.startHike() },
                        onEnd = { viewModel.endHike() }
                    )
                    1 -> MapScreen(
                        loggedPoints = loggedPoints,
                        hasSignalRestored = hasSignalRestored,
                        signalRestoredLat = restoredLat.toDouble(),
                        signalRestoredLng = restoredLng.toDouble()
                    )
                    2 -> SettingsScreen(
                        savedEmail = savedEmail,
                        syncStatus = syncStatus,
                        hasPendingSync = hasPendingSync,
                        latestCsvData = latestCsvData,
                        pointCount = loggedPoints.size,
                        useAutoSmtp = useAutoSmtp,
                        smtpHost = smtpHost,
                        smtpPort = smtpPort,
                        smtpUsername = smtpUsername,
                        smtpPassword = smtpPassword,
                        smtpSender = smtpSender,
                        onSaveEmail = { viewModel.updateSafetyEmail(it) },
                        onUpdateSmtpSettings = { useAuto, host, port, user, pass, sender ->
                            viewModel.updateSmtpSettings(useAuto, host, port, user, pass, sender)
                        },
                        onForceSync = { viewModel.triggerSyncWork() },
                        onClearDatabase = { viewModel.clearLogs() }
                    )
                    3 -> AboutScreen()
                }
            }
        }
    }
}

@Composable
fun TrackerScreen(
    isTracking: Boolean,
    liveSteps: Int,
    liveAltitude: Double,
    latestLocation: android.location.Location?,
    elapsedDisplay: String,
    loggedPoints: List<HikeDataPoint>,
    isOnline: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Calculate dynamic gain based on logged points elevation diff
    val altitudeGain = if (loggedPoints.size > 1) {
        val diff = loggedPoints.last().altitude - loggedPoints.first().altitude
        if (diff > 0.0) diff else 0.0
    } else {
        0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tracker Header Styled as Professional Polish Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "TraceRT",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Offline Hike Tracker",
                    color = Color(0xFFA3E635),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "GPS L1+L5",
                        color = Color(0xFFA3E635),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Pulsing Dot / Neon Indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFA3E635), RoundedCornerShape(50))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Sync Status Banner (Matching mock spec)
        val syncStatusTitle: String
        val syncStatusDesc: String
        val syncStatusColor = Color(0xFF38BDF8) // High vis blue for offline sync status icon
        val syncBorderColor: Color

        if (isTracking) {
            if (!isOnline) {
                syncStatusTitle = "Offline - Saving Locally"
                syncStatusDesc = "${loggedPoints.size} data points cached since signal lost"
                syncBorderColor = Color(0xFF1F2937)
            } else {
                syncStatusTitle = "GPS Link Safe - Online"
                syncStatusDesc = "Active connection monitored. Sync coordinates automatically."
                syncBorderColor = Color(0xFF1F2937)
            }
        } else {
            if (isOnline) {
                syncStatusTitle = "Signal Restored - Ready"
                syncStatusDesc = "Background auto-sync fully functional on telemetry link."
                syncBorderColor = Color(0xFF1F2937)
            } else {
                syncStatusTitle = "Standby - Waiting to Record"
                syncStatusDesc = "Start tracking to log your coordinate vectors."
                syncBorderColor = Color(0xFF1E293B)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, syncBorderColor, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Sync Info",
                        tint = syncStatusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = syncStatusTitle,
                        color = Color(0xFFF1F5F9),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = syncStatusDesc,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Professional Navy-Slate Stats 2x2 Grid Layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elapsed Time Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ELAPSED TIME",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = elapsedDisplay,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Steps Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STEPS",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("%,d", liveSteps),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Barometric Altitude Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CURRENT ALT",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%,d", liveAltitude.toInt()),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "m",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Elevation Gain Card (Dynamic elevation difference)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GAIN",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "+${altitudeGain.toInt()}",
                            color = Color(0xFFA3E635),
                            fontSize = 24.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "m",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vector Breadcrumb Real-Time Map Path View with dot pattern bounds (Professional aesthetic integration)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFF0A0A0A), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(24.dp))
        ) {
            BreadcrumbCanvas(
                hikePoints = loggedPoints,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic bottom-left tactical overlay caption matching prompt
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "OFFLINE BREADCRUMB MODE",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Primary Control Actions (tactile, custom feedback button borders matching mockup)
        if (!isTracking) {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA3E635)), // Neon Yellow-Green
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("start_hike_button")
            ) {
                Icon(Icons.Default.Explore, contentDescription = "Start Icon", tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "START RECORDING",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }
        } else {
            Button(
                onClick = onEnd,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), // Crimson color
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("end_hike_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "END HIKE & SAVE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Coordinates & telemetry segments log status card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "OFFLINE COORD DATAPOINTS",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${loggedPoints.size} trace segments cached in database",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (latestLocation != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Telemetry: Lat ${String.format("%.5f", latestLocation.latitude)}, Lng ${String.format("%.5f", latestLocation.longitude)}",
                        color = Color(0xFFA3E635),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun MapScreen(
    loggedPoints: List<HikeDataPoint>,
    hasSignalRestored: Boolean = false,
    signalRestoredLat: Double = 0.0,
    signalRestoredLng: Double = 0.0
) {
    val context = LocalContext.current
    var mapMode by remember { mutableStateOf(0) } // 0 = Offline breadcrumbs, 1 = Online Satellite tiles
    var isSatelliteEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Tab Layout for Map choice
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (mapMode == 0) Color(0xFF222222) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { mapMode = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OFFLINE BREADCRUMBS",
                    color = if (mapMode == 0) Color(0xFF00FF66) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (mapMode == 1) Color(0xFF222222) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { mapMode = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ONLINE TOPO MAP",
                    color = if (mapMode == 1) Color(0xFF00FF66) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Map Content View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (mapMode == 0) {
                BreadcrumbCanvas(
                    hikePoints = loggedPoints,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                ) {
                    AndroidMapView(
                        hikePoints = loggedPoints,
                        isSatellite = isSatelliteEnabled,
                        hasSignalRestored = hasSignalRestored,
                        signalRestoredLat = signalRestoredLat,
                        signalRestoredLng = signalRestoredLng,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating button to open entire route trace in external Google Maps app
                    if (loggedPoints.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val gmapsUrl = com.example.sync.HikeSyncManager.generateGoogleMapsLink(loggedPoints)
                                    val intentUri = android.net.Uri.parse(gmapsUrl)
                                    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, intentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        // Fallback if google maps application package is not installed
                                        try {
                                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, intentUri))
                                        } catch (ex: Exception) {
                                            // Ignore
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Open in Google Maps App",
                                tint = Color(0xFF00FF66),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GOOGLE MAPS APP",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Floating toggle maps styling (Satellite vs Normal type layouts)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .clickable { isSatelliteEnabled = !isSatelliteEnabled }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Toggle Topology Map Type",
                            tint = Color(0xFF00FF66),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSatelliteEnabled) "SATELLITE" else "NORMAL",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFA3E635))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (mapMode == 0) "Offline Breadcrumb Compass View" else "Online Topology Map overlays",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (mapMode == 0) "Vector plotting path scaling coordinates to fit screen offline" else "Satellite high-accuracy maps rendering via Play Services GPS",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    savedEmail: String,
    syncStatus: String,
    hasPendingSync: Boolean,
    latestCsvData: String?,
    pointCount: Int,
    useAutoSmtp: Boolean,
    smtpHost: String,
    smtpPort: Int,
    smtpUsername: String,
    smtpPassword: String,
    smtpSender: String,
    onSaveEmail: (String) -> Unit,
    onUpdateSmtpSettings: (Boolean, String, Int, String, String, String) -> Unit,
    onForceSync: () -> Unit,
    onClearDatabase: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "SAFETY CONTACT PREFERENCES",
            color = Color(0xFFA3E635), // Professional Polish Neon Green
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Emergency Dispatch",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-email address management chips
        val emailList = remember(savedEmail) {
            savedEmail.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        var newEmailInput by remember { mutableStateOf("") }
        var isEmailError by remember { mutableStateOf(false) }

        if (emailList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No safety contacts added yet. Enter an email below.",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            emailList.forEach { email ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = "Email Icon",
                                tint = Color(0xFFA3E635),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = email,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = {
                                val newList = emailList.toMutableList().apply { remove(email) }
                                onSaveEmail(newList.joinToString(","))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Email",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input row to add dynamic emails
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newEmailInput,
                onValueChange = { 
                    newEmailInput = it
                    isEmailError = false
                },
                isError = isEmailError,
                label = { Text("Add Safety Recipient Email") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFA3E635),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedLabelColor = Color(0xFFA3E635),
                    unfocusedLabelColor = Color(0xFF64748B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    errorBorderColor = Color.Red
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("safety_email_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val target = newEmailInput.trim()
                    if (target.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches()) {
                        val newList = emailList.toMutableList().apply { 
                            if (!contains(target)) add(target)
                        }
                        onSaveEmail(newList.joinToString(","))
                        newEmailInput = ""
                    } else {
                        isEmailError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA3E635)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("save_email_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Contact",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "🛰️ OUTGOING SMTP DISPATCH ENGINE",
            color = Color(0xFFA3E635),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Automated Backcountry Sync",
            color = Color.LightGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        var hostInput by remember { mutableStateOf(smtpHost) }
        var portInput by remember { mutableStateOf(smtpPort.toString()) }
        var userInput by remember { mutableStateOf(smtpUsername) }
        var passInput by remember { mutableStateOf(smtpPassword) }
        var senderInput by remember { mutableStateOf(smtpSender) }
        var passwordVisible by remember { mutableStateOf(false) }

        val smtpIsModified = hostInput != smtpHost ||
                portInput != smtpPort.toString() ||
                userInput != smtpUsername ||
                passInput != smtpPassword ||
                senderInput != smtpSender

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SMTP Server Credentials",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Automated satellite/restored link SMTP server dispatch profiles.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SMTP Server Configurations",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("SMTP Server Host") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA3E635),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedLabelColor = Color(0xFFA3E635),
                                unfocusedLabelColor = Color(0xFF64748B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA3E635),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedLabelColor = Color(0xFFA3E635),
                                unfocusedLabelColor = Color(0xFF64748B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text("SMTP Account Username") },
                        placeholder = { Text("e.g. your_email@gmail.com") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA3E635),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = Color(0xFFA3E635),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("SMTP Password (for Gmail: App Password)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA3E635),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = Color(0xFFA3E635),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (passwordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = Color.Gray)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text("Sender Email Address (Optional)") },
                        placeholder = { Text("Falls back to username if empty") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA3E635),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedLabelColor = Color(0xFFA3E635),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tip: For Google (Gmail), please configure an 'App Password' via your Google Account's Security controls under 2-Step Verification. Direct primary login passwords will be blocked.",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                if (smtpIsModified) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val parsedPort = portInput.toIntOrNull() ?: 465
                            onUpdateSmtpSettings(
                                true,
                                hostInput.trim(),
                                parsedPort,
                                userInput.trim(),
                                passInput,
                                senderInput.trim()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA3E635)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Outgoing SMTP Configuration", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SYNCHRONIZATION STATISTICS",
            color = Color(0xFFA3E635),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Database logged items:", color = Color(0xFF64748B), fontSize = 13.sp)
                    Text("$pointCount rows", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Connection background Sync:", color = Color(0xFF64748B), fontSize = 13.sp)
                    Text(syncStatus, color = Color(0xFFA3E635), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (hasPendingSync && latestCsvData != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("PREPARED LOG FLUSH SUMMARY (CSV):", color = Color(0xFF38BDF8), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = latestCsvData,
                            color = Color(0xFFA3E635),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tactical debug database tools
        Text(
            text = "TACTICAL STORAGE CONTEXT",
            color = Color(0xFFEF4444),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onForceSync,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .testTag("force_sync_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Force Sync Test", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onClearDatabase,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221111)),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .testTag("clear_db_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Clear Room Logs", color = Color(0xFFFF9999), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ABOUT TRACERT DEVICE",
            color = Color(0xFFA3E635),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .testTag("about_device_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFA3E635).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFA3E635).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "TraceRT Logo",
                            tint = Color(0xFFA3E635),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "TraceRT",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Offline Hike Tracker",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(16.dp))

                // Developer's Email Line
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Developer Support:", color = Color(0xFF64748B), fontSize = 13.sp)
                    Text(text = "buildnchill.tech@gmail.com", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }

                // Contact number Line
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Contact Hotline:", color = Color(0xFF64748B), fontSize = 13.sp)
                    Text(text = "09922073188", color = Color(0xFFA3E635), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(12.dp))

                // Copyright Statement
                Text(
                    text = "© 2026 TraceRT. All rights reserved.\nDesigned for telemetry navigation & hiker safety.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
