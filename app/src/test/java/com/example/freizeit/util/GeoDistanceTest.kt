package com.example.freizeit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `zero distance for identical points`() {
        assertEquals(0.0, GeoDistance.metersBetween(50.9, 6.9, 50.9, 6.9), 0.001)
    }

    @Test
    fun `cologne cathedral to bonn minster is about 25 km`() {
        val meters = GeoDistance.metersBetween(50.9413, 6.9583, 50.7328, 7.0994)
        assertTrue("was $meters", meters in 24_000.0..27_000.0)
    }

    @Test
    fun `formats below one km in meters`() {
        assertEquals("340 m", GeoDistance.format(340.7))
        assertEquals("0 m", GeoDistance.format(0.4))
    }

    @Test
    fun `formats one km and above with one decimal`() {
        val formatted = GeoDistance.format(1234.0)
        // decimal separator is locale-dependent
        assertTrue("was $formatted", formatted == "1.2 km" || formatted == "1,2 km")
    }
}
