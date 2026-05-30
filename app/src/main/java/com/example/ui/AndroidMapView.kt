package com.example.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.db.HikeDataPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

@Composable
fun AndroidMapView(
    hikePoints: List<HikeDataPoint>,
    isSatellite: Boolean,
    hasSignalRestored: Boolean = false,
    signalRestoredLat: Double = 0.0,
    signalRestoredLng: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    // Synchronize the MapView with Compose lifecycle hooks to prevent leaks or crash overhead
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    try {
                        mapView.onCreate(Bundle())
                    } catch (e: Exception) {
                        // Suppress if already created
                    }
                }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (e: Exception) {
                // Ignore any teardown exceptions
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    ) { view ->
        view.getMapAsync { googleMap ->
            googleMap.clear()
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isCompassEnabled = true

            // Set satellite map representation isSatellite is toggled online
            googleMap.mapType = if (isSatellite) {
                GoogleMap.MAP_TYPE_SATELLITE
            } else {
                GoogleMap.MAP_TYPE_NORMAL
            }

            if (hikePoints.isNotEmpty()) {
                val path = hikePoints.map { LatLng(it.latitude, it.longitude) }

                // Find index matching signal restored point Closest
                var restoredIndex = -1
                if (hasSignalRestored && signalRestoredLat != 0.0 && signalRestoredLng != 0.0) {
                    restoredIndex = hikePoints.indexOfFirst {
                        val latDiff = Math.abs(it.latitude - signalRestoredLat)
                        val lngDiff = Math.abs(it.longitude - signalRestoredLng)
                        latDiff < 1e-4 && lngDiff < 1e-4
                    }
                    if (restoredIndex == -1) {
                        var minDist = Double.MAX_VALUE
                        for (i in hikePoints.indices) {
                            val pt = hikePoints[i]
                            val d = Math.pow(pt.latitude - signalRestoredLat, 2.0) + Math.pow(pt.longitude - signalRestoredLng, 2.0)
                            if (d < minDist) {
                                minDist = d
                                restoredIndex = i
                            }
                        }
                    }
                }

                // Trace colors:
                // 1) From start to signal restored point: Amber Orange or Vibrant Cyan (representing the sent route)
                // 2) From signal restored point to the current position: Bright Neon Green (representing current route segment since signal restore)
                
                if (restoredIndex != -1 && restoredIndex < path.size) {
                    val sentSegment = path.subList(0, restoredIndex + 1)
                    val activeSegment = path.subList(restoredIndex, path.size)

                    // Draw Sent Route Segment (Vibrant Blue/Azure trace)
                    if (sentSegment.size >= 2) {
                        googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(sentSegment)
                                .color(0xFF00B0FF.toInt()) // Azure Blue Hex representing uploaded trace
                                .width(12f)
                        )
                    }

                    // Draw Active Continuation Segment (Neon Green trace)
                    if (activeSegment.size >= 2) {
                        googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(activeSegment)
                                .color(0xFF00FF66.toInt()) // Neon Green Hex
                                .width(10f)
                        )
                    } else {
                        // Just draw the full path as fallback
                        googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(path)
                                .color(0xFF00FF66.toInt())
                                .width(10f)
                        )
                    }

                    // Add Signal Restored Marker
                    val restoredLatLng = LatLng(signalRestoredLat, signalRestoredLng)
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(restoredLatLng)
                            .title("🛰️ Signal Restored & Logs Sent")
                            .snippet("Automatic background dispatch spot")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                } else {
                    // Draw Full Hike Path (Standard Neon safety color since no restored checkpoint exists yet)
                    googleMap.addPolyline(
                        PolylineOptions()
                            .addAll(path)
                            .color(0xFF00FF66.toInt())
                            .width(10f)
                    )
                }

                // Display multiple intermediate markings (checkpoints) along the route to trace the hike visually
                if (hikePoints.size > 2) {
                    val maxMarkers = 15
                    val checkpointInterval = (hikePoints.size / maxMarkers).coerceAtLeast(1)
                    for (i in 1 until hikePoints.size - 1 step checkpointInterval) {
                        val pt = hikePoints[i]
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(pt.latitude, pt.longitude))
                                .title("Checkpoint #${i + 1}")
                                .snippet("Steps: ${pt.cumulativeSteps} | Altitude: ${pt.altitude.toInt()}m")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                                .alpha(0.85f)
                        )
                    }
                }

                // Start point marker (Green)
                val start = path.first()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(start)
                        .title("Hike Start Point")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )

                // Current latest position marker (Red)
                val latest = path.last()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(latest)
                        .title("Your Current Position")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )

                // Center camera precisely around latest position with reasonable zoom
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latest, 15f))
            }
        }
    }
}
