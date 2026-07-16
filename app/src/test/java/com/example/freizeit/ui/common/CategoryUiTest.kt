package com.example.freizeit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #16: each of the 6 new categories from #14 needs its own distinct pin color. */
class CategoryUiTest {

    private val newCategories = listOf("shop", "tourism", "leisure_other", "office", "craft", "historic")
    private val fallbackColor = categoryColor("some_unknown_category")

    @Test
    fun `CATEGORY_ORDER includes all 11 known categories`() {
        assertEquals(
            listOf("playground", "park", "cafe", "restaurant", "ice_cream") + newCategories,
            CATEGORY_ORDER
        )
    }

    @Test
    fun `every category in CATEGORY_ORDER has a distinct color`() {
        val colors = CATEGORY_ORDER.map { categoryColor(it) }
        assertEquals("expected no two categories to share a color", CATEGORY_ORDER.size, colors.toSet().size)
    }

    @Test
    fun `new categories don't fall back to the shared unknown-category color`() {
        newCategories.forEach { category ->
            assertTrue(
                "expected $category to have its own color, not the fallback",
                categoryColor(category) != fallbackColor
            )
        }
    }

    @Test
    fun `new categories sort after the original 5 and before unknown categories`() {
        newCategories.forEach { category ->
            assertTrue(categoryOrderIndex(category) > categoryOrderIndex("ice_cream"))
            assertTrue(categoryOrderIndex(category) < categoryOrderIndex("some_unknown_category"))
        }
    }
}
