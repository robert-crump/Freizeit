package com.example.freizeit.data

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PoiJsonParserTest {

    private fun parse(json: String) = PoiJsonParser.parse(StringReader(json))

    @Test
    fun `parses a valid file with optional fields present and absent`() {
        val result = parse(
            """
            {
              "generated": "2026-07-05T17:08:17+00:00",
              "categories": ["playground", "restaurant"],
              "pois": [
                {
                  "id": "node/286560726",
                  "category": "restaurant",
                  "lat": 50.4261098,
                  "lon": 6.2054792,
                  "name": "Bütgenbacher Hof",
                  "opening_hours": "Mo-Su 11:00-22:00",
                  "street": "Marktplatz",
                  "housenumber": "8",
                  "postcode": "4750",
                  "city": "Bütgenbach"
                },
                { "id": "way/123", "category": "playground", "lat": 50.0, "lon": 6.0 }
              ]
            }
            """
        )

        assertEquals("2026-07-05T17:08:17+00:00", result.generatedAt)
        assertEquals(2, result.pois.size)

        val restaurant = result.pois[0]
        assertEquals("node/286560726", restaurant.id)
        assertEquals("restaurant", restaurant.category)
        assertEquals(50.4261098, restaurant.lat, 1e-9)
        assertEquals("Bütgenbacher Hof", restaurant.name)
        assertEquals("Mo-Su 11:00-22:00", restaurant.openingHours)
        assertEquals("Bütgenbach", restaurant.city)

        val playground = result.pois[1]
        assertEquals("way/123", playground.id)
        assertNull(playground.name)
        assertNull(playground.openingHours)
        assertEquals(false, playground.missingFromOsm)
    }

    @Test
    fun `empty pois list is valid`() {
        assertEquals(0, parse("""{"pois": []}""").pois.size)
    }

    @Test
    fun `rejects non-JSON content`() {
        assertThrows(PoiParseException::class.java) { parse("not json at all") }
    }

    @Test
    fun `rejects JSON without a pois list`() {
        assertThrows(PoiParseException::class.java) { parse("""{"foo": 1}""") }
    }

    @Test
    fun `rejects a top-level array`() {
        assertThrows(PoiParseException::class.java) { parse("""[1, 2]""") }
    }

    @Test
    fun `rejects a poi without id`() {
        assertThrows(PoiParseException::class.java) {
            parse("""{"pois": [{"category": "cafe", "lat": 1.0, "lon": 2.0}]}""")
        }
    }

    @Test
    fun `rejects a poi with non-numeric lat`() {
        assertThrows(PoiParseException::class.java) {
            parse("""{"pois": [{"id": "node/1", "category": "cafe", "lat": "north", "lon": 2.0}]}""")
        }
    }

    @Test
    fun `error message names the offending poi`() {
        val e = assertThrows(PoiParseException::class.java) {
            parse("""{"pois": [{"id": "node/1", "category": "cafe", "lat": 1.0, "lon": 2.0}, {"id": "node/2"}]}""")
        }
        assertEquals(true, e.message!!.contains("#1"))
    }
}
