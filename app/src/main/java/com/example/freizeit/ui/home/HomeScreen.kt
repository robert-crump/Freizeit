package com.example.freizeit.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.suggestion.Suggestion
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.ui.checkin.CheckInDateTimeFlow
import com.example.freizeit.ui.common.DurationBadge
import com.example.freizeit.ui.map.PlaceDetailSheet
import com.example.freizeit.ui.map.SuggestionsMiniMap
import com.example.freizeit.ui.map.displayName
import com.example.freizeit.ui.theme.FavoriteRed
import com.example.freizeit.ui.theme.WantToGoBlue
import com.example.freizeit.util.LatLon
import com.example.freizeit.util.LocationHelper
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    /** A POI id to auto-open in the detail sheet on launch (#50) — set from MainActivity's
     *  intent extra (widget taps go to the Map instead since #66). Consumed once via [onTargetPoiIdHandled]
     *  so it doesn't reopen on a later recomposition/resume or a return trip to this tab. */
    targetPoiId: String? = null,
    onTargetPoiIdHandled: () -> Unit = {},
    /** The detail sheet's Check-in button (#64): hands the place to FreizeitApp's app-wide
     *  check-in flow, which calls `onCheckedIn` (closing the sheet) once the visit is saved and
     *  then lands on the check-in history. */
    onSheetCheckIn: (poi: Poi, placeName: String, onCheckedIn: () -> Unit) -> Unit = { _, _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val targetPoi by viewModel.targetPoi.collectAsStateWithLifecycle()
    val targetPoiLastVisit by viewModel.targetPoiLastVisit.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCheckIn by remember { mutableStateOf<Suggestion?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(targetPoiId) {
        if (targetPoiId != null) {
            viewModel.openTargetPoi(targetPoiId)
            onTargetPoiIdHandled()
        }
    }

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

    // Refreshes location whenever Home comes back to the foreground (e.g. app reopened after
    // minimizing at a different location), so the deck re-filters/re-ranks against where the
    // user actually is and the mini-map re-centers. Nothing polls location continuously while
    // foregrounded, so the deck won't shuffle mid-swipe within a single session (#34).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // No horizontal padding here: the pager spans the full width so neighbor cards peek in
        // at the screen edges; everything else brings its own 16dp gutter.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeatherStrip(state.weather, modifier = Modifier.padding(horizontal = 16.dp))

            when {
                state.isLoading -> CenteredLoading()
                !state.hasPois -> CenteredHint(stringResource(R.string.home_empty))
                !state.hasVerdictedPlaces -> CenteredHint(stringResource(R.string.home_no_favorites))
                !state.hasVerdictedPlacesWithinRadius -> CenteredHint(
                    stringResource(R.string.home_no_suggestions_within_radius, state.radiusKm)
                )
                else -> SuggestionPager(
                    deck = state.deck,
                    customNames = state.customNames,
                    location = state.location,
                    onCheckIn = { suggestion -> pendingCheckIn = suggestion },
                    onRemoveVerdict = { suggestion -> viewModel.setVerdict(suggestion.poi, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    CheckInDateTimeFlow(
        pendingPoi = pendingCheckIn?.poi,
        placeName = pendingCheckIn?.let { it.poi.displayName(state.customNames[it.poi.id]) } ?: "",
        snackbarHostState = snackbarHostState,
        onDismiss = { pendingCheckIn = null },
        onConfirmed = { poi, visitedAt -> viewModel.checkIn(poi, visitedAt) },
        onUndo = { visitId -> viewModel.undoCheckIn(visitId) }
    )

    // Deep-link target (#50): reuses the same sheet Map opens from markers/rows, so a place
    // opened here already has full verdict/rename support, not a stripped-down preview.
    targetPoi?.let { item ->
        val placeName = item.poi.displayName(state.customNames[item.poi.id])
        PlaceDetailSheet(
            item = item,
            verdict = state.verdicts[item.poi.id]?.value,
            onVerdictChange = { viewModel.setVerdict(item.poi, it) },
            customName = state.customNames[item.poi.id],
            onCustomNameChange = { viewModel.setCustomName(item.poi.id, it) },
            lastVisit = targetPoiLastVisit,
            onCheckIn = {
                onSheetCheckIn(item.poi, placeName) { viewModel.dismissTargetPoi() }
            },
            onDismiss = { viewModel.dismissTargetPoi() }
        )
    }
}

/** How much of each neighbor card is visible at the screen edge (MyQuotes' `pager_peek`). */
private val PAGER_PEEK = 24.dp

/** The gap between two cards (MyQuotes' `pager_page_margin`). */
private val PAGER_PAGE_MARGIN = 8.dp

/** Scale and alpha of a neighbor card one full page away from the center. */
private const val NEIGHBOR_MIN_SCALE = 0.92f
private const val NEIGHBOR_MIN_ALPHA = 0.6f

/** Keeps the pager on the same place across deck changes; a plain field (not snapshot state)
 *  since it's only bookkeeping, read and written during composition. */
private class DeckAnchor {
    var ids: List<String>? = null
}

/**
 * The favorite and want-to-go places as a looping, peeking pager (#65, a close port of MyQuotes'
 * `PagerPeek` + [LoopingPositions]): the previous and next cards peek in at the edges, scaled
 * down and faded by their distance from the center, and swiping loops endlessly both ways. Each
 * page is its own vertical scroll holding the card at its natural height, top-aligned.
 *
 * Deck changes (a location refresh re-ranking it, a removed verdict shrinking it) keep the pager
 * on the same POI via [PagerState.requestScrollToPage] from the same composition, so the next
 * frame already shows it — never a frame of whatever card the old page number now maps to.
 * Removing a verdict first slides to the next card, so the removed one leaves the center before
 * the deck shrinks under it. Page keys are "lap:id" and a re-anchor stays in the same lap number,
 * so the card on screen (and its mini-map's MapView) keeps its composition across a shrink.
 */
@Composable
private fun SuggestionPager(
    deck: List<Suggestion>,
    customNames: Map<String, String>,
    location: LatLon?,
    onCheckIn: (Suggestion) -> Unit,
    onRemoveVerdict: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val size = deck.size
    val pagerState = rememberPagerState(initialPage = LoopingPositions.pagerPositionOf(0, size, 0)) {
        LoopingPositions.count(size)
    }

    // The POI on the current page, saved so the deck reopens where it was left.
    var currentId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    val anchor = remember { DeckAnchor() }
    val ids = deck.map { it.poi.id }
    if (ids != anchor.ids) {
        val oldSize = anchor.ids?.size ?: size
        anchor.ids = ids
        // A removed POI falls through to the one that followed it, now at its index.
        val index = ids.indexOf(currentId).takeIf { it >= 0 } ?: currentIndex.coerceIn(0, size - 1)
        val lapStart = if (oldSize <= 1) 0 else pagerState.currentPage / oldSize * size
        val target = LoopingPositions.pagerPositionOf(index, size, lapStart)
        if (target != pagerState.currentPage) pagerState.requestScrollToPage(target)
    }
    val currentDeck by rememberUpdatedState(deck)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val index = LoopingPositions.indexOf(page, currentDeck.size)
            currentDeck.getOrNull(index)?.let {
                currentId = it.poi.id
                currentIndex = index
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        // Neighbors sit one page spacing further out, so pad by peek + margin.
        contentPadding = PaddingValues(horizontal = PAGER_PEEK + PAGER_PAGE_MARGIN),
        pageSpacing = PAGER_PAGE_MARGIN,
        beyondViewportPageCount = 1,
        verticalAlignment = Alignment.Top,
        key = { page ->
            val lap = if (size <= 1) 0 else page / size
            "$lap:${deck[LoopingPositions.indexOf(page, size)].poi.id}"
        }
    ) { page ->
        val entry = deck[LoopingPositions.indexOf(page, size)]
        val isCurrent = page == pagerState.currentPage
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Where this page sits relative to the center: 0 centered, ±1 a neighbor.
                    val position = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                    val distance = min(1f, abs(position))
                    val onRight = (position > 0) != (layoutDirection == LayoutDirection.Rtl)
                    // Pivot on the edge facing the center card, so the visible peek stays the same width.
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (position == 0f) 0.5f else if (onRight) 0f else 1f,
                        pivotFractionY = 0.5f
                    )
                    val scale = 1f - (1f - NEIGHBOR_MIN_SCALE) * distance
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (1f - NEIGHBOR_MIN_ALPHA) * distance
                }
                .verticalScroll(rememberScrollState())
        ) {
            SuggestionCard(
                suggestion = entry,
                customName = customNames[entry.poi.id],
                location = location,
                // A peeking neighbor's partly visible buttons do nothing; swipe it in first.
                onCheckIn = { if (isCurrent) onCheckIn(entry) },
                onRemoveVerdict = remove@{
                    if (!isCurrent || pagerState.isScrollInProgress) return@remove
                    if (size <= 1) {
                        onRemoveVerdict(entry)
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            onRemoveVerdict(entry)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
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

/** Gap between the POI name and the duration badge below it — tighter than the 12dp rhythm used
 *  between the card's other, less related sections. */
private const val NAME_TO_DURATION_GAP_DP = 4

/** How far to nudge the heart glyph up inside its 48dp touch target to align with the name's top. */
private const val HEART_ICON_TOP_NUDGE_DP = 10

/** IconButton's 48dp touch target leaves ~12dp of padding around the 24dp glyph on each side;
 *  nudge right by that so the glyph's edge lands flush with the card's own 20dp content edge
 *  instead of sitting visibly inset from it. */
private const val HEART_ICON_END_NUDGE_DP = 12

/**
 * Self-contained swipeable unit (issue #17): name, a single-POI mini-map (current location vs.
 * this favorite), opening hours if known, and the Check-in/unfavorite actions all travel together.
 */
@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    customName: String?,
    location: LatLon?,
    onCheckIn: () -> Unit,
    onRemoveVerdict: () -> Unit,
    modifier: Modifier = Modifier
) {
    val poi = suggestion.poi
    // Natural (wrap-content) height; the pager page around it scrolls a card taller than the screen.
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Grouped in their own Column so the name-to-duration gap can be set tighter than
            // the 12dp rhythm the outer Column uses between its other, less related, children.
            Column(verticalArrangement = Arrangement.spacedBy(NAME_TO_DURATION_GAP_DP.dp)) {
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
                    val isFavorite = suggestion.verdictValue == Verdict.VALUE_FAVORITE
                    IconButton(
                        onClick = onRemoveVerdict,
                        // Nudging the glyph itself (rather than the whole button) pushed it past the
                        // edge of IconButton's own clipped state-layer, silently cropping it. Offsetting
                        // the button instead carries that clip bounds along with it, so the glyph
                        // reads as flush with the name's top edge / the card's content edge while
                        // staying fully inside its own hit/clip region.
                        modifier = Modifier.offset(x = HEART_ICON_END_NUDGE_DP.dp, y = -HEART_ICON_TOP_NUDGE_DP.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.Bookmark,
                            contentDescription = stringResource(
                                if (isFavorite) R.string.home_unfavorite else R.string.home_remove_want_to_go
                            ),
                            tint = if (isFavorite) FavoriteRed else WantToGoBlue
                        )
                    }
                }

                suggestion.distanceMeters?.let { distanceMeters ->
                    DurationBadge(distanceMeters)
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
                        // Closed is already called out by the "Warning: Currently closed" line
                        // below (suggestion.warnings) — showing it here too was redundant.
                        OpenStatus.CLOSED, OpenStatus.UNKNOWN -> {}
                    }
                }
            }

            if (suggestion.warnings.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    suggestion.warnings.forEach { warning ->
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
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

            suggestion.lastVisit?.let {
                Text(
                    text = stringResource(R.string.detail_last_visit, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onCheckIn, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_checkin))
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
            .padding(horizontal = 16.dp, vertical = 48.dp)
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
