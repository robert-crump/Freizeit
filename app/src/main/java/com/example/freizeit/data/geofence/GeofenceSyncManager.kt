package com.example.freizeit.data.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.repository.GeofenceStateRepository
import com.example.freizeit.ui.checkin.CHECKIN_FAVORITE_RADIUS_METERS
import com.example.freizeit.util.AutoCheckInPermissions
import com.example.freizeit.util.selectClosestFavorites
import kotlinx.coroutines.tasks.await

/**
 * Keeps Play Services' registered geofences in sync with (auto check-in enabled) x (current
 * favorites). One 200m circular geofence per favorite, same radius as the manual check-in list's
 * top-billing cutoff (issue #23) — see [CHECKIN_FAVORITE_RADIUS_METERS].
 *
 * Play Services caps active geofences at 100 per app (issue #29). Past that count, [sync]
 * (triggered by favorites/toggle changes and app-open) doesn't rank anything itself — it just
 * replays whichever closest-100 selection [rerank] last persisted to [GeofenceStateRepository].
 * [rerank] is the only thing that recomputes that selection, and it's only ever called from a
 * significant-location-change fix (see `GeofenceLocationMonitor`), never app-open or a periodic
 * job. That split is also what makes un-favoriting lazy: [sync] looks selected ids up by id
 * regardless of current favorite status, so a place dropped from favorites keeps its geofence
 * until the next [rerank] recomputes the set from scratch.
 */
class GeofenceSyncManager(
    private val context: Context,
    private val geofenceState: GeofenceStateRepository,
    private val poiDao: PoiDao
) {

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

    /**
     * Re-registers geofences from scratch for the current favorites, or — past [MAX_GEOFENCES] —
     * for whichever closest-100 selection the last [rerank] computed. Doesn't rank anything
     * itself; see the class doc for why.
     */
    suspend fun sync(autoCheckInEnabled: Boolean, favorites: List<Poi>) {
        if (!autoCheckInEnabled || favorites.isEmpty() || !hasRequiredPermissions()) {
            removeAll()
            return
        }
        val targets = if (favorites.size <= MAX_GEOFENCES) favorites else replaySelection(favorites)
        register(targets)
    }

    /**
     * Re-ranks [favorites] by distance to [location] and registers the closest [MAX_GEOFENCES],
     * persisting the selection so later [sync] calls replay it without a location fix of their
     * own. Call this only from a significant-location-change fix (issue #29) — never on app-open,
     * never from a periodic job.
     */
    suspend fun rerank(autoCheckInEnabled: Boolean, favorites: List<Poi>, location: Location) {
        if (!autoCheckInEnabled || favorites.isEmpty() || !hasRequiredPermissions()) {
            removeAll()
            return
        }
        val selected = selectClosestFavorites(favorites, location.latitude, location.longitude, MAX_GEOFENCES)
        geofenceState.setSelectedFavoriteIds(selected.map { it.id }.toSet())
        register(selected)
    }

    /**
     * Looks the last-persisted selection up by id directly (not by filtering [currentFavorites]),
     * so an un-favorited place keeps resolving here — and keeps its geofence — until the next
     * [rerank] drops its id from the persisted set. Falls back to the first [MAX_GEOFENCES]
     * favorites, arbitrary order, if no re-rank has ever run yet (e.g. first time crossing 100
     * favorites before any significant-location-change fix has arrived).
     */
    private suspend fun replaySelection(currentFavorites: List<Poi>): List<Poi> {
        val selectedIds = geofenceState.getSelectedFavoriteIds()
        if (selectedIds.isEmpty()) return currentFavorites.take(MAX_GEOFENCES)
        return selectedIds.mapNotNull { poiDao.getById(it) }
    }

    private suspend fun register(favorites: List<Poi>) {
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
        /** Play Services' hard per-app cap on simultaneously active geofences. */
        const val MAX_GEOFENCES = 100
    }
}
