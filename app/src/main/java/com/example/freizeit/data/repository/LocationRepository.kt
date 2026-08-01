package com.example.freizeit.data.repository

import android.content.Context
import android.os.Looper
import com.example.freizeit.util.LatLon
import com.example.freizeit.util.LocationHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Single shared source of the user's current location (issue #40) — replaces three independent
 * [LocationHelper] reads previously duplicated across MapViewModel/HomeViewModel/CheckInViewModel,
 * which could each show a different stale fix at the same moment. [refreshOnce] is a one-shot
 * last-known-location read (cheap; used by Home/Check-in on resume and by Map's "locate me" FAB).
 * [startContinuous]/[stopContinuous] arm/disarm an active GPS stream — used only while the Map
 * screen is on-screen (its composable lifecycle gates start/stop), since that's the only surface
 * where a live position matters; Home/Check-in only ever snapshot this flow's current value at a
 * refresh point, they never collect it continuously, so a Map-driven update can't reshuffle Home's
 * swipe deck mid-session (#34).
 */
class LocationRepository(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<LatLon?>(null)
    val location: StateFlow<LatLon?> = _location.asStateFlow()

    private var isContinuous = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _location.value = LatLon(loc.latitude, loc.longitude, loc.accuracy.takeIf { it > 0f })
        }
    }

    suspend fun refreshOnce() {
        _location.value = withContext(Dispatchers.IO) { LocationHelper.lastKnownLocation(context) }
    }

    fun startContinuous() {
        if (isContinuous || !LocationHelper.hasPermission(context)) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            isContinuous = true
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the call below.
        }
    }

    fun stopContinuous() {
        fusedClient.removeLocationUpdates(callback)
        isContinuous = false
    }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 10_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 5_000L
    }
}
