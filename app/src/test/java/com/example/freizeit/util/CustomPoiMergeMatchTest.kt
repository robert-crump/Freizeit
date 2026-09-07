package com.example.freizeit.util

import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPoiMergeMatchTest {

    // Same GeoDistance-derived offset helper as CustomPoiProximityTest, so these distances don't
    // silently drift if the earth-radius constant ever changes.
    private fun offsetNorth(lat: Double, lon: Double, meters: Double): Double {
        val metersPerDegreeLat = GeoDistance.metersBetween(lat, lon, lat + 1.0, lon)
        return lat + meters / metersPerDegreeLat
    }

    private val customCafe = CustomPoi(
        id = "custom/1", category = "cafe", lat = 50.9, lon = 6.9, name = "Cafe Sonne"
    )

    @Test
    fun `a close same-category similarly-named poi is a merge candidate`() {
        val imported = Poi(
            id = "node/1", category = "cafe",
            lat = offsetNorth(customCafe.lat, customCafe.lon, 15.0), lon = customCafe.lon,
            name = "Cafe Sonne"
        )

        val candidates = CustomPoiMergeMatch.findCandidates(listOf(customCafe), listOf(imported))

        assertEquals(listOf(MergeCandidate(customCafe, imported)), candidates)
    }

    @Test
    fun `a different category at the same spot is not a candidate`() {
        val imported = Poi(
            id = "node/1", category = "restaurant",
            lat = customCafe.lat, lon = customCafe.lon, name = "Cafe Sonne"
        )

        val candidates = CustomPoiMergeMatch.findCandidates(listOf(customCafe), listOf(imported))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `a same-category poi with an unrelated name at the same spot is not a candidate`() {
        val imported = Poi(
            id = "node/1", category = "cafe",
            lat = customCafe.lat, lon = customCafe.lon, name = "Backerei Klein"
        )

        val candidates = CustomPoiMergeMatch.findCandidates(listOf(customCafe), listOf(imported))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `a same-category same-name poi beyond the distance threshold is not a candidate`() {
        val imported = Poi(
            id = "node/1", category = "cafe",
            lat = offsetNorth(customCafe.lat, customCafe.lon, CustomPoiMergeMatch.THRESHOLD_METERS + 20.0),
            lon = customCafe.lon,
            name = "Cafe Sonne"
        )

        val candidates = CustomPoiMergeMatch.findCandidates(listOf(customCafe), listOf(imported))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `an unnamed imported poi is never a candidate`() {
        val imported = Poi(
            id = "node/1", category = "cafe", lat = customCafe.lat, lon = customCafe.lon, name = null
        )

        val candidates = CustomPoiMergeMatch.findCandidates(listOf(customCafe), listOf(imported))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `the nearest qualifying match is picked when several imported pois qualify`() {
        val near = Poi(
            id = "node/near", category = "cafe",
            lat = offsetNorth(customCafe.lat, customCafe.lon, 5.0), lon = customCafe.lon,
            name = "Cafe Sonne"
        )
        val far = Poi(
            id = "node/far", category = "cafe",
            lat = offsetNorth(customCafe.lat, customCafe.lon, 30.0), lon = customCafe.lon,
            name = "Cafe Sonne"
        )

        val candidates = CustomPoiMergeMatch.findCandidates(listOf(customCafe), listOf(far, near))

        assertEquals(1, candidates.size)
        assertEquals("node/near", candidates[0].poi.id)
    }

    @Test
    fun `each custom poi surfaces at most one candidate`() {
        val other = customCafe.copy(id = "custom/2")
        val imported = Poi(
            id = "node/1", category = "cafe", lat = customCafe.lat, lon = customCafe.lon, name = "Cafe Sonne"
        )

        val candidates = CustomPoiMergeMatch.findCandidates(listOf(customCafe, other), listOf(imported))

        assertEquals(2, candidates.size)
        assertEquals(setOf("custom/1", "custom/2"), candidates.map { it.customPoi.id }.toSet())
    }

    @Test
    fun `no candidates when either list is empty`() {
        val imported = Poi(
            id = "node/1", category = "cafe", lat = customCafe.lat, lon = customCafe.lon, name = "Cafe Sonne"
        )

        assertTrue(CustomPoiMergeMatch.findCandidates(emptyList(), listOf(imported)).isEmpty())
        assertTrue(CustomPoiMergeMatch.findCandidates(listOf(customCafe), emptyList()).isEmpty())
    }
}
