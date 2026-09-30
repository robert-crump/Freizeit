package com.example.freizeit.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapFiltersTest {

    private val none = MapFilters()

    @Test
    fun `toggling a category selects it, toggling it again clears it`() {
        val selected = none.toggleCategory("playground")
        assertEquals(MapFilters(activeCategory = "playground"), selected)
        assertEquals(none, selected.toggleCategory("playground"))
    }

    @Test
    fun `a different category replaces the active one`() {
        assertEquals(
            MapFilters(activeCategory = "park"),
            none.toggleCategory("playground").toggleCategory("park")
        )
    }

    @Test
    fun `favorites only clears category, want to go and search`() {
        val before = MapFilters(activeCategory = "park")
        assertEquals(MapFilters(favoritesOnly = true), before.toggleFavoritesOnly())
        assertEquals(MapFilters(favoritesOnly = true), MapFilters(wantToGoOnly = true).toggleFavoritesOnly())
        assertEquals(MapFilters(favoritesOnly = true), MapFilters(searchQuery = "caf").toggleFavoritesOnly())
        assertEquals(none, MapFilters(favoritesOnly = true).toggleFavoritesOnly())
    }

    @Test
    fun `want to go only clears the other filters`() {
        assertEquals(MapFilters(wantToGoOnly = true), MapFilters(favoritesOnly = true).toggleWantToGoOnly())
        assertEquals(none, MapFilters(wantToGoOnly = true).toggleWantToGoOnly())
    }

    @Test
    fun `committing a search trims it and clears the other filters`() {
        assertEquals(
            MapFilters(searchQuery = "Leni"),
            MapFilters(activeCategory = "park").commitSearch("  Leni ")
        )
    }

    @Test
    fun `a blank search only clears the search`() {
        assertEquals(none, MapFilters(searchQuery = "Leni").commitSearch("   "))
        assertEquals(none, MapFilters(searchQuery = "Leni").clearSearch())
    }

    @Test
    fun `a fresh MapFilters has nothing active, which is what opening a place resets to`() {
        assertEquals(null, none.activeCategory)
        assertEquals(false, none.favoritesOnly)
        assertEquals(false, none.wantToGoOnly)
        assertEquals(null, none.searchQuery)
    }
}
