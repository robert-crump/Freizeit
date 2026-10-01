package com.example.freizeit.ui.checkin

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.PoiOverride
import com.example.freizeit.data.entity.applyOverrides
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.util.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInRankingTest {

    private val home = LatLon(50.9, 6.9)

    private fun poi(id: String, latOffsetDeg: Double) =
        Poi(id = id, category = "cafe", lat = home.lat + latOffsetDeg, lon = home.lon, name = id)

    private fun favorite(placeId: String) = Verdict(
        placeId = placeId,
        value = Verdict.VALUE_FAVORITE,
        verdictedAt = 0L,
        snapshotName = null,
        snapshotLat = 0.0,
        snapshotLon = 0.0,
        snapshotCategory = "cafe"
    )

    @Test
    fun `favorite within 500m is top billed ahead of a closer non-favorite`() {
        // ~56m, non-favorite
        val nearNonFavorite = poi("near-non-favorite", 0.0005)
        // ~111m, favorite
        val nearFavorite = poi("near-favorite", 0.001)

        val result = rankNearbyForCheckIn(
            pois = listOf(nearNonFavorite, nearFavorite),
            verdicts = mapOf("near-favorite" to favorite("near-favorite")),
            location = home
        )

        assertEquals(listOf("near-favorite", "near-non-favorite"), result.map { it.poi.id })
    }

    @Test
    fun `favorite beyond 500m loses top billing and sorts by distance with the rest`() {
        // ~667m, favorite but outside the 500m top-billing radius
        val farFavorite = poi("far-favorite", 0.006)
        // ~56m, non-favorite
        val nearNonFavorite = poi("near-non-favorite", 0.0005)

        val result = rankNearbyForCheckIn(
            pois = listOf(farFavorite, nearNonFavorite),
            verdicts = mapOf("far-favorite" to favorite("far-favorite")),
            location = home
        )

        assertEquals(listOf("near-non-favorite", "far-favorite"), result.map { it.poi.id })
    }

    @Test
    fun `places far beyond 500m are still included, sorted after closer ones`() {
        // ~1.1km
        val farAway = poi("far-away", 0.01)
        // ~56m
        val nearNonFavorite = poi("near-non-favorite", 0.0005)

        val result = rankNearbyForCheckIn(listOf(farAway, nearNonFavorite), emptyMap(), home)

        assertEquals(listOf("near-non-favorite", "far-away"), result.map { it.poi.id })
    }

    @Test
    fun `no candidates within range yields an empty list`() {
        assertEquals(emptyList<CheckInCandidate>(), rankNearbyForCheckIn(emptyList(), emptyMap(), home))
    }

    @Test
    fun `favorites nearby lists only favorites within 500m, nearest first`() {
        val at400m = poi("fav-400m", 0.0036)
        val at111m = poi("fav-111m", 0.001)
        val at667m = poi("fav-667m", 0.006)
        val nonFavorite = poi("non-favorite", 0.0005)
        val verdicts = listOf("fav-400m", "fav-111m", "fav-667m").associateWith { favorite(it) }

        val ranked = rankNearbyForCheckIn(listOf(at400m, at111m, at667m, nonFavorite), verdicts, home)

        assertEquals(listOf("fav-111m", "fav-400m"), favoritesNearby(ranked).map { it.poi.id })
    }

    @Test
    fun `search finds a renamed place by its new name, not its OSM name`() {
        val osm = poi("renamed", 0.001).copy(name = "Cafe Mueller")
        val override = PoiOverride(placeId = "renamed", name = "Omas Kuchenstube")
        val places = applyOverrides(listOf(osm), mapOf("renamed" to override))
        val ranked = rankNearbyForCheckIn(places, emptyMap(), home)

        assertEquals(listOf("renamed"), matchCheckInSearch(ranked, "kuchen").map { it.poi.id })
        assertEquals(emptyList<CheckInCandidate>(), matchCheckInSearch(ranked, "Mueller"))
    }
}
