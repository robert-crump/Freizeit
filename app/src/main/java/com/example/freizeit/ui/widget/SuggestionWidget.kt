package com.example.freizeit.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.R
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.suggestion.SuggestionContext
import com.example.freizeit.domain.suggestion.SuggestionEngine
import com.example.freizeit.ui.MainActivity
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.theme.DarkColors
import com.example.freizeit.ui.theme.LightColors
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

/** Fallback below Android 12's dynamic color (issue #51's own acceptance criterion) — the exact
 *  same fixed scheme [com.example.freizeit.ui.theme.FreizeitTheme] falls back to, just wrapped
 *  for Glance via [ColorProviders] instead of passed straight to Compose's `MaterialTheme`. */
private val SuggestionWidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/**
 * Home-screen widget mirroring Home's own suggestion deck (issue #51) — no ranking logic of its
 * own, straight reuse of [SuggestionEngine] over the same favorite/want-to-go pool, cached
 * location, and weather snapshot [com.example.freizeit.ui.home.HomeViewModel] uses. Content is
 * computed once per Glance update cycle: widget added, a manual refresh tap (issue #54, via
 * [SuggestionWidgetUpdater]), or a future scheduled/event-driven trigger (#55/#56) — no scheduling
 * of its own yet.
 */
class SuggestionWidget : GlanceAppWidget() {

    /** Declares the same height steps [SuggestionWidgetContent.rowCountForSize] reads back —
     *  Glance renders [provideGlance] once per declared size and the launcher picks whichever is
     *  closest to the widget's actual placed size. */
    override val sizeMode = SizeMode.Responsive(SuggestionWidgetContent.WIDGET_SIZES.toSet())

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as FreizeitApplication).container
        val database = container.database

        container.locationRepository.refreshOnce()
        val location = container.locationRepository.location.value
        container.weatherRepository.refresh(
            lat = location?.lat ?: FALLBACK_LAT,
            lon = location?.lon ?: FALLBACK_LON
        )
        val weather = container.weatherRepository.snapshot.value

        val candidatePois = database.poiDao()
            .observeByVerdictValues(listOf(Verdict.VALUE_FAVORITE, Verdict.VALUE_WANT_TO_GO))
            .first()
        val verdicts = database.verdictDao().getAll().associateBy { it.placeId }
        val visits = database.visitDao().getAll().groupBy({ it.placeId }, { it.visitedAt })
        val customNames = database.poiCustomNameDao().getAll().associate { it.placeId to it.customName }
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

        val rows = SuggestionWidgetContent.rows(
            deck = deck,
            customNames = customNames,
            maxRows = SuggestionWidgetContent.MAX_ROWS,
            unnamedLabel = { category ->
                context.getString(R.string.map_unnamed, categoryDisplayName(category).lowercase())
            },
            travelLabel = { minutes -> context.getString(R.string.duration_minutes, minutes) }
        )

        // Built here (plain Context, no ambiguity between actionStartActivity's Intent-based and
        // reified-type overloads) rather than inside the composable below — generic "open Home"
        // for this slice; #52 replaces this with a per-row PendingIntent carrying the tapped
        // place's id via MainActivity.EXTRA_TARGET_POI_ID.
        val openAppAction = actionStartActivity(Intent(context, MainActivity::class.java))
        val refreshContentDescription = context.getString(R.string.widget_refresh_content_description)

        provideContent {
            SuggestionWidgetBody(rows, openAppAction, refreshContentDescription)
        }
    }

    @Composable
    private fun SuggestionWidgetBody(
        rows: List<SuggestionWidgetRow>,
        openAppAction: Action,
        refreshContentDescription: String
    ) {
        val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            SuggestionWidgetColors
        }
        GlanceTheme(colors = colors) {
            val rowCount = SuggestionWidgetContent.rowCountForSize(LocalSize.current)
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(8.dp)
            ) {
                // Distinct tap target from the suggestion rows below (issue #54) — forces the
                // shared recompute-and-push routine instead of waiting for a schedule.
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_refresh),
                        contentDescription = refreshContentDescription,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier
                            .size(20.dp)
                            .clickable(actionRunCallback<RefreshWidgetAction>())
                    )
                }
                rows.take(rowCount).forEach { row -> SuggestionRow(row, openAppAction) }
            }
        }
    }

    @Composable
    private fun SuggestionRow(row: SuggestionWidgetRow, openAppAction: Action) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(openAppAction)
        ) {
            Text(
                text = row.name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium)
            )
            if (row.travelLabel != null) {
                Text(
                    text = row.travelLabel,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }

    companion object {
        // Same Aachen fallback HomeViewModel uses (issue #22) when there's no location fix yet.
        private const val FALLBACK_LAT = 50.7753
        private const val FALLBACK_LON = 6.0839
    }
}
