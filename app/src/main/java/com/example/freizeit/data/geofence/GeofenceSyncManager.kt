package com.example.freizeit.data.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.example.freizeit.BuildConfig
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
 * Each geofence also carries a [LOITERING_DELAY_MILLIS] DWELL trigger: passing by a favorite
 * (ENTER/EXIT with no DWELL in between) never prompts a check-in, only staying continuously for
 * the full delay does. ENTER/EXIT are still registered so [GeofenceBroadcastReceiver] can track
 * dwelling state, but only DWELL drives the notification.
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
     * Reconciles registered geofences with the current favorites, or — past [MAX_GEOFENCES] —
     * with whichever closest-100 selection the last [rerank] computed. Doesn't rank anything
     * itself; see the class doc for why. See [register] for why this reconciles rather than
     * rebuilding from scratch.
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

    /**
     * Diffs [favorites] against the last-registered set ([GeofenceStateRepository]) and only
     * removes/adds what actually changed, rather than blindly tearing down and rebuilding every
     * geofence. That matters because a blind rebuild would reset any in-progress DWELL loitering
     * clock — including for favorites unrelated to whatever change triggered this call (a
     * process restart, or someone else being favorited/un-favorited).
     */
    private suspend fun register(favorites: List<Poi>) {
        val targetIds = favorites.map { it.id }.toSet()
        val registeredIds = geofenceState.getRegisteredFavoriteIds()
        if (targetIds == registeredIds) return

        val idsToRemove = (registeredIds - targetIds).toList()
        val toAdd = favorites.filter { it.id !in registeredIds }

        try {
            if (idsToRemove.isNotEmpty()) {
                geofencingClient.removeGeofences(idsToRemove).await()
            }
            if (toAdd.isNotEmpty()) {
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(toAdd.map(::buildGeofence))
                    .build()
                geofencingClient.addGeofences(request, geofencePendingIntent).await()
            }
            geofenceState.setRegisteredFavoriteIds(targetIds)
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the call below (e.g. via system
            // Settings mid-flight) — leave Play Services in whatever state it's already in
            // rather than crash; the next favorites/enabled change retries. Don't persist
            // targetIds here since we don't know which of the two calls above actually landed.
        }
    }

    private fun buildGeofence(poi: Poi): Geofence =
        Geofence.Builder()
            .setRequestId(poi.id)
            .setCircularRegion(poi.lat, poi.lon, CHECKIN_FAVORITE_RADIUS_METERS.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(loiteringDelayMillis)
            .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MILLIS)
            .build()

    private suspend fun removeAll() {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent).await()
        } catch (e: SecurityException) {
            // Nothing to clean up if we never had permission to register in the first place.
        }
        geofenceState.setDwellingPlaceIds(emptySet())
        geofenceState.setRegisteredFavoriteIds(emptySet())
        GeofenceNotifications.cancel(context)
    }

    private fun hasRequiredPermissions(): Boolean =
        AutoCheckInPermissions.hasForegroundLocation(context) &&
            AutoCheckInPermissions.hasBackgroundLocation(context)

    companion object {
        private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 1
        /** Play Services' hard per-app cap on simultaneously active geofences. */
        private const val MAX_GEOFENCES = 100

        /**
         * How long a device must stay continuously inside a geofence before DWELL fires — the
         * "staying" side of the passing-by-vs-staying distinction. Shorter in debug builds so a
         * test cycle isn't a real 15-minute wait; still exercises the real DWELL path end to end.
         * [GeofenceNotifications] surfaces this shortened delay on the notification itself so a
         * debug build doesn't read as "dwell time ignored" (issue #34).
         */
        private const val LOITERING_DELAY_MILLIS = 15 * 60 * 1000
        const val DEBUG_LOITERING_DELAY_MILLIS = 30 * 1000
        val loiteringDelayMillis: Int
            get() = if (BuildConfig.DEBUG) DEBUG_LOITERING_DELAY_MILLIS else LOITERING_DELAY_MILLIS

        /**
         * Delays Play Services' transition reporting by this much to smooth over GPS jitter at
         * the geofence boundary, which would otherwise spuriously EXIT-then-ENTER and reset the
         * loitering clock. Traded off against added latency on genuine transitions.
         */
        private const val NOTIFICATION_RESPONSIVENESS_MILLIS = 2 * 60 * 1000
    }
}
