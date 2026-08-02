package com.example.freizeit.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Actively requests a fresh fix, unlike [lastKnownLocation]'s passive cache read — a stale
     * cache is likely right after an indoor visit, where it can wrongly fail auto check-in's
     * staleness check (issue #43). Returns null if no fix arrives within [timeoutMillis].
     */
    suspend fun freshLocation(context: Context, timeoutMillis: Long = FRESH_LOCATION_TIMEOUT_MILLIS): LatLon? {
        if (!hasPermission(context)) return null
        val cancellationSource = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMillis) {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token)
                    .await()
                    ?.let { LatLon(it.latitude, it.longitude, it.accuracy.takeIf { acc -> acc > 0f }) }
            }
        } catch (e: SecurityException) {
            null
        } finally {
            cancellationSource.cancel()
        }
    }

    private const val FRESH_LOCATION_TIMEOUT_MILLIS = 8_000L
}
