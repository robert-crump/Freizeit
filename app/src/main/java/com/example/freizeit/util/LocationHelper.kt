package com.example.freizeit.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

data class LatLon(val lat: Double, val lon: Double, val accuracyMeters: Float? = null)

object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Most recent last-known location across providers, or null. Good enough
     * for sorting places by distance; no continuous updates needed.
     */
    fun lastKnownLocation(context: Context): LatLon? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return try {
            manager.getProviders(true)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
                ?.let { LatLon(it.latitude, it.longitude, it.accuracy.takeIf { acc -> acc > 0f }) }
        } catch (e: SecurityException) {
            null
        }
    }
}
