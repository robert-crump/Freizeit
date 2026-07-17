package com.example.freizeit.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.entity.PendingVisit
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.suggestion.Suggestion
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.explore.CategoryDot
import com.example.freizeit.ui.explore.SuggestionsMiniMap
import com.example.freizeit.ui.explore.displayName
import com.example.freizeit.ui.theme.FavoriteRed
import com.example.freizeit.util.LatLon
import com.example.freizeit.util.LocationHelper
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshLocation() }

    LaunchedEffect(Unit) {
        if (!LocationHelper.hasPermission(context)) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.pendingVisit?.let { visit ->
            VisitBanner(
                visit = visit,
                onVerdict = { viewModel.resolveVisit(it) },
                onDidNotGo = { viewModel.resolveVisit(null) }
            )
        }

        WeatherStrip(state.weather)

        val currentCard = state.currentCard
        when {
            state.isLoading -> CenteredLoading()
            !state.hasPois -> CenteredHint(stringResource(R.string.home_empty))
            !state.hasFavorites -> CenteredHint(stringResource(R.string.home_no_favorites))
            currentCard == null -> CenteredHint(stringResource(R.string.home_no_suggestions))
            else -> SwipeableSuggestionCard(
                card = currentCard,
                nextCard = state.nextCard,
                customNames = state.customNames,
                location = state.location,
                onAdvance = viewModel::advance,
                onGo = { suggestion ->
                    val poi = suggestion.poi
                    viewModel.recordGo(poi, state.customNames[poi.id])
                    val label = Uri.encode(state.customNames[poi.id] ?: poi.name ?: poi.category)
                    val navUri = Uri.parse("geo:${poi.lat},${poi.lon}?q=${poi.lat},${poi.lon}($label)")
                    context.startActivity(Intent(Intent.ACTION_VIEW, navUri))
                },
                onUnfavorite = { suggestion -> viewModel.setVerdict(suggestion.poi, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/** Drag distance (in dp) past which a horizontal drag counts as a swipe. */
private const val SWIPE_THRESHOLD_DP = 96

/**
 * How much further than the swipe threshold a drag has to travel before the top card is fully
 * transparent — so a card that's just crossed the threshold isn't instantly invisible.
 */
private const val FADE_DISTANCE_MULTIPLIER = 1.75f

/** Scale and vertical offset of the peeking card at rest (no drag in progress). */
private const val PEEK_REST_SCALE = 0.95f
private const val PEEK_REST_OFFSET_DP = 12

/** Duration of the commit animation (card finishes leaving, peek card finishes revealing). */
private const val COMMIT_ANIMATION_MILLIS = 250

/** Alpha of the top (dragged) card at [offsetPx] — 1 at rest, 0 once past the fade distance. */
internal fun topCardAlpha(offsetPx: Float, thresholdPx: Float): Float {
    val fadeDistancePx = thresholdPx * FADE_DISTANCE_MULTIPLIER
    if (fadeDistancePx <= 0f) return 0f
    return (1f - abs(offsetPx) / fadeDistancePx).coerceIn(0f, 1f)
}

/** How far the peek card has grown toward full size, 0..1, tied to the same fade distance. */
internal fun revealProgress(offsetPx: Float, thresholdPx: Float): Float =
    1f - topCardAlpha(offsetPx, thresholdPx)

/** True once a drag has gone far enough to count as a completed swipe. */
internal fun isPastSwipeThreshold(offsetPx: Float, thresholdPx: Float): Boolean =
    abs(offsetPx) > thresholdPx

/**
 * One favorite at a time, backed by a peeking card behind it so the deck reads as stackable.
 * Dragging translates and fades the top card while the peek card grows into place; releasing
 * past the threshold commits that motion to completion and advances the deck, releasing short
 * of it springs back to rest. Unfavoriting runs the same commit animation (sliding right, since
 * a button tap has no drag direction to inherit) before applying the actual verdict change.
 */
@Composable
private fun SwipeableSuggestionCard(
    card: Suggestion,
    nextCard: Suggestion?,
    customNames: Map<String, String>,
    location: LatLon?,
    onAdvance: () -> Unit,
    onGo: (Suggestion) -> Unit,
    onUnfavorite: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val peekOffsetPx = with(density) { PEEK_REST_OFFSET_DP.dp.toPx() }
    val scope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    var isCommitting by remember { mutableStateOf(false) }
    val currentOnAdvance by rememberUpdatedState(onAdvance)

    fun commit(direction: Float, onCommitted: () -> Unit) {
        if (isCommitting) return
        isCommitting = true
        scope.launch {
            offsetX.animateTo(
                targetValue = direction * thresholdPx * FADE_DISTANCE_MULTIPLIER,
                animationSpec = tween(COMMIT_ANIMATION_MILLIS)
            )
            onCommitted()
            offsetX.snapTo(0f)
            isCommitting = false
        }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val current = offsetX.value
                    if (isPastSwipeThreshold(current, thresholdPx)) {
                        commit(sign(current)) { currentOnAdvance() }
                    } else {
                        scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                    }
                },
                onDragCancel = {
                    scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (isCommitting) return@detectHorizontalDragGestures
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                }
            )
        }
    ) {
        if (nextCard != null) {
            val progress = revealProgress(offsetX.value, thresholdPx)
            SuggestionCard(
                suggestion = nextCard,
                customName = customNames[nextCard.poi.id],
                location = location,
                onGo = {},
                onUnfavorite = {},
                modifier = Modifier.graphicsLayer {
                    scaleX = lerp(PEEK_REST_SCALE, 1f, progress)
                    scaleY = lerp(PEEK_REST_SCALE, 1f, progress)
                    translationY = lerp(peekOffsetPx, 0f, progress)
                }
            )
        }

        SuggestionCard(
            suggestion = card,
            customName = customNames[card.poi.id],
            location = location,
            onGo = { onGo(card) },
            onUnfavorite = {
                commit(direction = 1f) { onUnfavorite(card) }
            },
            modifier = Modifier.graphicsLayer {
                translationX = offsetX.value
                alpha = topCardAlpha(offsetX.value, thresholdPx)
            }
        )
    }
}

@Composable
private fun VisitBanner(
    visit: PendingVisit,
    onVerdict: (String) -> Unit,
    onDidNotGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.home_visit_prompt,
                    visit.snapshotName ?: categoryDisplayName(visit.snapshotCategory)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { onVerdict(Verdict.VALUE_FAVORITE) }) { Text("❤️") }
                TextButton(onClick = onDidNotGo) { Text(stringResource(R.string.home_visit_didnt_go)) }
            }
        }
    }
}

@Composable
private fun WeatherStrip(weather: WeatherSnapshot?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (weather == null) {
                Text(
                    text = stringResource(R.string.home_weather_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = WeatherSnapshot.emojiForCode(weather.currentWeatherCode, weather.isDay),
                    style = MaterialTheme.typography.headlineMedium
                )
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(
                                R.string.home_weather_temp,
                                weather.currentTempC.roundToInt()
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = WeatherSnapshot.describeCode(weather.currentWeatherCode),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = weather.outlook(LocalDateTime.now()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** How far to nudge the heart glyph up inside its 48dp touch target to align with the name's top. */
private const val HEART_ICON_TOP_NUDGE_DP = 10

/**
 * Self-contained swipeable unit (issue #17): name, a single-POI mini-map (current location vs.
 * this favorite), opening hours if known, and the Go/unfavorite actions all travel together.
 */
@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    customName: String?,
    location: LatLon?,
    onGo: () -> Unit,
    onUnfavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val poi = suggestion.poi
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = poi.displayName(customName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onUnfavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.home_unfavorite),
                        tint = FavoriteRed,
                        // IconButton centers the glyph in its 48dp touch target; nudge it up so
                        // it reads as flush with the name's top edge instead of vertically centered.
                        modifier = Modifier.offset(y = -HEART_ICON_TOP_NUDGE_DP.dp)
                    )
                }
            }

            SuggestionsMiniMap(
                pois = listOf(poi),
                selectedPoiId = poi.id,
                location = location,
                onPoiClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryDot(poi.category)
                Text(
                    text = categoryDisplayName(poi.category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            poi.openingHours?.let { hours ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_opening_hours, hours),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    when (suggestion.openStatus) {
                        OpenStatus.OPEN -> OpenStatusBadge(
                            text = stringResource(R.string.home_open_now),
                            color = MaterialTheme.colorScheme.primary
                        )
                        OpenStatus.CLOSED -> OpenStatusBadge(
                            text = stringResource(R.string.home_closed_now),
                            color = MaterialTheme.colorScheme.error
                        )
                        OpenStatus.UNKNOWN -> {}
                    }
                }
            }

            if (suggestion.reasons.isNotEmpty()) {
                Text(
                    text = suggestion.reasons.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onGo, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_go))
            }
        }
    }
}

@Composable
private fun OpenStatusBadge(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun CenteredHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CenteredLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
