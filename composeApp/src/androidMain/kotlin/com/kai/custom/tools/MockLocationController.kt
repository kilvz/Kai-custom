package com.kai.custom.tools

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.*
import kotlin.math.*

object MockLocationController {
    private var mockJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startMocking(
        context: Context,
        startLat: Double,
        startLng: Double,
        destLat: Double? = null,
        destLng: Double? = null,
        speedKmh: Double? = null
    ): String {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        fun removeExistingTestProviders() {
            try {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {}
            try {
                locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {}
        }

        removeExistingTestProviders()

        try {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true,
                @Suppress("DEPRECATION") android.location.Criteria.POWER_HIGH,
                @Suppress("DEPRECATION") android.location.Criteria.ACCURACY_FINE,
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        } catch (e: SecurityException) {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Kai is not set as the mock location provider. Developer Options have been opened — please go to 'Select mock location app' and choose Kai."
        }

        mockJob?.cancel()

        mockJob = scope.launch {
            var currentLat = startLat
            var currentLng = startLng

            val speedMps = (speedKmh ?: 5.0) * (1000.0 / 3600.0) // Convert km/h to m/s
            val updateIntervalMs = 500L
            val stepDistance = speedMps * (updateIntervalMs / 1000.0) // Distance per tick in meters

            while (isActive) {
                if (destLat != null && destLng != null) {
                    val dist = haversineDistance(currentLat, currentLng, destLat, destLng)
                    if (dist > stepDistance) {
                        // Move one step closer
                        val bearing = calculateBearing(currentLat, currentLng, destLat, destLng)
                        val nextPoint = calculateDestinationPoint(currentLat, currentLng, bearing, stepDistance)
                        currentLat = nextPoint.first
                        currentLng = nextPoint.second
                    } else {
                        // Arrived at destination
                        currentLat = destLat
                        currentLng = destLng
                    }
                }

                val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
                    this.latitude = currentLat
                    this.longitude = currentLng
                    accuracy = 1.0f
                    time = System.currentTimeMillis()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                    }
                }

                try {
                    locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
                } catch (e: Exception) {
                    // Ignore transient errors if provider was removed
                }

                delay(updateIntervalMs)
            }
        }

        return if (destLat != null && destLng != null) {
            "Started moving from ($startLat, $startLng) to ($destLat, $destLng) at ${speedKmh ?: 5.0} km/h."
        } else {
            "Started mocking GPS location at ($startLat, $startLng)."
        }
    }

    fun stopMocking(context: Context): String {
        mockJob?.cancel()
        mockJob = null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {}
        return "Stopped mocking GPS location. Real GPS will now resume."
    }

    // --- Math helpers ---

    // Returns distance in meters
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // Returns bearing in degrees
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(radLat2)
        val x = cos(radLat1) * sin(radLat2) - sin(radLat1) * cos(radLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    // Returns (lat, lon)
    private fun calculateDestinationPoint(lat: Double, lon: Double, bearingDeg: Double, distanceMeters: Double): Pair<Double, Double> {
        val r = 6371000.0
        val brng = Math.toRadians(bearingDeg)
        val radLat = Math.toRadians(lat)
        val radLon = Math.toRadians(lon)

        val destLat = asin(sin(radLat) * cos(distanceMeters / r) + cos(radLat) * sin(distanceMeters / r) * cos(brng))
        val destLon = radLon + atan2(sin(brng) * sin(distanceMeters / r) * cos(radLat), cos(distanceMeters / r) - sin(radLat) * sin(destLat))

        return Pair(Math.toDegrees(destLat), Math.toDegrees(destLon))
    }
}
