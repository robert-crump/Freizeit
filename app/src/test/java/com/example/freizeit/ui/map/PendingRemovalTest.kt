package com.example.freizeit.ui.map

import com.example.freizeit.data.entity.Poi
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [excludePendingRemoval] — the pure half of the optimistic delete/hide-with-undo (#47,
 *  #73): the marker disappearing immediately, before anything is actually written. The
 *  DB-touching half ([MapViewModel.commitPendingRemoval]/[MapViewModel.undoRemovePlace]) isn't
 *  independently unit-tested, matching this codebase's existing convention of testing ViewModel
 *  behavior through its extracted pure functions rather than the ViewModel itself. */
class PendingRemovalTest {

    private fun poi(id: String) = Poi(id = id, category = "cafe", lat = 50.9, lon = 6.9, name = "Place $id")

    private val pois = listOf(poi("custom/1"), poi("node/2"), poi("custom/3"))

    @Test
    fun `no pending removal leaves the list untouched`() {
        assertEquals(pois, excludePendingRemoval(pois, pendingRemovalId = null))
    }

    @Test
    fun `a pending delete hides just that one custom place`() {
        assertEquals(listOf("node/2", "custom/3"), excludePendingRemoval(pois, "custom/1").map { it.id })
    }

    @Test
    fun `a pending hide hides just that one OSM place`() {
        assertEquals(listOf("custom/1", "custom/3"), excludePendingRemoval(pois, "node/2").map { it.id })
    }

    @Test
    fun `a pending removal for an id not present is a no-op`() {
        assertEquals(pois, excludePendingRemoval(pois, pendingRemovalId = "custom/unknown"))
    }

    @Test
    fun `an empty list stays empty regardless of pending removal`() {
        assertEquals(emptyList<Poi>(), excludePendingRemoval(emptyList(), pendingRemovalId = "custom/1"))
    }
}
