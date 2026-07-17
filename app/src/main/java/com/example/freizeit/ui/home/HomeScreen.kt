package com.example.freizeit.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 * One favorite at a time, swipeable either direction to move to the next — both directions do
 * the same thing, so there's no drag-follow/rotation physics, just a threshold + crossfade.
 */
@Composable
private fun SwipeableSuggestionCard(
    card: Suggestion,
    customNames: Map<String, String>,
    location: LatLon?,
    onAdvance: () -> Unit,
    onGo: (Suggestion) -> Unit,
    onUnfavorite: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (abs(dragOffsetX) > thresholdPx) onAdvance()
                    dragOffsetX = 0f
                },
                onDragCancel = { dragOffsetX = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount
                }
            )
        }
    ) {
        Crossfade(targetState = card, label = "suggestion-card") { target ->
            SuggestionCard(
                suggestion = target,
                customName = customNames[target.poi.id],
                location = location,
                onGo = { onGo(target) },
                onUnfavorite = { onUnfavorite(target) }
            )
        }
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
                        tint = FavoriteRed
                    )
                }
            }

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
