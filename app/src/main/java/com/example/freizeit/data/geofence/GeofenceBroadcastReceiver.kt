package com.example.freizeit.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.checkIn
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Visit
import com.example.freizeit.ui.checkin.CHECKIN_FAVORITE_RADIUS_METERS
import com.example.freizeit.util.GeofenceCandidate
import com.example.freizeit.util.LocationHelper
import com.example.freizeit.util.closestEligibleFavorite
import com.example.freizeit.util.isCoolingDown
import com.example.freizeit.util.isNotificationStale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles both Play Services' geofence ENTER/EXIT/DWELL broadcasts and the notification's own
 * action buttons — one receiver so both paths share the same "recompute the one active
 * notification" logic in [refreshNotification]. ENTER is ignored outright: only DWELL (staying,
 * not just passing by) is notification-worthy.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext as FreizeitApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_GEOFENCE_TRANSITION -> handleGeofenceTransition(appContext, intent)
                    ACTION_CHECK_IN -> handleCheckIn(appContext, intent)
                    ACTION_DISMISS -> cancelNotification(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleGeofenceTransition(app: FreizeitApplication, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofencing error code ${event.errorCode}")
            return
        }
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Merely passing by isn't notification-worthy — only a confirmed DWELL is. Ignore
            // entirely so a drive-by never touches dwelling state or shows a prompt.
            return
        }
        val triggeringPlaceIds = event.triggeringGeofences.orEmpty().map { it.requestId }.toSet()
        val geofenceState = app.container.geofenceStateRepository
        val dwellingIds = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_DWELL -> geofenceState.getDwellingPlaceIds() + triggeringPlaceIds
            Geofence.GEOFENCE_TRANSITION_EXIT -> geofenceState.getDwellingPlaceIds() - triggeringPlaceIds
            else -> return
        }
        geofenceState.setDwellingPlaceIds(dwellingIds)

        val triggerLocation = event.triggeringLocation ?: return
        refreshNotification(app, dwellingIds, triggerLocation.latitude, triggerLocation.longitude)
    }

    private suspend fun handleCheckIn(app: FreizeitApplication, intent: Intent) {
        val placeId = intent.getStringExtra(EXTRA_PLACE_ID) ?: return
        val poi = app.container.database.poiDao().getById(placeId) ?: return
        app.container.database.visitDao().checkIn(poi, source = Visit.SOURCE_NOTIFICATION)
        cancelNotification(app)
    }

    private suspend fun refreshNotification(
        app: FreizeitApplication,
        dwellingPlaceIds: Set<String>,
        triggerLat: Double,
        triggerLon: Double
    ) {
        if (dwellingPlaceIds.isEmpty()) {
            cancelNotification(app)
            return
        }
        val poiDao = app.container.database.poiDao()
        val visitDao = app.container.database.visitDao()
        val now = System.currentTimeMillis()

        val candidates = dwellingPlaceIds.mapNotNull { placeId ->
            poiDao.getById(placeId)?.let { poi -> GeofenceCandidate(poi.id, poi.lat, poi.lon) }
        }
        val coolingDown = candidates
            .filter { isCoolingDown(visitDao.lastVisitedAt(it.placeId), now) }
            .map { it.placeId }
            .toSet()

        val closest = closestEligibleFavorite(candidates, coolingDown, triggerLat, triggerLon)
        if (closest == null) {
            cancelNotification(app)
            return
        }
        val poi = poiDao.getById(closest.placeId) ?: return

        // Play Services can deliver a DWELL broadcast well after the fact under Doze/standby
        // batching (issue #42) — by the time it arrives the device may already be elsewhere, so
        // a current location fix that disagrees with the report wins over trusting the event.
        val currentLocation = LocationHelper.lastKnownLocation(app)
        if (currentLocation != null &&
            isNotificationStale(
                currentLat = currentLocation.lat,
                currentLon = currentLocation.lon,
                accuracyMeters = currentLocation.accuracyMeters,
                poiLat = poi.lat,
                poiLon = poi.lon,
                radiusMeters = CHECKIN_FAVORITE_RADIUS_METERS
            )
        ) {
            cancelNotification(app)
            return
        }

        showNotification(app, poi)
    }

    private suspend fun showNotification(app: FreizeitApplication, poi: Poi) {
        GeofenceNotifications.show(app, poi)
        app.container.geofenceStateRepository.setActiveNotificationPlaceId(poi.id)
    }

    private suspend fun cancelNotification(app: FreizeitApplication) {
        GeofenceNotifications.cancel(app)
        app.container.geofenceStateRepository.setActiveNotificationPlaceId(null)
    }

    companion object {
        const val ACTION_GEOFENCE_TRANSITION = "com.example.freizeit.action.GEOFENCE_TRANSITION"
        const val ACTION_CHECK_IN = "com.example.freizeit.action.GEOFENCE_CHECK_IN"
        const val ACTION_DISMISS = "com.example.freizeit.action.GEOFENCE_DISMISS"
        const val EXTRA_PLACE_ID = "place_id"
        private const val TAG = "GeofenceReceiver"
    }
}
