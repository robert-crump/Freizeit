package com.example.freizeit.util

import java.time.Instant
import java.time.ZoneId

/**
 * Radius of each auto check-in geofence, and of the staleness check that backs it up. Deliberately
 * its own constant, not the check-in tab's "Favorites nearby" radius: that one grew to 500 m in
 * #78, which silently grew the geofences too and put home inside a nearby favorite's circle, so
 * coming home prompted a check-in there.
 */
const val GEOFENCE_RADIUS_METERS = 200.0

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
 * the one favorite a single geofence event may prompt for (issue #28). Null when every
 * candidate is absent, on cooldown or already notified today.
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

/**
 * Favorites whose check-in notification was already posted today (issue #80) and so stay quiet
 * until the next calendar day — whether it's still up, was answered, swiped away or ignored.
 * No exemption for prompts still on screen (#81): those are never re-posted either, so the
 * posting time their Check in action carries never resets. [lastNotifiedAt] maps place id to
 * when its notification was last posted.
 */
fun notifiedTodayPlaceIds(
    lastNotifiedAt: Map<String, Long>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Set<String> =
    lastNotifiedAt.filterValues { isCoolingDown(it, nowMillis, zone) }.keys

/**
 * How long a prompt posted at [nowMillis] stays up at most (#81): until the next local midnight,
 * since "notified today" is the unit it belongs to.
 */
fun millisUntilMidnight(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
    return midnight.toInstant().toEpochMilli() - nowMillis
}

/**
 * Which of the on-screen check-in prompts ([promptedPlaceIds]) to cancel after a geofence sync
 * (#81): those whose place is no longer a notification target — un-favorited, removed from
 * want-to-go or deleted. While every switch is off ([enabled] false: paused/turned off), the
 * target list is empty without anything having changed about the places, so nothing goes.
 */
fun promptsToCancel(promptedPlaceIds: Set<String>, targetPlaceIds: Set<String>, enabled: Boolean): Set<String> =
    if (!enabled) emptySet() else promptedPlaceIds - targetPlaceIds

/**
 * [lastNotifiedAt] with [placeId] stamped at [nowMillis] and every entry from an earlier day
 * dropped — only today's entries can ever suppress anything, so the map never outgrows a day.
 */
fun recordNotified(
    lastNotifiedAt: Map<String, Long>,
    placeId: String,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Map<String, Long> =
    lastNotifiedAt.filterValues { isCoolingDown(it, nowMillis, zone) } + (placeId to nowMillis)
