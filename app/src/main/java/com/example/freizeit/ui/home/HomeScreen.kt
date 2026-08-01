package com.example.freizeit.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.opening.OpenStatus
import com.example.freizeit.domain.suggestion.Suggestion
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.ui.checkin.CheckInDateTimeFlow
import com.example.freizeit.ui.common.DurationBadge
import com.example.freizeit.ui.map.SuggestionsMiniMap
import com.example.freizeit.ui.map.displayName
import com.example.freizeit.ui.theme.FavoriteRed
import com.example.freizeit.ui.theme.WantToGoBlue
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
    var pendingCheckIn by remember { mutableStateOf<Suggestion?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeatherStrip(state.weather)

            when {
                state.isLoading -> CenteredLoading()
                !state.hasPois -> CenteredHint(stringResource(R.string.home_empty))
                !state.hasVerdictedPlaces -> CenteredHint(stringResource(R.string.home_no_favorites))
                !state.hasVerdictedPlacesWithinRadius -> CenteredHint(
                    stringResource(R.string.home_no_suggestions_within_radius, state.radiusKm)
                )
                else -> SwipeableSuggestionCard(
                    deck = state.deck,
                    customNames = state.customNames,
                    location = state.location,
                    onCheckIn = { suggestion -> pendingCheckIn = suggestion },
                    onRemoveVerdict = { suggestion -> viewModel.setVerdict(suggestion.poi, null) },
                    modifier = Modifier.fillMaxWidth()
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
}

/** Drag distance (in dp) past which a horizontal drag counts as a swipe. */
private const val SWIPE_THRESHOLD_DP = 96

/** Duration of the departing card's fade-out, run to completion before the next/previous card
 *  starts fading in — sequential, not a crossfade, so only one card is ever visible at a time. */
private const val FADE_OUT_MILLIS = 150

/** Duration of the incoming card's fade-in (and its slide-in, run concurrently with it). */
private const val FADE_IN_MILLIS = 200

/** How far (in dp) the incoming card slides from, easing to its resting position as it fades in —
 *  a small motion cue alongside the fade, continuing in the swipe's direction. */
private const val ENTER_SLIDE_DP = 24

/** True once a drag has gone far enough to count as a completed swipe. */
internal fun isPastSwipeThreshold(offsetPx: Float, thresholdPx: Float): Boolean =
    abs(offsetPx) > thresholdPx

/**
 * One favorite or want-to-go place at a time — no peek of the next/previous card at rest or
 * during a drag (explicit #43 decision replacing the old stacked-deck look). Dragging still
 * translates the displayed card with the finger; releasing past the threshold fades it out in
 * place, then fades the next/previous card in (with a slight slide from the swipe direction).
 * Releasing short of the threshold springs the drag back to rest. Removing a card's verdict runs
 * the same fade-out/fade-in sequence (always toward "next", since a button tap has no drag
 * direction to inherit) before applying the actual verdict change.
 *
 * Position in [deck] is tracked locally ([localIndex]) rather than round-tripped through the
 * ViewModel: a swipe-driven page needs the very next frame to show the promoted card at rest,
 * and a Room/combine round trip (even a fast one) lands a beat too late. Removing a verdict still
 * goes through the ViewModel since it has to persist, and — same as before this session —
 * [localIndex] is deliberately left untouched for that path: once the deck shrinks (removing the
 * entry at [localIndex]), everything after it left-shifts by one, so [localIndex] already points
 * at the right next entry with no bump needed.
 *
 * [displayedId] tracks which POI is the big/visible card independently of [card] (which always
 * reflects the *persisted* [localIndex]/[deck]): a swipe bumps [localIndex] synchronously, so the
 * two stay in lockstep, but a verdict removal's [onRemoveVerdict] round trip is async — [deck]
 * won't actually shrink (and [card] won't catch up) until Room re-emits, possibly several frames
 * after the fade-in has already started. Fading in the entry captured as [commitTo]'s `target` up
 * front, keyed on id rather than re-derived from [localIndex] each frame, means the fade-in never
 * has to wait for that round trip — [card] simply catches up to already-correct [displayedId]
 * once the deck does shrink, with no visible jump.
 *
 * Previous/current/next cards are each key()'d by POI id rather than pinned to a fixed call site,
 * so a card's SuggestionsMiniMap (and its MapView) travels with its POI across commits instead of
 * being handed new suggestion data to render from scratch — the latter was the source of a
 * post-commit flicker (the reused MapView briefly still showing its previous POI's tiles).
 */
@Composable
private fun SwipeableSuggestionCard(
    deck: List<Suggestion>,
    customNames: Map<String, String>,
    location: LatLon?,
    onCheckIn: (Suggestion) -> Unit,
    onRemoveVerdict: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val enterSlidePx = with(density) { ENTER_SLIDE_DP.dp.toPx() }
    val scope = rememberCoroutineScope()

    var localIndex by rememberSaveable { mutableStateOf(0) }
    val dragOffsetPx = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(1f) }
    val enterOffsetPx = remember { Animatable(0f) }
    var isCommitting by remember { mutableStateOf(false) }

    val card = deck[localIndex.mod(deck.size)]
    val nextCard = if (deck.size <= 1) null else deck[(localIndex + 1).mod(deck.size)]
    val previousCard = if (deck.size <= 1) null else deck[(localIndex - 1).mod(deck.size)]
    var displayedId by remember { mutableStateOf(card.poi.id) }
    // All three roles must come from this one loop (one call site) rather than the current card
    // being rendered from a separate key() elsewhere: Compose only preserves a composable's
    // state (and here, its MapView) across recompositions when the same key recurs at the same
    // call site. Splitting current out into its own call site meant a card promoted from
    // neighbor to current was actually torn down and rebuilt from scratch — losing the very
    // continuity this keying was for. A 2-card deck's "previous" and "next" are the same POI;
    // distinctBy dedupes that to a single neighbor entry.
    val window = (listOfNotNull(previousCard, nextCard) + card).distinctBy { it.poi.id }

    fun commitTo(target: Suggestion, direction: Int, onCommitted: () -> Unit) {
        if (isCommitting) return
        isCommitting = true
        scope.launch {
            cardAlpha.animateTo(0f, tween(FADE_OUT_MILLIS))
            onCommitted()
            dragOffsetPx.snapTo(0f)
            displayedId = target.poi.id
            cardAlpha.snapTo(0f)
            enterOffsetPx.snapTo(direction * enterSlidePx)
            launch { enterOffsetPx.animateTo(0f, tween(FADE_IN_MILLIS)) }
            cardAlpha.animateTo(1f, tween(FADE_IN_MILLIS))
            isCommitting = false
        }
    }

    Box(
        // The deck's own size tracks the current card's natural (wrap-content) size, animating
        // to a new target whenever a swipe changes which card that is. Neighbor cards below are
        // matchParentSize()'d so they always conform to this same (possibly still-animating)
        // size instead of contributing to it — that's what stops a taller neighbor from bleeding
        // past the current card's bounds.
        modifier = modifier
            .animateContentSize(tween(FADE_OUT_MILLIS + FADE_IN_MILLIS))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val direction = sign(dragOffsetPx.value).toInt()
                        val target = if (direction < 0) previousCard else nextCard
                        if (target != null && isPastSwipeThreshold(dragOffsetPx.value, thresholdPx)) {
                            commitTo(target, direction) { localIndex += direction }
                        } else {
                            scope.launch { dragOffsetPx.animateTo(0f, animationSpec = spring()) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetPx.animateTo(0f, animationSpec = spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isCommitting) return@detectHorizontalDragGestures
                        change.consume()
                        scope.launch { dragOffsetPx.snapTo(dragOffsetPx.value + dragAmount) }
                    }
                )
            }
    ) {
        for (entry in window) {
            key(entry.poi.id) {
                val isDisplayed = entry.poi.id == displayedId
                // A single SuggestionCard call site regardless of role: role-dependent behavior
                // is expressed as plain values (modifier, callbacks) fed into that one call,
                // never as which composable call executes — an if/else choosing between two
                // separate SuggestionCard(...) call expressions would itself defeat the keying,
                // for the same reason splitting current/neighbor into separate loops did.
                SuggestionCard(
                    suggestion = entry,
                    customName = customNames[entry.poi.id],
                    location = location,
                    onCheckIn = if (isDisplayed) ({ onCheckIn(entry) }) else ({}),
                    onRemoveVerdict = if (isDisplayed) {
                        {
                            val target = nextCard
                            if (target != null) {
                                commitTo(target, direction = 1) { onRemoveVerdict(entry) }
                            } else {
                                onRemoveVerdict(entry)
                            }
                        }
                    } else {
                        {}
                    },
                    modifier = if (isDisplayed) {
                        Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                            .graphicsLayer {
                                translationX = dragOffsetPx.value + enterOffsetPx.value
                                alpha = cardAlpha.value
                            }
                    } else {
                        Modifier
                            .matchParentSize()
                            .zIndex(0f)
                            .graphicsLayer { alpha = 0f }
                    }
                )
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
    // Sizing is entirely up to the caller: the current card wraps its own content (driving the
    // deck's animateContentSize), neighbor cards are matchParentSize()'d to whatever that
    // resolves to, so a neighbor's extra content scrolls within its clipped bounds rather than
    // poking out past the current card's edge.
    Card(modifier = modifier) {
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
                val isFavorite = suggestion.verdictValue == Verdict.VALUE_FAVORITE
                IconButton(onClick = onRemoveVerdict) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.Bookmark,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.home_unfavorite else R.string.home_remove_want_to_go
                        ),
                        tint = if (isFavorite) FavoriteRed else WantToGoBlue,
                        // IconButton centers the glyph in its 48dp touch target; nudge it up so
                        // it reads as flush with the name's top edge instead of vertically centered,
                        // and right so it reads as flush with the card's content edge instead of
                        // sitting visibly inset from it.
                        modifier = Modifier.offset(x = HEART_ICON_END_NUDGE_DP.dp, y = -HEART_ICON_TOP_NUDGE_DP.dp)
                    )
                }
            }

            suggestion.distanceMeters?.let { distanceMeters ->
                DurationBadge(distanceMeters)
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
