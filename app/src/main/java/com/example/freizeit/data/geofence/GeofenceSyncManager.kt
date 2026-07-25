package com.example.freizeit.data.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.repository.GeofenceStateRepository
import com.example.freizeit.ui.checkin.CHECKIN_FAVORITE_RADIUS_METERS
import com.example.freizeit.util.AutoCheckInPermissions
import kotlinx.coroutines.tasks.await

/**
 * Keeps Play Services' registered geofences in sync with (auto check-in enabled) x (current
 * favorites). One 200m circular geofence per favorite, same radius as the manual check-in list's
 * top-billing cutoff (issue #23) — see [CHECKIN_FAVORITE_RADIUS_METERS].
 */
class GeofenceSyncManager(private val context: Context, private val geofenceState: GeofenceStateRepository) {

    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(GeofenceBroadcastReceiver.ACTION_GEOFENCE_TRANSITION)
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            intent,
            // Play Services requires this one to be mutable (it writes extras into the intent
            // before redelivering it) — FLAG_IMMUTABLE here throws ApiException: 10 at registration.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /** Re-registers every geofence from scratch; cheapest correct way to reflect an edited favorite list. */
    suspend fun sync(autoCheckInEnabled: Boolean, favorites: List<Poi>) {
        if (!autoCheckInEnabled || favorites.isEmpty() || !hasRequiredPermissions()) {
            removeAll()
            return
        }
        val geofences = favorites.map { poi ->
            Geofence.Builder()
                .setRequestId(poi.id)
                .setCircularRegion(poi.lat, poi.lon, CHECKIN_FAVORITE_RADIUS_METERS.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        try {
            geofencingClient.removeGeofences(geofencePendingIntent).await()
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            geofencingClient.addGeofences(request, geofencePendingIntent).await()
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the call below (e.g. via system
            // Settings mid-flight) — leave Play Services in whatever state it's already in
            // rather than crash; the next favorites/enabled change retries.
        }
    }

    private suspend fun removeAll() {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent).await()
        } catch (e: SecurityException) {
            // Nothing to clean up if we never had permission to register in the first place.
        }
        geofenceState.setInsidePlaceIds(emptySet())
        GeofenceNotifications.cancel(context)
    }

    private fun hasRequiredPermissions(): Boolean =
        AutoCheckInPermissions.hasForegroundLocation(context) &&
            AutoCheckInPermissions.hasBackgroundLocation(context)

    private companion object {
        const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 1
    }
}
