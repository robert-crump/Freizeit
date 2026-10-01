package com.example.freizeit.ui.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.suggestion.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionWidgetContentTest {

    private fun poi(id: String, name: String? = "Café Central", category: String = "cafe") =
        Poi(id = id, category = category, lat = 50.0, lon = 6.0, name = name)

    private fun suggestion(
        id: String,
        travelMinutes: Int? = 12,
        distanceMeters: Double? = travelMinutes?.let { it * 250.0 },
        name: String? = "Café Central",
        category: String = "cafe"
    ) = Suggestion(
        poi = poi(id, name, category),
        score = 0.0,
        distanceMeters = distanceMeters,
        travelMinutes = travelMinutes,
        openStatus = OpenStatus.UNKNOWN,
        reasons = emptyList(),
        verdictValue = Verdict.VALUE_FAVORITE
    )

    private fun rows(deck: List<Suggestion>, maxRows: Int = 5) =
        SuggestionWidgetContent.rows(
            deck, maxRows,
            unnamedLabel = { category -> "Unnamed $category" },
            distanceLabel = { "${it.toInt()} m" },
            travelLabel = { "$it min" }
        )

    private fun state(deck: List<Suggestion>, hasVerdictedPlaces: Boolean, withinRadius: Boolean) =
        SuggestionWidgetContent.state(
            deck = deck,
            hasVerdictedPlaces = hasVerdictedPlaces,
            hasVerdictedPlacesWithinRadius = withinRadius,
            noFavoritesHint = "No favorites yet", noSuggestionsWithinRadiusHint = "Nothing within 5 km",
            unnamedLabel = { category -> "Unnamed $category" },
            distanceLabel = { "${it.toInt()} m" },
            travelLabel = { "$it min" }
        )

    @Test
    fun `rows caps at maxRows, best-first`() {
        val deck = listOf(suggestion("a"), suggestion("b"), suggestion("c"), suggestion("d"))
        assertEquals(listOf("a", "b", "c"), rows(deck, maxRows = 3).map { it.poiId })
    }

    @Test
    fun `state keeps only the deck's top 3 for the carousel`() {
        val deck = listOf(suggestion("a"), suggestion("b"), suggestion("c"), suggestion("d"))
        val state = state(deck, hasVerdictedPlaces = true, withinRadius = true) as SuggestionWidgetState.Rows
        assertEquals(listOf("a", "b", "c"), state.rows.map { it.poiId })
    }

    @Test
    fun `state with a shorter deck keeps all of it`() {
        val state = state(listOf(suggestion("a")), hasVerdictedPlaces = true, withinRadius = true)
        assertEquals(1, (state as SuggestionWidgetState.Rows).rows.size)
    }

    @Test
    fun `detail label is distance dot duration, null without a location fix`() {
        val deck = listOf(
            suggestion("a", travelMinutes = 7, distanceMeters = 1200.0),
            suggestion("b", travelMinutes = null, distanceMeters = null)
        )
        val rows = rows(deck)
        assertEquals("1200 m · 7 min", rows[0].detailLabel)
        assertNull(rows[1].detailLabel)
    }

    @Test
    fun `detail label drops a missing part without a dangling separator`() {
        val label = SuggestionWidgetContent.detailLabel(
            suggestion("a", travelMinutes = null, distanceMeters = 800.0),
            distanceLabel = { "${it.toInt()} m" },
            travelLabel = { "$it min" }
        )
        assertEquals("800 m", label)
    }

    @Test
    fun `poi name is shown, falls back to unnamed label, category is kept`() {
        val deck = listOf(suggestion("a", name = "OSM Name"), suggestion("b", name = null, category = "playground"))
        val rows = rows(deck)
        assertEquals("OSM Name", rows[0].name)
        assertEquals("Unnamed playground", rows[1].name)
        assertEquals("playground", rows[1].category)
    }

    @Test
    fun `state with no verdicted places returns a hint pointing to Explore, regardless of deck`() {
        assertEquals(
            SuggestionWidgetState.Hint("No favorites yet", HintDestination.EXPLORE),
            state(listOf(suggestion("a")), hasVerdictedPlaces = false, withinRadius = false)
        )
    }

    @Test
    fun `state with verdicted places but none within radius returns a hint pointing to Settings`() {
        assertEquals(
            SuggestionWidgetState.Hint("Nothing within 5 km", HintDestination.SETTINGS),
            state(emptyList(), hasVerdictedPlaces = true, withinRadius = false)
        )
    }

    @Test
    fun `stepIndex wraps both ways`() {
        assertEquals(1, SuggestionWidgetContent.stepIndex(0, 1, 3))
        assertEquals(0, SuggestionWidgetContent.stepIndex(2, 1, 3))
        assertEquals(2, SuggestionWidgetContent.stepIndex(0, -1, 3))
        assertEquals(0, SuggestionWidgetContent.stepIndex(1, -1, 2))
        assertEquals(0, SuggestionWidgetContent.stepIndex(0, 1, 1))
        assertEquals(0, SuggestionWidgetContent.stepIndex(0, 1, 0))
    }

    @Test
    fun `dots mark the current position`() {
        assertEquals("● ○ ○", SuggestionWidgetContent.dots(0, 3))
        assertEquals("○ ○ ●", SuggestionWidgetContent.dots(2, 3))
        assertEquals("○ ●", SuggestionWidgetContent.dots(1, 2))
    }

    @Test
    fun `isTall only from 2 cells tall`() {
        assertFalse(SuggestionWidgetContent.isTall(DpSize(250.dp, 40.dp)))
        assertFalse(SuggestionWidgetContent.isTall(DpSize(250.dp, 60.dp)))
        assertTrue(SuggestionWidgetContent.isTall(DpSize(250.dp, 110.dp)))
    }

    @Test
    fun `nothing stored yet reads as null`() {
        assertNull(SuggestionWidgetContent.storedState(mutablePreferencesOf()))
    }

    @Test
    fun `stored rows and hints round-trip`() {
        val rowsState = state(
            listOf(suggestion("a"), suggestion("b", travelMinutes = null, distanceMeters = null)),
            hasVerdictedPlaces = true, withinRadius = true
        )
        val hint = SuggestionWidgetState.Hint("Nothing within 5 km", HintDestination.SETTINGS)
        for (original in listOf(rowsState, hint)) {
            val prefs = mutablePreferencesOf()
            SuggestionWidgetContent.storeRecomputed(prefs, original)
            assertEquals(original, SuggestionWidgetContent.storedState(prefs))
        }
    }

    @Test
    fun `arrow steps wrap through the stored cards, recompute resets to the first`() {
        val prefs = mutablePreferencesOf()
        val three = state(listOf(suggestion("a"), suggestion("b"), suggestion("c")), true, true)
        SuggestionWidgetContent.storeRecomputed(prefs, three)
        assertEquals(0, SuggestionWidgetContent.storedIndex(prefs))

        SuggestionWidgetContent.storeStep(prefs, -1)
        assertEquals(2, SuggestionWidgetContent.storedIndex(prefs))
        SuggestionWidgetContent.storeStep(prefs, 1)
        assertEquals(0, SuggestionWidgetContent.storedIndex(prefs))
        SuggestionWidgetContent.storeStep(prefs, 1)
        assertEquals(1, SuggestionWidgetContent.storedIndex(prefs))

        SuggestionWidgetContent.storeRecomputed(prefs, three)
        assertEquals(0, SuggestionWidgetContent.storedIndex(prefs))
    }

    @Test
    fun `recompute to a shorter deck drops the old cards and the index`() {
        val prefs = mutablePreferencesOf()
        SuggestionWidgetContent.storeRecomputed(prefs, state(listOf(suggestion("a"), suggestion("b"), suggestion("c")), true, true))
        SuggestionWidgetContent.storeStep(prefs, 2)
        val one = state(listOf(suggestion("x")), true, true)
        SuggestionWidgetContent.storeRecomputed(prefs, one)
        assertEquals(one, SuggestionWidgetContent.storedState(prefs))
        assertEquals(0, SuggestionWidgetContent.storedIndex(prefs))
        SuggestionWidgetContent.storeStep(prefs, 1)
        assertEquals(0, SuggestionWidgetContent.storedIndex(prefs))
    }
}
