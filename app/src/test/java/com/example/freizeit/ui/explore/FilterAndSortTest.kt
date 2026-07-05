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
    fun `filters to the selected categories`() {
        val result = filterAndSort(pois, setOf("cafe"), null)
        assertEquals(listOf("node/1", "node/3"), result.map { it.poi.id })
    }

    @Test
    fun `empty selection shows nothing`() {
        assertEquals(0, filterAndSort(pois, emptySet(), null).size)
    }

    @Test
    fun `with location sorts nearest first and fills distances`() {
        val home = LatLon(50.90, 6.90)
        val result = filterAndSort(pois, setOf("cafe", "park", "playground"), home)

        assertEquals(listOf("node/1", "node/4", "node/2", "node/3"), result.map { it.poi.id })
        assertEquals(0.0, result[0].distanceMeters!!, 0.001)
        assertEquals(true, result[3].distanceMeters!! > result[2].distanceMeters!!)
    }

    @Test
    fun `without location sorts by name with unnamed last`() {
        val result = filterAndSort(pois, setOf("cafe", "park", "playground"), null)

        assertEquals(listOf("node/2", "node/1", "node/4", "node/3"), result.map { it.poi.id })
        assertNull(result[0].distanceMeters)
    }
}
