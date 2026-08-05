package com.example.freizeit.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPoiTest {

    @Test
    fun `newCustomPoiId is prefixed and recognized by isCustomPoiId`() {
        val id = newCustomPoiId()

        assertTrue(id.startsWith(CUSTOM_POI_ID_PREFIX))
        assertTrue(isCustomPoiId(id))
    }

    @Test
    fun `newCustomPoiId never repeats`() {
        assertNotEquals(newCustomPoiId(), newCustomPoiId())
    }

    @Test
    fun `an OSM-style id is not a custom POI id`() {
        assertFalse(isCustomPoiId("node/286560726"))
    }

    @Test
    fun `toPoi carries every field over and is never flagged missing from OSM`() {
        val customPoi = CustomPoi(
            id = "custom/1",
            category = "cafe",
            lat = 50.9,
            lon = 6.9,
            name = "Our Café",
            openingHours = "Mo-Fr 08:00-18:00",
            street = "Beispielstraße",
            housenumber = "1",
            postcode = "52062",
            city = "Aachen"
        )

        val poi = customPoi.toPoi()

        assertEquals("custom/1", poi.id)
        assertEquals("cafe", poi.category)
        assertEquals(50.9, poi.lat, 0.0)
        assertEquals(6.9, poi.lon, 0.0)
        assertEquals("Our Café", poi.name)
        assertEquals("Mo-Fr 08:00-18:00", poi.openingHours)
        assertEquals("Beispielstraße", poi.street)
        assertEquals("1", poi.housenumber)
        assertEquals("52062", poi.postcode)
        assertEquals("Aachen", poi.city)
        assertFalse(poi.missingFromOsm)
    }
}
