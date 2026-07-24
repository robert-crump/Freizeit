package com.example.freizeit.ui.checkin

import com.example.freizeit.data.entity.Poi
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
    fun `favorite within 200m is top billed ahead of a closer non-favorite`() {
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
    fun `favorite beyond 200m loses top billing and sorts by distance with the rest`() {
        // ~389m, favorite but outside the 200m top-billing radius
        val farFavorite = poi("far-favorite", 0.0035)
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
    fun `places beyond 500m are excluded entirely`() {
        // ~667m
        val tooFar = poi("too-far", 0.006)

        val result = rankNearbyForCheckIn(listOf(tooFar), emptyMap(), home)

        assertEquals(emptyList<CheckInCandidate>(), result)
    }

    @Test
    fun `no candidates within range yields an empty list`() {
        assertEquals(emptyList<CheckInCandidate>(), rankNearbyForCheckIn(emptyList(), emptyMap(), home))
    }
}
