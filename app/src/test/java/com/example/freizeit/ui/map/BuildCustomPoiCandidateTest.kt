package com.example.freizeit.ui.map

import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.isCustomPoiId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [buildCustomPoiCandidate] — issue #47's edit-save path (reusing an existing custom
 *  POI's id so Save updates it in place) alongside the pre-existing create path (a fresh id per
 *  save). */
class BuildCustomPoiCandidateTest {

    private val existing = CustomPoi(
        id = "custom/existing-id",
        category = "cafe",
        lat = 50.9,
        lon = 6.9,
        name = "Old Name",
        street = "Old Street",
        housenumber = "1",
        postcode = "4000",
        city = "Old City",
        openingHours = "Mo-Fr 09:00-18:00"
    )

    @Test
    fun `with no initial, mints a fresh custom poi id`() {
        val result = buildCustomPoiCandidate(
            initial = null,
            category = "cafe",
            lat = 50.9,
            lon = 6.9,
            name = "New Café",
            openingHours = null,
            street = null,
            housenumber = null,
            postcode = null,
            city = null
        )

        assertTrue(isCustomPoiId(result.id))
    }

    @Test
    fun `two saves with no initial mint two different ids`() {
        fun save() = buildCustomPoiCandidate(
            initial = null, category = "cafe", lat = 50.9, lon = 6.9, name = "Café",
            openingHours = null, street = null, housenumber = null, postcode = null, city = null
        )

        assertNotEquals(save().id, save().id)
    }

    @Test
    fun `editing reuses the original id instead of minting a new one`() {
        val result = buildCustomPoiCandidate(
            initial = existing,
            category = "restaurant",
            lat = existing.lat,
            lon = existing.lon,
            name = "New Name",
            openingHours = "Mo-Su 10:00-22:00",
            street = "New Street",
            housenumber = "2",
            postcode = "4001",
            city = "New City"
        )

        assertEquals(existing.id, result.id)
        assertEquals("restaurant", result.category)
        assertEquals("New Name", result.name)
        assertEquals("New Street", result.street)
    }
}
