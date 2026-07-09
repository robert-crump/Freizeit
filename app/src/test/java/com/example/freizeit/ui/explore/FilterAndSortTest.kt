package com.example.freizeit.ui.explore

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.util.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterAndSortTest {

    private fun poi(id: String, category: String, lat: Double, lon: Double, name: String? = null) =
        Poi(id = id, category = category, lat = lat, lon = lon, name = name)

    private val pois = listOf(
        poi("node/1", "cafe", 50.90, 6.90, "Bravo"),
        poi("node/2", "park", 50.95, 6.95, "Alpha"),
        poi("node/3", "cafe", 51.50, 7.50, null),
        poi("node/4", "playground", 50.91, 6.91, "Charlie")
    )

    @Test
    fun `filters to the selected category`() {
        val result = filterAndSort(pois, "cafe", null)
        assertEquals(listOf("node/1", "node/3"), result.map { it.poi.id })
    }

    @Test
    fun `no category selected shows nothing`() {
        assertEquals(0, filterAndSort(pois, null, null).size)
    }

    @Test
    fun `with location sorts nearest first and fills distances`() {
        val home = LatLon(50.90, 6.90)
        val result = filterAndSort(pois, "cafe", home)

        assertEquals(listOf("node/1", "node/3"), result.map { it.poi.id })
        assertEquals(0.0, result[0].distanceMeters!!, 0.001)
        assertEquals(true, result[1].distanceMeters!! > result[0].distanceMeters!!)
    }

    @Test
    fun `without location sorts by name with unnamed last`() {
        val result = filterAndSort(pois, "cafe", null)

        assertEquals(listOf("node/1", "node/3"), result.map { it.poi.id })
        assertNull(result[1].distanceMeters)
    }

    @Test
    fun `favorites filter keeps only the ids in the favorite set regardless of category`() {
        val result = filterAndSort(pois, null, null, favoriteIds = setOf("node/2"))
        assertEquals(listOf("node/2"), result.map { it.poi.id })
    }

    @Test
    fun `null favorites filter falls back to the selected category`() {
        val result = filterAndSort(pois, "cafe", null, favoriteIds = null)
        assertEquals(2, result.size)
    }

    @Test
    fun `search matches a substring of the name case-insensitively across categories`() {
        val result = filterAndSort(pois, null, null, searchQuery = "HAR")
        assertEquals(listOf("node/4"), result.map { it.poi.id }) // "Charlie"
    }

    @Test
    fun `search takes priority over category and favorites filters`() {
        val result = filterAndSort(
            pois, selectedCategory = "playground", location = null,
            favoriteIds = setOf("node/1"), searchQuery = "alpha"
        )
        assertEquals(listOf("node/2"), result.map { it.poi.id })
    }

    @Test
    fun `search matches a custom name even when the OSM name differs`() {
        val result = filterAndSort(
            pois, null, null, searchQuery = "hidden gem",
            customNames = mapOf("node/3" to "Our Hidden Gem")
        )
        assertEquals(listOf("node/3"), result.map { it.poi.id })
    }
}
