package com.example.freizeit.ui.map

import com.example.freizeit.data.entity.CustomPoi
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [excludePendingDelete] — the pure half of issue #47's optimistic delete-with-undo
 *  (the marker disappearing immediately, before the underlying row is actually removed). The
 *  DB-touching half ([MapViewModel.commitPendingDelete]/[MapViewModel.undoDeleteCustomPoi]) isn't
 *  independently unit-tested, matching this codebase's existing convention of testing ViewModel
 *  behavior through its extracted pure functions rather than the ViewModel itself. */
class PendingCustomPoiDeleteTest {

    private fun customPoi(id: String) =
        CustomPoi(id = id, category = "cafe", lat = 50.9, lon = 6.9, name = "Place $id")

    private val pois = listOf(customPoi("custom/1"), customPoi("custom/2"), customPoi("custom/3"))

    @Test
    fun `no pending delete leaves the list untouched`() {
        assertEquals(pois, excludePendingDelete(pois, pendingDeleteId = null))
    }

    @Test
    fun `a pending delete hides just that one custom poi`() {
        val result = excludePendingDelete(pois, pendingDeleteId = "custom/2")

        assertEquals(listOf("custom/1", "custom/3"), result.map { it.id })
    }

    @Test
    fun `a pending delete for an id not present is a no-op`() {
        assertEquals(pois, excludePendingDelete(pois, pendingDeleteId = "custom/unknown"))
    }

    @Test
    fun `an empty list stays empty regardless of pending delete`() {
        assertEquals(emptyList<CustomPoi>(), excludePendingDelete(emptyList(), pendingDeleteId = "custom/1"))
    }
}
