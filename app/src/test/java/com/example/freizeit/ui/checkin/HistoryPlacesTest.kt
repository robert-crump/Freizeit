package com.example.freizeit.ui.checkin

import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.PoiOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HistoryPlacesTest {

    private val osm = Poi(id = "node/1", category = "park", lat = 50.9, lon = 6.9, name = "Stadtpark")
    private val custom = CustomPoi(id = "custom/1", category = "cafe", lat = 50.9, lon = 6.9, name = "Our Café")

    @Test
    fun `a renamed place shows its current name`() {
        val places = historyPlaces(
            setOf("node/1"), listOf(osm), emptyList(), listOf(PoiOverride("node/1", name = "Our Park"))
        )
        assertEquals("Our Park", places["node/1"]?.name)
    }

    @Test
    fun `custom places resolve too, with their own name`() {
        val places = historyPlaces(setOf("custom/1"), emptyList(), listOf(custom), emptyList())
        assertEquals("Our Café", places["custom/1"]?.name)
    }

    @Test
    fun `a place that no longer exists is left out, so the row falls back to its snapshot`() {
        val places = historyPlaces(setOf("node/gone"), listOf(osm), listOf(custom), emptyList())
        assertFalse("node/gone" in places)
    }

    @Test
    fun `a hidden place keeps its current name in the history`() {
        val places = historyPlaces(
            setOf("node/1"), listOf(osm), emptyList(), listOf(PoiOverride("node/1", name = "Our Park", hidden = true))
        )
        assertEquals("Our Park", places["node/1"]?.name)
    }

    @Test
    fun `only visited places are resolved`() {
        val places = historyPlaces(setOf("node/1"), listOf(osm, osm.copy(id = "node/2")), listOf(custom), emptyList())
        assertEquals(setOf("node/1"), places.keys)
    }
}
