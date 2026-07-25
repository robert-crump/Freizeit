package com.example.freizeit.data.geofence

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.freizeit.util.AutoCheckInPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires [onSignificantChange] whenever the device moves roughly [SIGNIFICANT_DISPLACEMENT_METERS]
 * — the sole trigger for re-ranking which favorites get a geofence once there are more than 100
 * of them (issue #29). Deliberately not app-open, not a periodic job: [start]/[stop] just arm or
 * disarm this listener, they never rank anything themselves.
 */
class GeofenceLocationMonitor(
    private val context: Context,
    private val onSignificantChange: suspend (Location) -> Unit
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch { onSignificantChange(location) }
        }
    }

    fun start() {
        if (isRunning || !AutoCheckInPermissions.hasForegroundLocation(context)) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(SIGNIFICANT_DISPLACEMENT_METERS)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            isRunning = true
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the call below.
        }
    }

    fun stop() {
        fusedClient.removeLocationUpdates(callback)
        isRunning = false
    }

    private companion object {
        /** Roughly "moved to a new neighborhood" — enough to plausibly change which 100 favorites
         * are closest, without chasing every GPS jitter at rest. */
        const val SIGNIFICANT_DISPLACEMENT_METERS = 500f
        const val UPDATE_INTERVAL_MILLIS = 5 * 60_000L
    }
}
