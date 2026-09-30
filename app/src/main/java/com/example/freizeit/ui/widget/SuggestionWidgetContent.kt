package com.example.freizeit.ui.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.freizeit.domain.suggestion.Suggestion

/** One suggestion of the widget carousel — already resolved to display strings so
 *  [SuggestionWidget]'s Glance composable does no formatting of its own (name/label i18n happens
 *  once, in [SuggestionWidgetContent.rows], from Android string resources supplied by the caller). */
data class SuggestionWidgetRow(
    val poiId: String,
    val name: String,
    /** Drives the category circle icon, same glyph as the map marker (#67). */
    val category: String,
    /** "1.2 km · 15 min"; null when there's no location fix (no distance, no travel time) —
     *  the card shows the name alone rather than a placeholder. */
    val detailLabel: String?
)

/**
 * Pure data prep for the one-at-a-time carousel widget (#67, originally #51's rows): mirrors
 * Home's own deck (SuggestionEngine.rankAll/withinRadius, computed by [SuggestionWidgetUpdater])
 * into the top [CAROUSEL_SIZE] display-ready cards, plus the carousel's index math and how
 * both are persisted in the per-widget Glance state. Kept free of Context and Glance types so
 * it's unit-testable without Robolectric or a rendered widget.
 */
object SuggestionWidgetContent {

    /** The carousel cycles through at most this many of the deck's best suggestions. */
    const val CAROUSEL_SIZE = 3

    /** Smallest placeable width (3 cells) at 1 and 2 cells tall — the two layouts the widget has.
     *  More width or height only adds breathing room, never more suggestions. */
    val COMPACT_SIZE = DpSize(180.dp, 40.dp)
    val TALL_SIZE = DpSize(180.dp, 100.dp)
    val WIDGET_SIZES: List<DpSize> = listOf(COMPACT_SIZE, TALL_SIZE)

    /** Dots and larger text only from 2 cells tall — at 1 cell the two text lines fill it. */
    fun isTall(size: DpSize): Boolean = size.height >= TALL_SIZE.height

    /** The deck's top [maxRows] as display-ready cards. [customNames] and the formatters mirror
     *  how Home itself resolves a display name ([com.example.freizeit.ui.map.displayName]),
     *  distance ([com.example.freizeit.util.GeoDistance.format]) and travel time
     *  ([com.example.freizeit.ui.common.DurationBadge]) — plain functions here since those
     *  call-sites are `@Composable`. */
    fun rows(
        deck: List<Suggestion>,
        customNames: Map<String, String>,
        maxRows: Int,
        unnamedLabel: (category: String) -> String,
        distanceLabel: (meters: Double) -> String,
        travelLabel: (minutes: Int) -> String
    ): List<SuggestionWidgetRow> =
        deck.take(maxRows.coerceAtLeast(0)).map { suggestion ->
            val poi = suggestion.poi
            SuggestionWidgetRow(
                poiId = poi.id,
                name = customNames[poi.id] ?: poi.name ?: unnamedLabel(poi.category),
                category = poi.category,
                detailLabel = detailLabel(suggestion, distanceLabel, travelLabel)
            )
        }

    /** "distance · duration", either part dropped when missing, null when both are. */
    fun detailLabel(
        suggestion: Suggestion,
        distanceLabel: (meters: Double) -> String,
        travelLabel: (minutes: Int) -> String
    ): String? = listOfNotNull(
        suggestion.distanceMeters?.let(distanceLabel),
        suggestion.travelMinutes?.let(travelLabel)
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /** What the widget should render for one update cycle (#53) — mirrors the same three-way
     *  branch HomeScreen's `when` uses over `hasVerdictedPlaces`/`hasVerdictedPlacesWithinRadius`
     *  (the `!hasPois` case doesn't apply here: an empty POI table also means no favorites, so
     *  it already falls out of `hasVerdictedPlaces`). Either empty case fully replaces [rows] —
     *  never shown alongside it — so the caller only has one thing to render per cycle. */
    fun state(
        deck: List<Suggestion>,
        hasVerdictedPlaces: Boolean,
        hasVerdictedPlacesWithinRadius: Boolean,
        customNames: Map<String, String>,
        noFavoritesHint: String,
        noSuggestionsWithinRadiusHint: String,
        unnamedLabel: (category: String) -> String,
        distanceLabel: (meters: Double) -> String,
        travelLabel: (minutes: Int) -> String
    ): SuggestionWidgetState = when {
        !hasVerdictedPlaces -> SuggestionWidgetState.Hint(noFavoritesHint, HintDestination.EXPLORE)
        !hasVerdictedPlacesWithinRadius ->
            SuggestionWidgetState.Hint(noSuggestionsWithinRadiusHint, HintDestination.SETTINGS)
        else -> SuggestionWidgetState.Rows(
            rows(deck, customNames, CAROUSEL_SIZE, unnamedLabel, distanceLabel, travelLabel)
        )
    }

    /** Arrow step with wrap-around in both directions (3 → 1, 1 → 3). */
    fun stepIndex(index: Int, delta: Int, count: Int): Int =
        if (count <= 0) 0 else Math.floorMod(index + delta, count)

    /** Position dots, e.g. "● ○ ○" for index 0 of 3. */
    fun dots(index: Int, count: Int): String =
        (0 until count).joinToString(" ") { if (it == index) "●" else "○" }

    // --- Per-widget Glance state: the last computed content plus the carousel position. Arrow
    // taps only change the index and re-render; the deck is recomputed solely by
    // SuggestionWidgetUpdater (or on a widget's very first render), which resets the index. ---

    private val INDEX = intPreferencesKey("carousel_index")
    private val HINT_MESSAGE = stringPreferencesKey("hint_message")
    private val HINT_DESTINATION = stringPreferencesKey("hint_destination")
    private val CARD_COUNT = intPreferencesKey("card_count")
    private fun cardId(i: Int) = stringPreferencesKey("card_${i}_id")
    private fun cardName(i: Int) = stringPreferencesKey("card_${i}_name")
    private fun cardCategory(i: Int) = stringPreferencesKey("card_${i}_category")
    private fun cardDetail(i: Int) = stringPreferencesKey("card_${i}_detail")

    /** Replaces the stored content with a freshly computed [state] and resets to suggestion #1. */
    fun storeRecomputed(prefs: MutablePreferences, state: SuggestionWidgetState) {
        prefs.clear()
        prefs[INDEX] = 0
        when (state) {
            is SuggestionWidgetState.Hint -> {
                prefs[HINT_MESSAGE] = state.message
                prefs[HINT_DESTINATION] = state.destination.name
            }
            is SuggestionWidgetState.Rows -> {
                prefs[CARD_COUNT] = state.rows.size
                state.rows.forEachIndexed { i, row ->
                    prefs[cardId(i)] = row.poiId
                    prefs[cardName(i)] = row.name
                    prefs[cardCategory(i)] = row.category
                    row.detailLabel?.let { prefs[cardDetail(i)] = it }
                }
            }
        }
    }

    /** The stored content, or null if this widget has never been computed (or was stored by a
     *  build predating this format) — the caller then computes it. */
    fun storedState(prefs: Preferences): SuggestionWidgetState? {
        val hint = prefs[HINT_MESSAGE]
        if (hint != null) {
            val destination = prefs[HINT_DESTINATION]
                ?.let { name -> HintDestination.entries.firstOrNull { it.name == name } }
                ?: HintDestination.EXPLORE
            return SuggestionWidgetState.Hint(hint, destination)
        }
        val count = prefs[CARD_COUNT] ?: return null
        val rows = (0 until count).map { i ->
            SuggestionWidgetRow(
                poiId = prefs[cardId(i)] ?: return null,
                name = prefs[cardName(i)] ?: return null,
                category = prefs[cardCategory(i)] ?: return null,
                detailLabel = prefs[cardDetail(i)]
            )
        }
        return SuggestionWidgetState.Rows(rows)
    }

    /** Current carousel position, clamped into the stored card range. */
    fun storedIndex(prefs: Preferences): Int {
        val count = prefs[CARD_COUNT] ?: 0
        val index = prefs[INDEX] ?: 0
        return if (index in 0 until count) index else 0
    }

    /** One arrow tap: moves the stored index by [delta], wrapping. */
    fun storeStep(prefs: MutablePreferences, delta: Int) {
        prefs[INDEX] = stepIndex(storedIndex(prefs), delta, prefs[CARD_COUNT] ?: 0)
    }
}

/** Where a tap on [SuggestionWidgetState.Hint] should open the app to (#53) — resolved to an
 *  actual `Action`/route by the caller, kept as a plain enum here so this file stays free of
 *  Glance/Intent types. */
enum class HintDestination { EXPLORE, SETTINGS }

/** Result of [SuggestionWidgetContent.state]: either the carousel's cards, or a single compact
 *  hint replacing them entirely. */
sealed interface SuggestionWidgetState {
    data class Rows(val rows: List<SuggestionWidgetRow>) : SuggestionWidgetState
    data class Hint(val message: String, val destination: HintDestination) : SuggestionWidgetState
}
