package com.example.freizeit.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Single shared "recompute and push" entry point for [SuggestionWidget] (issue #54). Triggers
 * Glance to re-run [SuggestionWidget.provideGlance] for every placed instance — which already
 * does the fresh location fetch, weather refresh, and deck recompute (issue #51) — rather than
 * duplicating that fetch-rank-render sequence here. The manual refresh button
 * ([RefreshWidgetAction]) calls this; the scheduled (#55) and event-driven (#56) refresh triggers
 * are expected to call the same function instead of each reimplementing it.
 *
 * A no-op when the widget isn't currently placed on any home screen ([updateAll] over an empty
 * instance set does nothing).
 */
object SuggestionWidgetUpdater {
    suspend fun refreshAndPush(context: Context) {
        SuggestionWidget().updateAll(context)
    }
}
