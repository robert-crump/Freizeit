package com.example.freizeit.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceFormTest {

    private val initial = PlaceFormValues(name = "Stadtpark", category = "park", city = "Aachen")

    @Test
    fun `nothing changed disables save`() {
        assertFalse(canSavePlaceForm(initial, initial, requiresName = false))
        assertFalse(canSavePlaceForm(initial.copy(name = " Stadtpark "), initial, requiresName = false))
    }

    @Test
    fun `a change enables save`() {
        assertTrue(canSavePlaceForm(initial.copy(city = "Würselen"), initial, requiresName = false))
    }

    @Test
    fun `an OSM place may clear its name, which reverts to OSM`() {
        assertTrue(canSavePlaceForm(initial.copy(name = ""), initial, requiresName = false))
    }

    @Test
    fun `a custom place needs a name and a category`() {
        assertFalse(canSavePlaceForm(initial.copy(name = "  "), initial, requiresName = true))
        val new = PlaceFormValues()
        assertFalse(canSavePlaceForm(new.copy(name = "Our Café"), new, requiresName = true))
        assertTrue(canSavePlaceForm(new.copy(name = "Our Café", category = "cafe"), new, requiresName = true))
    }
}
