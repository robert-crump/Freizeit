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
    fun `reconcile drops a removed id from dwelling and leaves others untouched`() {
        val result = reconcileDwellingOnUnregister(
            dwellingPlaceIds = setOf("a", "b"),
            idsToRemove = setOf("a"),
            activeNotificationPlaceId = null
        )
        assertEquals(setOf("b"), result.dwellingPlaceIds)
        assertEquals(false, result.cancelActiveNotification)
    }

    @Test
    fun `reconcile cancels the active notification when its own place is removed`() {
        val result = reconcileDwellingOnUnregister(
            dwellingPlaceIds = setOf("a", "b"),
            idsToRemove = setOf("a"),
            activeNotificationPlaceId = "a"
        )
        assertEquals(setOf("b"), result.dwellingPlaceIds)
        assertEquals(true, result.cancelActiveNotification)
    }

    @Test
    fun `reconcile leaves the active notification alone when a different place is removed`() {
        val result = reconcileDwellingOnUnregister(
            dwellingPlaceIds = setOf("a", "b"),
            idsToRemove = setOf("a"),
            activeNotificationPlaceId = "b"
        )
        assertEquals(false, result.cancelActiveNotification)
    }

    @Test
    fun `reconcile is a no-op when nothing is removed`() {
        val result = reconcileDwellingOnUnregister(
            dwellingPlaceIds = setOf("a", "b"),
            idsToRemove = emptySet(),
            activeNotificationPlaceId = "a"
        )
        assertEquals(setOf("a", "b"), result.dwellingPlaceIds)
        assertEquals(false, result.cancelActiveNotification)
    }
}
