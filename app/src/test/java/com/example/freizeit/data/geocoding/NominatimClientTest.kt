package com.example.freizeit.data.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NominatimClientTest {

    private val sample = """
        [
          {
            "place_id": 12345,
            "lat": "50.7753",
            "lon": "6.0839",
            "display_name": "Kleinkölnstraße, Aachen, North Rhine-Westphalia, Germany",
            "address": {
              "road": "Kleinkölnstraße",
              "house_number": "7",
              "postcode": "52062",
              "city": "Aachen"
            }
          },
          {
            "place_id": 6789,
            "lat": "50.8500",
            "lon": "5.9000",
            "display_name": "Markt, Vaals, Limburg, Netherlands",
            "address": {
              "road": "Markt",
              "town": "Vaals"
            }
          }
        ]
    """.trimIndent()

    @Test
    fun `parses a result array into GeocodeResults`() {
        val results = NominatimClient.parse(sample)
        assertNotNull(results)
        results!!
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals(50.7753, first.lat, 0.0001)
        assertEquals(6.0839, first.lon, 0.0001)
        assertEquals("Kleinkölnstraße, Aachen, North Rhine-Westphalia, Germany", first.displayName)
        assertEquals("Kleinkölnstraße", first.street)
        assertEquals("7", first.housenumber)
        assertEquals("52062", first.postcode)
        assertEquals("Aachen", first.city)
    }

    @Test
    fun `falls back through town and village when city is absent`() {
        val results = NominatimClient.parse(sample)!!
        assertEquals("Vaals", results[1].city)
        assertNull(results[1].housenumber)
        assertNull(results[1].postcode)
    }

    @Test
    fun `an empty result array parses as an empty list, not null`() {
        val results = NominatimClient.parse("[]")
        assertNotNull(results)
        assertTrue(results!!.isEmpty())
    }

    @Test
    fun `garbage and non-array JSON return null instead of throwing`() {
        assertNull(NominatimClient.parse("not json at all"))
        assertNull(NominatimClient.parse("{}"))
    }

    @Test
    fun `a hit missing lat or lon is dropped rather than crashing the whole parse`() {
        val results = NominatimClient.parse(
            """[{"lat": "50.0", "display_name": "no lon here"}]"""
        )
        assertNotNull(results)
        assertTrue(results!!.isEmpty())
    }

    @Test
    fun `searching a blank query returns an empty list without a network call`() {
        assertEquals(emptyList<Any>(), NominatimClient.search("   "))
    }
}
