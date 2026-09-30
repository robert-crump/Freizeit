package com.example.freizeit.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CategoryIconTest {

    @Test
    fun playgroundUsesSwing() {
        assertSame(SwingIcon, categoryIcon("playground"))
    }

    @Test
    fun otherCategoriesKeepTheirMaterialIcons() {
        val expected = mapOf(
            "park" to "Filled.Park",
            "cafe" to "Filled.LocalCafe",
            "restaurant" to "Filled.Restaurant",
            "ice_cream" to "Filled.Icecream",
            "shop" to "Filled.Storefront",
            "tourism" to "Filled.Attractions",
            "leisure_other" to "Filled.Interests",
            "office" to "Filled.Business",
            "craft" to "Filled.Handyman",
            "historic" to "Filled.Museum",
            UNKNOWN_CATEGORY to "Filled.Place",
            "something_else" to "Filled.Place"
        )
        expected.forEach { (category, iconName) ->
            assertEquals(category, iconName, categoryIcon(category).name)
        }
    }
}
