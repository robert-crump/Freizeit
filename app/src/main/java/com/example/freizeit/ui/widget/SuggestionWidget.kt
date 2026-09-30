package com.example.freizeit.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.freizeit.R
import com.example.freizeit.ui.FreizeitDestination
import com.example.freizeit.ui.MainActivity
import com.example.freizeit.ui.map.categoryCircleMasks
import com.example.freizeit.ui.map.markerBackgroundColor
import com.example.freizeit.ui.map.markerForegroundColor
import com.example.freizeit.ui.theme.DarkColors
import com.example.freizeit.ui.theme.LightColors

/** Fallback below Android 12's dynamic color (issue #51's own acceptance criterion) — the exact
 *  same fixed scheme [com.example.freizeit.ui.theme.FreizeitTheme] falls back to, just wrapped
 *  for Glance via [ColorProviders] instead of passed straight to Compose's `MaterialTheme`. */
private val SuggestionWidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** Map-marker colors for the category circle's two tinted layers, day/night like the map. */
private val MarkerBackground = ColorProvider(day = markerBackgroundColor(false), night = markerBackgroundColor(true))
private val MarkerForeground = ColorProvider(day = markerForegroundColor(false), night = markerForegroundColor(true))

/**
 * Home-screen widget showing Home's suggestion deck as a one-at-a-time carousel over its top 3
 * (issue #67; the deck itself is #51's, computed by [SuggestionWidgetUpdater]). Glance widgets
 * can't take a horizontal swipe, so ‹ / › arrows cycle through the cards ([CarouselStepAction]).
 *
 * Renders purely from the per-widget Glance state: content is computed on a widget's first
 * render here, and afterwards only by [SuggestionWidgetUpdater.refreshAndPush] (manual refresh,
 * scheduled and event-driven updates), which also resets the carousel to #1.
 */
class SuggestionWidget : GlanceAppWidget() {

    /** Glance renders [provideGlance]'s content once per declared size and the launcher picks
     *  whichever fits the widget's actual placed size. */
    override val sizeMode = SizeMode.Responsive(SuggestionWidgetContent.WIDGET_SIZES.toSet())

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, id)
        if (SuggestionWidgetContent.storedState(prefs) == null) {
            SuggestionWidgetUpdater.store(context, id, SuggestionWidgetUpdater.compute(context))
        }

        // Built here (plain Context, no ambiguity between actionStartActivity's Intent-based and
        // reified-type overloads) rather than inside the composable below. The card's action
        // carries its place id via MainActivity.EXTRA_OPEN_ON_MAP_POI_ID so the tap lands on the
        // Map, centered on that place with its detail sheet open and filters cleared (#66,
        // reusing #63's "open place on Map"). A vanished-by-tap-time place is
        // MapViewModel.openPlace's problem, not this widget's — the app then just opens on the Map.
        // FLAG_ACTIVITY_SINGLE_TOP so a tap while MainActivity is already running is delivered to
        // its existing instance via onNewIntent instead of stacking a second instance on top.
        fun openAppAction(extraKey: String, extraValue: String) = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(extraKey, extraValue)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )

        provideContent {
            val state = currentState<Preferences>()
            when (val widgetState = SuggestionWidgetContent.storedState(state)) {
                // Only between a failed first compute and the next refresh — keep the chrome.
                null -> SuggestionWidgetHintBody(message = "", action = null)
                is SuggestionWidgetState.Rows -> {
                    val rows = widgetState.rows
                    if (rows.isEmpty()) {
                        SuggestionWidgetHintBody(message = "", action = null)
                    } else {
                        val index = SuggestionWidgetContent.storedIndex(state)
                        val row = rows[index]
                        SuggestionCarousel(
                            row = row,
                            index = index,
                            count = rows.size,
                            openAction = openAppAction(MainActivity.EXTRA_OPEN_ON_MAP_POI_ID, row.poiId)
                        )
                    }
                }
                is SuggestionWidgetState.Hint -> {
                    // No favorites at all -> Explore (Map, where places actually get favorited);
                    // favorites exist but none within radius -> Settings, to widen the radius.
                    val route = when (widgetState.destination) {
                        HintDestination.EXPLORE -> FreizeitDestination.MAP.route
                        HintDestination.SETTINGS -> FreizeitDestination.SETTINGS.route
                    }
                    SuggestionWidgetHintBody(
                        message = widgetState.message,
                        action = openAppAction(MainActivity.EXTRA_TARGET_DESTINATION, route)
                    )
                }
            }
        }
    }

    /** `‹ (icon) name / "distance · duration"  ⟳ ›`, plus position dots from 2 cells tall. The
     *  whole widget opens the card's place, except the arrows and refresh, which have their own
     *  click targets. A single-card deck shows no arrows or dots. */
    @Composable
    private fun SuggestionCarousel(row: SuggestionWidgetRow, index: Int, count: Int, openAction: Action) {
        SuggestionWidgetTheme {
            val tall = SuggestionWidgetContent.isTall(LocalSize.current)
            val showArrows = count > 1
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(horizontal = 4.dp, vertical = if (tall) 8.dp else 4.dp)
                    .clickable(openAction)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showArrows) {
                        ArrowButton(R.drawable.ic_widget_chevron_left, R.string.widget_previous_content_description, -1)
                    } else {
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    CategoryCircle(row.category, if (tall) 40.dp else 32.dp)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = row.name,
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontWeight = FontWeight.Medium,
                                fontSize = if (tall) 16.sp else 14.sp
                            )
                        )
                        if (row.detailLabel != null) {
                            Text(
                                text = row.detailLabel,
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = if (tall) 14.sp else 12.sp
                                )
                            )
                        }
                    }
                    Column(modifier = GlanceModifier.fillMaxHeight()) {
                        RefreshButton()
                    }
                    if (showArrows) {
                        ArrowButton(R.drawable.ic_widget_chevron_right, R.string.widget_next_content_description, 1)
                    } else {
                        Spacer(GlanceModifier.width(8.dp))
                    }
                }
                if (tall && showArrows) {
                    Text(
                        text = SuggestionWidgetContent.dots(index, count),
                        modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }

    /** The empty-state hint (#53): a single clickable message, no arrows or dots. [action] is
     *  null only for the transient "nothing stored yet" state. */
    @Composable
    private fun SuggestionWidgetHintBody(message: String, action: Action?) {
        SuggestionWidgetTheme {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    maxLines = 2,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .let { if (action != null) it.clickable(action) else it },
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
                Column(modifier = GlanceModifier.fillMaxHeight()) {
                    RefreshButton()
                }
            }
        }
    }

    @Composable
    private fun SuggestionWidgetTheme(content: @Composable () -> Unit) {
        val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            SuggestionWidgetColors
        }
        GlanceTheme(colors = colors, content = content)
    }

    /** Small top-right icon (issue #54) forcing the shared recompute-and-push routine instead of
     *  waiting for a schedule. */
    @Composable
    private fun RefreshButton() {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = LocalContext.current.getString(R.string.widget_refresh_content_description),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier
                .size(26.dp)
                .padding(4.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>())
        )
    }

    @Composable
    private fun ArrowButton(iconRes: Int, descriptionRes: Int, delta: Int) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = LocalContext.current.getString(descriptionRes),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier
                .fillMaxHeight()
                .padding(horizontal = 4.dp)
                .width(28.dp)
                .clickable(
                    actionRunCallback<CarouselStepAction>(actionParametersOf(CarouselStepAction.DELTA to delta))
                )
        )
    }

    /** Map-marker circle for [category]: two white mask layers tinted with the marker's
     *  day/night colors (see [com.example.freizeit.ui.map.CategoryCircleMasks]). */
    @Composable
    private fun CategoryCircle(category: String, diameter: Dp) {
        val context = LocalContext.current
        val masks = remember(category, diameter) {
            categoryCircleMasks(category, Density(context), diameter)
        }
        Box(modifier = GlanceModifier.size(diameter)) {
            Image(
                provider = ImageProvider(masks.disc),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MarkerBackground),
                modifier = GlanceModifier.size(diameter)
            )
            Image(
                provider = ImageProvider(masks.foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MarkerForeground),
                modifier = GlanceModifier.size(diameter)
            )
        }
    }
}
