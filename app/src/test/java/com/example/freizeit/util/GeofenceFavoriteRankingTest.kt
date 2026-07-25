package com.example.freizeit.util

import com.example.freizeit.data.entity.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceFavoriteRankingTest {

    /** A favorite `distanceIndex` steps north of the origin — larger index, farther away. */
    private fun favoriteAt(distanceIndex: Int) =
        Poi(id = "poi-$distanceIndex", category = "cafe", lat = 50.9 + distanceIndex * 0.001, lon = 6.9)

    @Test
    fun `at or under the limit, every favorite is returned unchanged`() {
        val favorites = (0 until 50).map { favoriteAt(it) }

        val result = selectClosestFavorites(favorites, lat = 50.9, lon = 6.9, limit = 100)

        assertEquals(favorites, result)
    }

    @Test
    fun `exactly at the limit, every favorite is returned unchanged`() {
        val favorites = (0 until 100).map { favoriteAt(it) }

        val result = selectClosestFavorites(favorites, lat = 50.9, lon = 6.9, limit = 100)

        assertEquals(favorites, result)
    }

    @Test
    fun `over the limit, only the closest are kept`() {
        // 150 favorites, indices 0..149 strictly increasing in distance from the origin.
        val favorites = (0 until 150).map { favoriteAt(it) }.shuffled(kotlin.random.Random(42))

        val result = selectClosestFavorites(favorites, lat = 50.9, lon = 6.9, limit = 100)

        assertEquals(100, result.size)
        val expectedIds = (0 until 100).map { "poi-$it" }.toSet()
        assertEquals(expectedIds, result.map { it.id }.toSet())
    }

    @Test
    fun `over the limit, results are sorted closest first`() {
        val favorites = (0 until 120).map { favoriteAt(it) }.shuffled(kotlin.random.Random(7))

        val result = selectClosestFavorites(favorites, lat = 50.9, lon = 6.9, limit = 100)

        val distances = result.map { GeoDistance.metersBetween(50.9, 6.9, it.lat, it.lon) }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `empty favorites returns empty`() {
        val result = selectClosestFavorites(emptyList(), lat = 50.9, lon = 6.9, limit = 100)

        assertTrue(result.isEmpty())
    }
}
