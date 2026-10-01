package com.example.freizeit.ui.map

import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
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
    fun `All is in the same single-select group as the category and verdict chips`() {
        val all = MapFilters(allCategories = true)
        assertEquals(all, MapFilters(activeCategory = "park").toggleAllCategories())
        assertEquals(all, MapFilters(favoritesOnly = true).toggleAllCategories())
        assertEquals(all, MapFilters(searchQuery = "caf").toggleAllCategories())
        assertEquals(MapFilters(activeCategory = "park"), all.toggleCategory("park"))
        assertEquals(MapFilters(wantToGoOnly = true), all.toggleWantToGoOnly())
    }

    @Test
    fun `tapping All again clears it`() {
        assertEquals(none, MapFilters(allCategories = true).toggleAllCategories())
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
    fun `a fresh MapFilters has nothing active`() {
        assertEquals(null, none.activeCategory)
        assertEquals(false, none.allCategories)
        assertEquals(false, none.favoritesOnly)
        assertEquals(false, none.wantToGoOnly)
        assertEquals(null, none.searchQuery)
    }

    private val cafe = Poi(id = "c", category = "cafe", lat = 50.9, lon = 6.9, name = "Café Leni")

    @Test
    fun `opening a sheet keeps filters that already show the place`() {
        val category = MapFilters(activeCategory = "cafe")
        assertEquals(category, category.revealing(cafe, null))
        val search = MapFilters(searchQuery = "Leni")
        assertEquals(search, search.revealing(cafe, null))
        val favorites = MapFilters(favoritesOnly = true)
        assertEquals(favorites, favorites.revealing(cafe, Verdict.VALUE_FAVORITE))
        val all = MapFilters(allCategories = true)
        assertEquals(all, all.revealing(cafe, null))
    }

    @Test
    fun `opening a sheet for a hidden place turns All on`() {
        val all = MapFilters(allCategories = true)
        assertEquals(all, none.revealing(cafe, null))
        assertEquals(all, MapFilters(activeCategory = "park").revealing(cafe, null))
        assertEquals(all, MapFilters(searchQuery = "Zoo").revealing(cafe, null))
        assertEquals(all, MapFilters(favoritesOnly = true).revealing(cafe, Verdict.VALUE_WANT_TO_GO))
        assertEquals(all, MapFilters(wantToGoOnly = true).revealing(cafe, null))
    }
}
