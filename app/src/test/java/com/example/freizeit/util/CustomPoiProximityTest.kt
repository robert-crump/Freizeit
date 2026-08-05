package com.example.freizeit.util

import com.example.freizeit.data.entity.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomPoiProximityTest {

    // ~0.00009 degrees of latitude is roughly 10m — small offsets below are deliberately
    // constructed via GeoDistance itself rather than eyeballed degree deltas, so the test doesn't
    // silently drift if the earth-radius constant ever changes.
    private fun offsetNorth(lat: Double, lon: Double, meters: Double): Double {
        val metersPerDegreeLat = GeoDistance.metersBetween(lat, lon, lat + 1.0, lon)
        return lat + meters / metersPerDegreeLat
    }

    private val cafe = Poi(id = "node/cafe", category = "cafe", lat = 50.9, lon = 6.9, name = "Existing Café")
    private val park = Poi(id = "node/park", category = "park", lat = 50.9, lon = 6.9, name = "Existing Park")

    @Test
    fun `finds a same-category place within the threshold`() {
        val nearLat = offsetNorth(cafe.lat, cafe.lon, 15.0)

        val match = CustomPoiProximity.findNearbyMatch(nearLat, cafe.lon, "cafe", listOf(cafe, park))

        assertEquals(cafe, match)
    }

    @Test
    fun `ignores a place of a different category even if very close`() {
        val nearLat = offsetNorth(park.lat, park.lon, 5.0)

        val match = CustomPoiProximity.findNearbyMatch(nearLat, park.lon, "cafe", listOf(park))

        assertNull(match)
    }

    @Test
    fun `ignores a same-category place beyond the threshold`() {
        val farLat = offsetNorth(cafe.lat, cafe.lon, CustomPoiProximity.WARNING_THRESHOLD_METERS + 20.0)

        val match = CustomPoiProximity.findNearbyMatch(farLat, cafe.lon, "cafe", listOf(cafe))

        assertNull(match)
    }

    @Test
    fun `a place at exactly the threshold distance still counts`() {
        // thresholdMeters is set to the actual computed distance (not a synthetically offset
        // value re-measured a second time) so this boundary check can't be flaky on
        // Haversine/floating-point rounding.
        val nearLat = offsetNorth(cafe.lat, cafe.lon, 25.0)
        val exactDistance = GeoDistance.metersBetween(nearLat, cafe.lon, cafe.lat, cafe.lon)

        val match = CustomPoiProximity.findNearbyMatch(
            nearLat, cafe.lon, "cafe", listOf(cafe), thresholdMeters = exactDistance
        )

        assertEquals(cafe, match)
    }

    @Test
    fun `returns the nearest match when several same-category places are within range`() {
        val near = cafe.copy(id = "node/near", lat = offsetNorth(cafe.lat, cafe.lon, 5.0))
        val far = cafe.copy(id = "node/far", lat = offsetNorth(cafe.lat, cafe.lon, 30.0))

        val match = CustomPoiProximity.findNearbyMatch(cafe.lat, cafe.lon, "cafe", listOf(far, near))

        assertEquals("node/near", match?.id)
    }

    @Test
    fun `no match when the list is empty`() {
        assertNull(CustomPoiProximity.findNearbyMatch(50.9, 6.9, "cafe", emptyList()))
    }

    @Test
    fun `custom threshold overrides the default`() {
        val nearLat = offsetNorth(cafe.lat, cafe.lon, 5.0)

        assertNull(CustomPoiProximity.findNearbyMatch(nearLat, cafe.lon, "cafe", listOf(cafe), thresholdMeters = 2.0))
    }
}
