package com.example.freizeit.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.R
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.repository.observeAllByVerdictValues
import com.example.freizeit.domain.suggestion.SuggestionContext
import com.example.freizeit.domain.suggestion.SuggestionEngine
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.util.GeoDistance
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

/**
 * Single shared "recompute and push" entry point for [SuggestionWidget] (issue #54). The manual
 * refresh button ([RefreshWidgetAction]), the scheduled (#55) and the event-driven (#56) refresh
 * triggers all call [refreshAndPush] instead of each reimplementing the fetch-rank-render
 * sequence.
 *
 * The computed content is stored in each widget's own Glance state (#67), and the widget renders
 * from that state only, so a carousel arrow tap re-renders without reshuffling the deck, and every
 * recompute resets the carousel to suggestion #1.
 */
object SuggestionWidgetUpdater {

    /** A no-op when the widget isn't currently placed on any home screen. */
    suspend fun refreshAndPush(context: Context) {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(SuggestionWidget::class.java)
        if (glanceIds.isEmpty()) return
        val state = compute(context)
        glanceIds.forEach { store(context, it, state) }
        SuggestionWidget().updateAll(context)
    }

    suspend fun store(context: Context, glanceId: GlanceId, state: SuggestionWidgetState) {
        updateAppWidgetState(context, glanceId) { prefs ->
            SuggestionWidgetContent.storeRecomputed(prefs, state)
        }
    }

    /**
     * Mirrors Home's own suggestion deck (issue #51) — no ranking logic of its own, straight reuse
     * of [SuggestionEngine] over the same favorite/want-to-go pool, cached location, and weather
     * snapshot [com.example.freizeit.ui.home.HomeViewModel] uses.
     */
    suspend fun compute(context: Context): SuggestionWidgetState {
        val container = (context.applicationContext as FreizeitApplication).container
        val database = container.database

        container.locationRepository.refreshOnce()
        val location = container.locationRepository.location.value
        container.weatherRepository.refresh(
            lat = location?.lat ?: FALLBACK_LAT,
            lon = location?.lon ?: FALLBACK_LON
        )
        val weather = container.weatherRepository.snapshot.value

        val candidatePois = observeAllByVerdictValues(
            database.poiDao(),
            database.customPoiDao(),
            database.poiOverrideDao(),
            listOf(Verdict.VALUE_FAVORITE, Verdict.VALUE_WANT_TO_GO)
        ).first()
        val verdicts = database.verdictDao().getAll().associateBy { it.placeId }
        val visits = database.visitDao().getAll().groupBy({ it.placeId }, { it.visitedAt })
        val radiusKm = container.settingsRepository.suggestionRadiusKm.first()

        val candidatesInRange = SuggestionEngine.withinRadius(candidatePois, location, radiusKm * 1000.0)
        val suggestionContext = SuggestionContext(
            now = LocalDateTime.now(),
            location = location,
            weather = weather,
            verdicts = verdicts,
            visits = visits
        )
        val deck = SuggestionEngine.rankAll(candidatesInRange, suggestionContext)

        // Mirrors HomeViewModel.uiState's own hasVerdictedPlaces/hasVerdictedPlacesWithinRadius
        // exactly (issue #53) — same two booleans, same source data, so the widget and Home never
        // disagree about which of the three states applies.
        val hasVerdictedPlaces = candidatePois.isNotEmpty()
        val hasVerdictedPlacesWithinRadius = candidatePois.isEmpty() || candidatesInRange.isNotEmpty()

        return SuggestionWidgetContent.state(
            deck = deck,
            hasVerdictedPlaces = hasVerdictedPlaces,
            hasVerdictedPlacesWithinRadius = hasVerdictedPlacesWithinRadius,
            noFavoritesHint = context.getString(R.string.widget_no_favorites_hint),
            noSuggestionsWithinRadiusHint =
                context.getString(R.string.widget_no_suggestions_within_radius_hint, radiusKm),
            unnamedLabel = { category ->
                context.getString(R.string.map_unnamed, categoryDisplayName(category).lowercase())
            },
            distanceLabel = GeoDistance::format,
            travelLabel = { minutes -> context.getString(R.string.duration_minutes, minutes) }
        )
    }

    // Same Aachen fallback HomeViewModel uses (issue #22) when there's no location fix yet.
    private const val FALLBACK_LAT = 50.7753
    private const val FALLBACK_LON = 6.0839
}
