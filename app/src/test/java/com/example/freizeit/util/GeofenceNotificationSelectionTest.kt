package com.example.freizeit.util

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceNotificationSelectionTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun millisAt(iso: String): Long =
        ZonedDateTime.parse(iso).withZoneSameInstant(zone).toInstant().toEpochMilli()

    @Test
    fun `never visited is not cooling down`() {
        assertEquals(false, isCoolingDown(null, millisAt("2026-07-25T12:00:00+02:00"), zone))
    }

    @Test
    fun `visit earlier the same calendar day is cooling down`() {
        val lastVisit = millisAt("2026-07-25T08:00:00+02:00")
        val now = millisAt("2026-07-25T23:00:00+02:00")
        assertEquals(true, isCoolingDown(lastVisit, now, zone))
    }

    @Test
    fun `visit on the previous calendar day is not cooling down`() {
        val lastVisit = millisAt("2026-07-24T23:59:00+02:00")
        val now = millisAt("2026-07-25T00:01:00+02:00")
        assertEquals(false, isCoolingDown(lastVisit, now, zone))
    }

    @Test
    fun `closest eligible favorite picks the nearest non-cooling-down candidate`() {
        val near = GeofenceCandidate("near", lat = 50.9005, lon = 6.9)
        val far = GeofenceCandidate("far", lat = 50.95, lon = 6.9)

        val result = closestEligibleFavorite(
            candidates = listOf(far, near),
            coolingDownPlaceIds = emptySet(),
            triggerLat = 50.9,
            triggerLon = 6.9
        )

        assertEquals("near", result?.placeId)
    }

    @Test
    fun `a cooling-down closest candidate is skipped in favor of the next nearest`() {
        val near = GeofenceCandidate("near", lat = 50.9005, lon = 6.9)
        val far = GeofenceCandidate("far", lat = 50.95, lon = 6.9)

        val result = closestEligibleFavorite(
            candidates = listOf(near, far),
            coolingDownPlaceIds = setOf("near"),
            triggerLat = 50.9,
            triggerLon = 6.9
        )

        assertEquals("far", result?.placeId)
    }

    @Test
    fun `every candidate cooling down yields no notification target`() {
        val near = GeofenceCandidate("near", lat = 50.9005, lon = 6.9)

        val result = closestEligibleFavorite(
            candidates = listOf(near),
            coolingDownPlaceIds = setOf("near"),
            triggerLat = 50.9,
            triggerLon = 6.9
        )

        assertNull(result)
    }

    @Test
    fun `no candidates yields no notification target`() {
        assertNull(closestEligibleFavorite(emptyList(), emptySet(), 50.9, 6.9))
    }

    @Test
    fun `location well within the radius is not stale`() {
        val stale = isNotificationStale(
            currentLat = 50.9, currentLon = 6.9, accuracyMeters = 10f,
            poiLat = 50.9001, poiLon = 6.9, radiusMeters = 200.0
        )
        assertEquals(false, stale)
    }

    @Test
    fun `location well beyond the radius plus accuracy is stale`() {
        val stale = isNotificationStale(
            currentLat = 50.9, currentLon = 6.9, accuracyMeters = 10f,
            poiLat = 50.95, poiLon = 6.9, radiusMeters = 200.0
        )
        assertEquals(true, stale)
    }

    @Test
    fun `accuracy buffer forgives a fix just past the bare radius`() {
        // ~210m away: past the 200m radius alone, but within radius + 50m accuracy.
        val stale = isNotificationStale(
            currentLat = 50.9, currentLon = 6.9, accuracyMeters = 50f,
            poiLat = 50.9019, poiLon = 6.9, radiusMeters = 200.0
        )
        assertEquals(false, stale)
    }

    @Test
    fun `null or non-positive accuracy adds no buffer`() {
        val stale = isNotificationStale(
            currentLat = 50.9, currentLon = 6.9, accuracyMeters = null,
            poiLat = 50.9019, poiLon = 6.9, radiusMeters = 200.0
        )
        assertEquals(true, stale)
    }

    @Test
    fun `a fix 290 m from a favorite is outside its geofence radius`() {
        // ~290 m away with a precise fix. With the geofence riding on the 500 m "Favorites
        // nearby" radius this passed and prompted.
        val stale = isNotificationStale(
            currentLat = 50.9, currentLon = 6.9, accuracyMeters = 13f,
            poiLat = 50.9026, poiLon = 6.9, radiusMeters = GEOFENCE_RADIUS_METERS
        )
        assertEquals(true, stale)
    }

    @Test
    fun `a place notified earlier today is held back even though it was only dismissed`() {
        val result = notifiedTodayPlaceIds(
            lastNotifiedAt = mapOf("cafe" to millisAt("2026-07-25T16:10:00+02:00")),
            nowMillis = millisAt("2026-07-25T18:10:00+02:00"),
            zone = zone
        )
        assertEquals(setOf("cafe"), result)
    }

    @Test
    fun `a place notified yesterday may notify again`() {
        val result = notifiedTodayPlaceIds(
            lastNotifiedAt = mapOf("cafe" to millisAt("2026-07-24T23:59:00+02:00")),
            nowMillis = millisAt("2026-07-25T00:01:00+02:00"),
            zone = zone
        )
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `a prompt still on screen counts as notified today, so it is never re-posted`() {
        val today = millisAt("2026-07-25T16:10:00+02:00")
        val result = notifiedTodayPlaceIds(
            lastNotifiedAt = mapOf("cafe" to today, "park" to today),
            nowMillis = millisAt("2026-07-25T16:30:00+02:00"),
            zone = zone
        )
        assertEquals(setOf("cafe", "park"), result)
    }

    @Test
    fun `a nearby favorite takes over once the closest one already notified today`() {
        val cafe = GeofenceCandidate("cafe", lat = 50.9005, lon = 6.9)
        val park = GeofenceCandidate("park", lat = 50.902, lon = 6.9)
        val notifiedToday = notifiedTodayPlaceIds(
            lastNotifiedAt = mapOf("cafe" to millisAt("2026-07-25T16:10:00+02:00")),
            nowMillis = millisAt("2026-07-25T18:10:00+02:00"),
            zone = zone
        )

        val result = closestEligibleFavorite(listOf(cafe, park), notifiedToday, triggerLat = 50.9, triggerLon = 6.9)

        assertEquals("park", result?.placeId)
    }

    @Test
    fun `recording a notification stamps the place and drops earlier days`() {
        val now = millisAt("2026-07-25T18:10:00+02:00")
        val earlierToday = millisAt("2026-07-25T09:00:00+02:00")
        val result = recordNotified(
            lastNotifiedAt = mapOf("old" to millisAt("2026-07-24T12:00:00+02:00"), "park" to earlierToday),
            placeId = "cafe",
            nowMillis = now,
            zone = zone
        )
        assertEquals(mapOf("park" to earlierToday, "cafe" to now), result)
    }

    @Test
    fun `a prompt posted in the evening expires at the next local midnight`() {
        val result = millisUntilMidnight(millisAt("2026-07-25T22:30:00+02:00"), zone)
        assertEquals(90 * 60 * 1000L, result)
    }

    @Test
    fun `midnight expiry follows the local day across a DST switch`() {
        // 2026-10-25 is 25 hours long in Berlin (clocks go back at 03:00).
        val result = millisUntilMidnight(millisAt("2026-10-25T00:00:00+02:00"), zone)
        assertEquals(25 * 60 * 60 * 1000L, result)
    }

    @Test
    fun `prompts for places that are no longer targets are cancelled`() {
        val result = promptsToCancel(setOf("cafe", "park"), targetPlaceIds = setOf("park", "lake"), enabled = true)
        assertEquals(setOf("cafe"), result)
    }

    @Test
    fun `un-favoriting the last favorite cancels its prompt`() {
        val result = promptsToCancel(setOf("cafe"), targetPlaceIds = emptySet(), enabled = true)
        assertEquals(setOf("cafe"), result)
    }

    @Test
    fun `pausing check-ins keeps every prompt already on screen`() {
        val result = promptsToCancel(setOf("cafe", "park"), targetPlaceIds = emptySet(), enabled = false)
        assertEquals(emptySet<String>(), result)
    }
}
