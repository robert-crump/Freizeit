package com.example.freizeit.ui.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.freizeit.domain.suggestion.Suggestion

/** One rendered widget row — already resolved to display strings so [SuggestionWidget]'s
 *  Glance composable does no formatting of its own (name/travel-time i18n happens once, in
 *  [SuggestionWidgetContent.rows], from Android string resources supplied by the caller). */
data class SuggestionWidgetRow(
    val poiId: String,
    val name: String,
    /** Null when [Suggestion.travelMinutes] is null (no location fix yet) — the row shows the
     *  name alone rather than a placeholder. */
    val travelLabel: String?
)

/**
 * Pure data prep for the minimal top-3(-ish) widget (#51): mirrors Home's own deck
 * (SuggestionEngine.rankAll/withinRadius, computed by [SuggestionWidget]) into exactly the
 * `name + travel time` rows the widget shows, and how many of them fit a given widget size.
 * Kept pure/Compose-runtime-only (no Context, no Glance types) so both halves are unit-testable
 * without Robolectric or a rendered widget.
 */
object SuggestionWidgetContent {

    /** Rows fit 1 at the smallest (2x1) size up to this many at the tallest resize step. */
    const val MAX_ROWS = 5

    private const val WIDGET_WIDTH_DP = 120

    /** One row height per step, "70dp * cells - 16dp" (Android's home-screen cell-size
     *  formula) for 1..[MAX_ROWS] cells tall. Same list drives [SizeMode.Responsive]'s declared
     *  breakpoints in [SuggestionWidget] and this file's [rowCountForSize] lookup, so the two
     *  can never disagree about what a given size means. */
    val WIDGET_SIZES: List<DpSize> = (1..MAX_ROWS).map { cells ->
        DpSize(WIDGET_WIDTH_DP.dp, (70 * cells - 16).dp)
    }

    /** How many rows a widget of [size] should show — the tallest declared [WIDGET_SIZES] step
     *  at or below [size]'s height, floored to 1 row (never zero: a widget always has *some*
     *  height once placed) and capped at [MAX_ROWS]. */
    fun rowCountForSize(size: DpSize, sizes: List<DpSize> = WIDGET_SIZES): Int {
        val stepIndex = sizes.indexOfLast { size.height >= it.height }
        return (stepIndex + 1).coerceIn(1, sizes.size)
    }

    /** The deck's top [maxRows] as display-ready rows. [customNames] and the two formatters
     *  mirror how Home itself resolves a display name ([com.example.freizeit.ui.map.displayName])
     *  and a travel-time label ([com.example.freizeit.ui.common.DurationBadge]) — duplicated here
     *  as plain functions rather than reused directly since both call-sites are `@Composable`. */
    fun rows(
        deck: List<Suggestion>,
        customNames: Map<String, String>,
        maxRows: Int,
        unnamedLabel: (category: String) -> String,
        travelLabel: (minutes: Int) -> String
    ): List<SuggestionWidgetRow> =
        deck.take(maxRows.coerceAtLeast(0)).map { suggestion ->
            val poi = suggestion.poi
            SuggestionWidgetRow(
                poiId = poi.id,
                name = customNames[poi.id] ?: poi.name ?: unnamedLabel(poi.category),
                travelLabel = suggestion.travelMinutes?.let(travelLabel)
            )
        }
}
