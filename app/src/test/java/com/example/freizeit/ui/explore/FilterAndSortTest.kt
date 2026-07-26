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
        poi("node/4", "playground", 50.91, 6.91, "Charlie"),
        poi("node/5", "shop", 50.92, 6.92, "Delta Bakery")
    )

    @Test
    fun `showAll filters to the active categories`() {
        val result = filterAndSort(pois, setOf("cafe"), null, showAll = true)
        assertEquals(listOf("node/1", "node/3"), result.map { it.poi.id })
    }

    @Test
    fun `showAll with multiple active categories matches any of them`() {
        val result = filterAndSort(pois, setOf("cafe", "park"), null, showAll = true)
        assertEquals(setOf("node/1", "node/2", "node/3"), result.map { it.poi.id }.toSet())
    }

    @Test
    fun `showAll with zero active categories shows nothing`() {
        assertEquals(0, filterAndSort(pois, emptySet(), null, showAll = true).size)
    }

    @Test
    fun `showAll false shows nothing regardless of active categories`() {
        assertEquals(0, filterAndSort(pois, setOf("cafe"), null, showAll = false).size)
    }

    @Test
    fun `no filter active shows nothing`() {
        assertEquals(0, filterAndSort(pois, emptySet(), null).size)
    }

    @Test
    fun `with location sorts nearest first and fills distances`() {
        val home = LatLon(50.90, 6.90)
        val result = filterAndSort(pois, setOf("cafe"), home, showAll = true)

        assertEquals(listOf("node/1", "node/3"), result.map { it.poi.id })
        assertEquals(0.0, result[0].distanceMeters!!, 0.001)
        assertEquals(true, result[1].distanceMeters!! > result[0].distanceMeters!!)
    }

    @Test
    fun `without location sorts by name with unnamed last`() {
        val result = filterAndSort(pois, setOf("cafe"), null, showAll = true)

        assertEquals(listOf("node/1", "node/3"), result.map { it.poi.id })
        assertNull(result[1].distanceMeters)
    }

    @Test
    fun `favorites filter keeps only the ids in the favorite set regardless of category`() {
        val result = filterAndSort(pois, emptySet(), null, verdictIds = setOf("node/2"))
        assertEquals(listOf("node/2"), result.map { it.poi.id })
    }

    @Test
    fun `null favorites filter falls back to showAll's active categories`() {
        val result = filterAndSort(pois, setOf("cafe"), null, verdictIds = null, showAll = true)
        assertEquals(2, result.size)
    }

    @Test
    fun `search matches a substring of the name case-insensitively across categories`() {
        val result = filterAndSort(pois, emptySet(), null, searchQuery = "HAR")
        assertEquals(listOf("node/4"), result.map { it.poi.id }) // "Charlie"
    }

    @Test
    fun `search takes priority over category and favorites filters`() {
        val result = filterAndSort(
            pois, activeCategories = setOf("playground"), location = null,
            verdictIds = setOf("node/1"), searchQuery = "alpha"
        )
        assertEquals(listOf("node/2"), result.map { it.poi.id })
    }

    @Test
    fun `search matches a custom name even when the OSM name differs`() {
        val result = filterAndSort(
            pois, emptySet(), null, searchQuery = "hidden gem",
            customNames = mapOf("node/3" to "Our Hidden Gem")
        )
        assertEquals(listOf("node/3"), result.map { it.poi.id })
    }

    @Test
    fun `search matches a new coarse category the same as the original five`() {
        val result = filterAndSort(pois, emptySet(), null, searchQuery = "bakery")
        assertEquals(listOf("node/5"), result.map { it.poi.id })
    }

    @Test
    fun `showAll with every category active passes every poi through`() {
        val result = filterAndSort(
            pois, activeCategories = pois.map { it.category }.toSet(), location = null, showAll = true
        )
        assertEquals(pois.map { it.id }.toSet(), result.map { it.poi.id }.toSet())
        assertEquals(pois.size, result.size)
    }

    @Test
    fun `favorites filter takes priority over showAll`() {
        val result = filterAndSort(
            pois, activeCategories = emptySet(), location = null,
            verdictIds = setOf("node/2"), showAll = true
        )
        assertEquals(listOf("node/2"), result.map { it.poi.id })
    }

    @Test
    fun `search takes priority over showAll`() {
        val result = filterAndSort(
            pois, activeCategories = emptySet(), location = null,
            searchQuery = "alpha", showAll = true
        )
        assertEquals(listOf("node/2"), result.map { it.poi.id })
    }
}
