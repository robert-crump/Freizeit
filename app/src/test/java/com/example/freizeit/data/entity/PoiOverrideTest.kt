package com.example.freizeit.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PoiOverrideTest {

    private val osm = Poi(
        id = "node/1", category = "park", lat = 50.9, lon = 6.9, name = "Stadtpark",
        street = "Talstraße", postcode = "52068", city = "Aachen", openingHours = null
    )

    private fun build(
        name: String = "Stadtpark",
        category: String = "park",
        street: String = "Talstraße",
        housenumber: String = "",
        postcode: String = "52068",
        city: String = "Aachen",
        openingHours: String = ""
    ) = buildPoiOverride(osm, name, category, street, housenumber, postcode, city, openingHours)

    @Test
    fun `no override leaves the place unchanged`() {
        assertSame(osm, osm.withOverride(null))
    }

    @Test
    fun `override values replace OSM values, null ones keep OSM`() {
        val effective = osm.withOverride(PoiOverride("node/1", name = "Our Park", housenumber = "3"))
        assertEquals("Our Park", effective.name)
        assertEquals("3", effective.housenumber)
        assertEquals("Talstraße", effective.street)
        assertEquals("park", effective.category)
    }

    @Test
    fun `applyOverrides drops hidden places and applies the rest`() {
        val other = osm.copy(id = "node/2", name = "Other")
        val result = applyOverrides(
            listOf(osm, other),
            mapOf("node/1" to PoiOverride("node/1", hidden = true), "node/2" to PoiOverride("node/2", category = "cafe"))
        )
        assertEquals(listOf("node/2"), result.map { it.id })
        assertEquals("cafe", result[0].category)
    }

    @Test
    fun `unchanged form saves no override`() {
        assertNull(build())
    }

    @Test
    fun `fields equal to OSM are stored as null, changed ones are kept trimmed`() {
        assertEquals(
            PoiOverride("node/1", name = "Our Park", openingHours = "Mo-Su 08:00-20:00"),
            build(name = "  Our Park ", openingHours = "Mo-Su 08:00-20:00", street = " Talstraße ")
        )
    }

    @Test
    fun `a field cleared to blank reverts to OSM`() {
        assertNull(build(name = "   ", city = ""))
    }

    @Test
    fun `a category change is stored`() {
        assertEquals(PoiOverride("node/1", category = "playground"), build(category = "playground"))
    }
}
