package com.example.freizeit.util

import java.time.Instant
import java.time.ZoneId

/** A favorite currently inside its geofence, snapshotted just enough to rank by distance. */
data class GeofenceCandidate(val placeId: String, val lat: Double, val lon: Double)

/**
 * True once [lastVisitedAtMillis] (any [com.example.freizeit.data.entity.Visit] source) falls on
 * the same calendar day as [nowMillis] — the shared, per-favorite cooldown from issue #28.
 */
fun isCoolingDown(lastVisitedAtMillis: Long?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (lastVisitedAtMillis == null) return false
    val lastDate = Instant.ofEpochMilli(lastVisitedAtMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return lastDate == today
}

/**
 * Of the favorites currently inside their geofence, the nearest one that isn't cooling down —
 * the sole candidate whose notification may be shown/active at any moment (issue #28). Null when
 * every candidate is either absent or on cooldown, meaning any existing notification should be
 * cancelled instead.
 */
fun closestEligibleFavorite(
    candidates: List<GeofenceCandidate>,
    coolingDownPlaceIds: Set<String>,
    triggerLat: Double,
    triggerLon: Double
): GeofenceCandidate? =
    candidates
        .filterNot { it.placeId in coolingDownPlaceIds }
        .minByOrNull { GeoDistance.metersBetween(triggerLat, triggerLon, it.lat, it.lon) }

/**
 * True when [currentLat]/[currentLon] (a last-known location fix, taken at DWELL-broadcast
 * receipt time) is far enough from the favorite that the DWELL report — Play Services can delay
 * delivery well past its requested responsiveness under Doze/standby batching (issue #42) — no
 * longer reflects where the device actually is. [accuracyMeters] pads the radius so ordinary GPS
 * jitter right at the boundary doesn't false-positive; a null/non-positive accuracy adds no pad.
 */
fun isNotificationStale(
    currentLat: Double,
    currentLon: Double,
    accuracyMeters: Float?,
    poiLat: Double,
    poiLon: Double,
    radiusMeters: Double
): Boolean {
    val pad = accuracyMeters?.takeIf { it > 0f } ?: 0f
    val distance = GeoDistance.metersBetween(currentLat, currentLon, poiLat, poiLon)
    return distance > radiusMeters + pad
}

/** Outcome of reconciling dwelling state against favorites that just lost their geofence. */
data class DwellReconciliation(val dwellingPlaceIds: Set<String>, val cancelActiveNotification: Boolean)

/**
 * Drops [idsToRemove] (favorites whose geofence just got unregistered — e.g. un-favorited) from
 * [dwellingPlaceIds]. Without this, such a place has no geofence left to ever send an EXIT, so it
 * would stay marked "dwelling" forever and keep winning [closestEligibleFavorite] on later,
 * unrelated transitions (issue #42). [activeNotificationPlaceId] is the place id, if any, the
 * currently-shown notification is for — [cancelActiveNotification] is true only when that exact
 * id was one of the ones removed, since there's no location fix available in this path to safely
 * re-rank the remaining dwelling favorites and pick a replacement.
 */
fun reconcileDwellingOnUnregister(
    dwellingPlaceIds: Set<String>,
    idsToRemove: Set<String>,
    activeNotificationPlaceId: String?
): DwellReconciliation = DwellReconciliation(
    dwellingPlaceIds = dwellingPlaceIds - idsToRemove,
    cancelActiveNotification = activeNotificationPlaceId != null && activeNotificationPlaceId in idsToRemove
)
