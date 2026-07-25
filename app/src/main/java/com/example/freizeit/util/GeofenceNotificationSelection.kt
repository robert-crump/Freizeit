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
