package com.example.freizeit.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.checkIn
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Visit
import com.example.freizeit.data.repository.findPoiById
import com.example.freizeit.util.GEOFENCE_RADIUS_METERS
import com.example.freizeit.util.GeofenceCandidate
import com.example.freizeit.util.GeofenceEventLog
import com.example.freizeit.util.LocationHelper
import com.example.freizeit.util.closestEligibleFavorite
import com.example.freizeit.util.isCoolingDown
import com.example.freizeit.util.isNotificationStale
import com.example.freizeit.util.notifiedTodayPlaceIds
import com.example.freizeit.util.recordNotified
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles both Play Services' geofence ENTER/EXIT/DWELL broadcasts and the check-in
 * notifications' actions: Check in, Dismiss/swipe-away and the confirmation's Undo (#81). ENTER
 * is ignored outright: only DWELL (staying, not just passing by) is notification-worthy.
 *
 * Transitions only ever add prompts (#81): once posted, a prompt stays up until it's answered,
 * the place is checked in some other way, midnight, or the place stops being a target (see
 * [GeofenceSyncManager]) — leaving the place, location going off or a stale fix no longer
 * remove it, they only keep new prompts from being posted.
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
                    ACTION_DISMISS -> intent.getStringExtra(EXTRA_PLACE_ID)?.let { GeofenceNotifications.cancelPrompt(appContext, it) }
                    ACTION_UNDO_CHECK_IN -> handleUndoCheckIn(appContext, intent)
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
            GeofenceEventLog.append(app, "error code=${event.errorCode}")
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                // Location was switched off: Play Services has dropped every geofence (issue #58).
                // Forget what we think is registered so the next sync re-adds them all.
                val geofenceState = app.container.geofenceStateRepository
                geofenceState.setRegisteredFavoriteIds(emptySet())
                geofenceState.setRegistrationEpoch(null)
                geofenceState.setDwellingPlaceIds(emptySet())
            }
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
        GeofenceEventLog.append(
            app,
            "transition=${if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) "DWELL" else "EXIT"} " +
                "triggered=$triggeringPlaceIds dwelling=$dwellingIds"
        )

        val triggerLocation = event.triggeringLocation ?: run {
            GeofenceEventLog.append(app, "transition without triggering location, notification not refreshed")
            return
        }
        refreshNotification(app, dwellingIds, triggerLocation.latitude, triggerLocation.longitude)
    }

    /** Logs the visit at the prompt's posting time, not now (#81), and swaps the prompt for the
     *  Undo confirmation. A place deleted since just loses its prompt. */
    private suspend fun handleCheckIn(app: FreizeitApplication, intent: Intent) {
        val placeId = intent.getStringExtra(EXTRA_PLACE_ID) ?: return
        val poi = findPoiById(
            app.container.database.poiDao(),
            app.container.database.customPoiDao(),
            app.container.database.poiOverrideDao(),
            placeId
        )
        if (poi == null) {
            GeofenceNotifications.cancelPrompt(app, placeId)
            return
        }
        val visitedAt = intent.getLongExtra(EXTRA_POSTED_AT, 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
        val visitId = app.container.database.visitDao().checkIn(poi, source = Visit.SOURCE_NOTIFICATION, visitedAt = visitedAt)
        GeofenceEventLog.append(app, "check-in from notification poi=${poi.id} visitedAt=$visitedAt")
        GeofenceNotifications.showConfirmation(app, poi.id, poi.name, visitId, visitedAt)
    }

    /** Removes just the visit; the prompt doesn't come back (#81). */
    private suspend fun handleUndoCheckIn(app: FreizeitApplication, intent: Intent) {
        val placeId = intent.getStringExtra(EXTRA_PLACE_ID) ?: return
        val visitId = intent.getLongExtra(EXTRA_VISIT_ID, -1L)
        if (visitId >= 0L) app.container.database.visitDao().deleteByIds(listOf(visitId))
        GeofenceEventLog.append(app, "undo check-in from notification poi=$placeId visit=$visitId")
        GeofenceNotifications.cancelConfirmation(app, placeId)
    }

    private suspend fun refreshNotification(
        app: FreizeitApplication,
        dwellingPlaceIds: Set<String>,
        triggerLat: Double,
        triggerLon: Double
    ) {
        if (dwellingPlaceIds.isEmpty()) return
        val poiDao = app.container.database.poiDao()
        val customPoiDao = app.container.database.customPoiDao()
        val overrideDao = app.container.database.poiOverrideDao()
        val visitDao = app.container.database.visitDao()
        val now = System.currentTimeMillis()

        val candidates = dwellingPlaceIds.mapNotNull { placeId ->
            findPoiById(poiDao, customPoiDao, overrideDao, placeId)?.let { poi -> GeofenceCandidate(poi.id, poi.lat, poi.lon) }
        }
        val coolingDown = candidates
            .filter { isCoolingDown(visitDao.lastVisitedAt(it.placeId), now) }
            .map { it.placeId }
            .toSet()
        val geofenceState = app.container.geofenceStateRepository
        val notifiedToday = notifiedTodayPlaceIds(lastNotifiedAt = geofenceState.getLastNotifiedAt(), nowMillis = now)

        val closest = closestEligibleFavorite(candidates, coolingDown + notifiedToday, triggerLat, triggerLon)
        GeofenceEventLog.append(
            app,
            "closestEligibleFavorite candidates=${candidates.map { it.placeId }} coolingDown=$coolingDown " +
                "notifiedToday=$notifiedToday -> ${closest?.placeId ?: "none"}"
        )
        if (closest == null) return
        val poi = findPoiById(poiDao, customPoiDao, overrideDao, closest.placeId) ?: return

        // Play Services can deliver a DWELL broadcast well after the fact under Doze/standby
        // batching (issue #42) — by the time it arrives the device may already be elsewhere, so
        // a current location fix that disagrees with the report wins over trusting the event.
        // Prefer an actively-requested fresh fix over the passive last-known cache (issue #43):
        // right after an indoor visit the cache can be stale/inaccurate enough to wrongly reject
        // a valid check-in. Fall back to the cache if no fresh fix arrives in time.
        val currentLocation = LocationHelper.freshLocation(app) ?: LocationHelper.lastKnownLocation(app)
        val stale = currentLocation != null &&
            isNotificationStale(
                currentLat = currentLocation.lat,
                currentLon = currentLocation.lon,
                accuracyMeters = currentLocation.accuracyMeters,
                poiLat = poi.lat,
                poiLon = poi.lon,
                radiusMeters = GEOFENCE_RADIUS_METERS
            )
        GeofenceEventLog.append(app, "staleness poi=${poi.id} currentLocation=$currentLocation stale=$stale")
        if (stale) return

        showPrompt(app, poi)
    }

    private suspend fun showPrompt(app: FreizeitApplication, poi: Poi) {
        val postedAt = System.currentTimeMillis()
        if (!GeofenceNotifications.showPrompt(app, poi, postedAt)) return
        val geofenceState = app.container.geofenceStateRepository
        geofenceState.setLastNotifiedAt(recordNotified(geofenceState.getLastNotifiedAt(), poi.id, postedAt))
    }

    companion object {
        const val ACTION_GEOFENCE_TRANSITION = "com.example.freizeit.action.GEOFENCE_TRANSITION"
        const val ACTION_CHECK_IN = "com.example.freizeit.action.GEOFENCE_CHECK_IN"
        const val ACTION_DISMISS = "com.example.freizeit.action.GEOFENCE_DISMISS"
        const val ACTION_UNDO_CHECK_IN = "com.example.freizeit.action.GEOFENCE_UNDO_CHECK_IN"
        const val EXTRA_PLACE_ID = "place_id"
        /** When the prompt was posted: the time its Check in logs (#81). */
        const val EXTRA_POSTED_AT = "posted_at"
        const val EXTRA_VISIT_ID = "visit_id"
        private const val TAG = "GeofenceReceiver"
    }
}
