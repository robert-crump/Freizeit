package com.example.freizeit.ui.home

import com.example.freizeit.data.entity.Favorite
import com.example.freizeit.util.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAnchorTest {

    private val location = LatLon(50.9000, 6.9000)

    private fun favorite(id: Long, name: String, lat: Double, lon: Double) =
        Favorite(id = id, name = name, address = null, lat = lat, lon = lon)

    @Test
    fun `favorite within radius is returned as the anchor`() {
        // ~80 m north of location.
        val home = favorite(1, "Home", lat = 50.9007, lon = 6.9000)
        assertEquals(home, nearestFavoriteWithin(location, listOf(home)))
    }

    @Test
    fun `favorite outside radius is not an anchor`() {
        // ~1.1 km east of location — well outside the 300 m anchor radius.
        val far = favorite(1, "Far away", lat = 50.9000, lon = 6.9160)
        assertNull(nearestFavoriteWithin(location, listOf(far)))
    }

    @Test
    fun `the nearest of several favorites within radius wins`() {
        val home = favorite(1, "Home", lat = 50.9007, lon = 6.9000) // ~80 m
        val closer = favorite(2, "Closer", lat = 50.9002, lon = 6.9000) // ~22 m

        assertEquals(closer, nearestFavoriteWithin(location, listOf(home, closer)))
    }

    @Test
    fun `no favorites means no anchor`() {
        assertNull(nearestFavoriteWithin(location, emptyList()))
    }
}
