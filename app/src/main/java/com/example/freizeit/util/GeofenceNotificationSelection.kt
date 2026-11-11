package com.example.freizeit.util

import java.time.Instant
import java.time.ZoneId

/**
 * Radius of each auto check-in geofence; the fresh-fix check behind it uses the tighter
 * [CHECKIN_CONFIRM_RADIUS_METERS] (#82). Deliberately
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
 * How close a fresh fix must put the device to a favorite before its DWELL may prompt (#82).
 * Tighter than [GEOFENCE_RADIUS_METERS] on purpose: the fence stays wide so Play Services' coarse
 * detection doesn't miss real visits, and this check weeds out spots that merely sit inside the
 * circle — e.g. a place passed every morning ~150 m from a favorite, with an 18 m accurate fix.
 */
const val CHECKIN_CONFIRM_RADIUS_METERS = 100.0

/**
 * How long the device has to stay continuously inside a geofence before Play Services reports
 * DWELL — the "staying" side of passing-by-vs-staying. The same in debug builds: the debug build
 * is used day to day, and a short debug delay prompted on every walk-by. Its start is the arrival
 * a prompt shows and its Check in logs (#82).
 */
const val GEOFENCE_LOITERING_DELAY_MILLIS = 15 * 60 * 1000L

/**
 * True when a fresh fix ([currentLat]/[currentLon], taken on DWELL receipt) is within
 * [radiusMeters] of the favorite — the gate every prompt has to pass (#82). It also catches DWELL
 * broadcasts Play Services delivered late under Doze batching, once the device has moved on
 * (#42). No accuracy padding: a poor fix makes it stricter, never more lenient. A ±100 m fix 26 m
 * from the place still passes, while padding would have let a ±56 m fix 161 m away through.
 */
fun isWithinConfirmRadius(
    currentLat: Double,
    currentLon: Double,
    poiLat: Double,
    poiLon: Double,
    radiusMeters: Double = CHECKIN_CONFIRM_RADIUS_METERS
): Boolean = GeoDistance.metersBetween(currentLat, currentLon, poiLat, poiLon) <= radiusMeters

/**
 * When the device entered the geofence (#82): the start of the loitering clock that ended with a
 * DWELL decided at [dwellFixTimeMillis] (the triggering location's time, not the possibly late
 * broadcast's). Falls back to [nowMillis] when the fix carries no time.
 */
fun arrivalTime(dwellFixTimeMillis: Long, nowMillis: Long): Long =
    (dwellFixTimeMillis.takeIf { it > 0L } ?: nowMillis) - GEOFENCE_LOITERING_DELAY_MILLIS

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
