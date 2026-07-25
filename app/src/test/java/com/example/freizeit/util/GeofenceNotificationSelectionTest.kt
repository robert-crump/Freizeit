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
}
