package com.example.freizeit.ui.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.suggestion.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionWidgetContentTest {

    private fun poi(id: String, name: String? = "Café Central", category: String = "cafe") =
        Poi(id = id, category = category, lat = 50.0, lon = 6.0, name = name)

    private fun suggestion(id: String, travelMinutes: Int? = 12, name: String? = "Café Central") =
        Suggestion(
            poi = poi(id, name),
            score = 0.0,
            distanceMeters = travelMinutes?.let { it * 250.0 },
            travelMinutes = travelMinutes,
            openStatus = OpenStatus.UNKNOWN,
            reasons = emptyList(),
            verdictValue = Verdict.VALUE_FAVORITE
        )

    @Test
    fun `rows caps at maxRows, best-first`() {
        val deck = listOf(suggestion("a"), suggestion("b"), suggestion("c"), suggestion("d"))
        val rows = SuggestionWidgetContent.rows(
            deck, customNames = emptyMap(), maxRows = 3,
            unnamedLabel = { "Unnamed" }, travelLabel = { "$it min" }
        )
        assertEquals(listOf("a", "b", "c"), rows.map { it.poiId })
    }

    @Test
    fun `travel label formats minutes, null travel means null label`() {
        val deck = listOf(suggestion("a", travelMinutes = 7), suggestion("b", travelMinutes = null))
        val rows = SuggestionWidgetContent.rows(
            deck, customNames = emptyMap(), maxRows = 5,
            unnamedLabel = { "Unnamed" }, travelLabel = { "$it min" }
        )
        assertEquals("7 min", rows[0].travelLabel)
        assertNull(rows[1].travelLabel)
    }

    @Test
    fun `custom name wins over poi name, falls back to unnamed label`() {
        val deck = listOf(suggestion("a", name = "OSM Name"), suggestion("b", name = null))
        val rows = SuggestionWidgetContent.rows(
            deck, customNames = mapOf("a" to "My Name"), maxRows = 5,
            unnamedLabel = { category -> "Unnamed $category" }, travelLabel = { "$it min" }
        )
        assertEquals("My Name", rows[0].name)
        assertEquals("Unnamed cafe", rows[1].name)
    }

    @Test
    fun `rowCountForSize floors to the step at or below, min 1 max the list size`() {
        val sizes = listOf(DpSize(120.dp, 54.dp), DpSize(120.dp, 124.dp), DpSize(120.dp, 194.dp))
        assertEquals(1, SuggestionWidgetContent.rowCountForSize(DpSize(120.dp, 30.dp), sizes))
        assertEquals(1, SuggestionWidgetContent.rowCountForSize(DpSize(120.dp, 54.dp), sizes))
        assertEquals(2, SuggestionWidgetContent.rowCountForSize(DpSize(120.dp, 130.dp), sizes))
        assertEquals(3, SuggestionWidgetContent.rowCountForSize(DpSize(120.dp, 500.dp), sizes))
    }

    @Test
    fun `WIDGET_SIZES has one step per row from 1 to MAX_ROWS, strictly increasing height`() {
        val sizes = SuggestionWidgetContent.WIDGET_SIZES
        assertEquals(SuggestionWidgetContent.MAX_ROWS, sizes.size)
        for (i in 1 until sizes.size) {
            assert(sizes[i].height > sizes[i - 1].height) { "sizes must strictly increase in height" }
        }
        // Every declared step round-trips to its own 1-based row count.
        sizes.forEachIndexed { index, size ->
            assertEquals(index + 1, SuggestionWidgetContent.rowCountForSize(size))
        }
    }
}
