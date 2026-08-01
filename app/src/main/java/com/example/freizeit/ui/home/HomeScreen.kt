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
import androidx.compose.ui.util.lerp
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

/**
 * How much further than the swipe threshold a drag has to travel before the peek card finishes
 * growing in — so a card that's just crossed the threshold isn't instantly at full size.
 */
private const val REVEAL_DISTANCE_MULTIPLIER = 1.75f

/** Scale and vertical offset of the peeking card at rest (no drag in progress). */
private const val PEEK_REST_SCALE = 0.95f
private const val PEEK_REST_OFFSET_DP = 12

/** Duration of the commit animation (card finishes leaving, peek card finishes revealing). */
private const val COMMIT_ANIMATION_MILLIS = 250

/** How far [offsetPx] has traveled toward the swipe threshold, 0 at rest, 1 once reached. */
private fun thresholdProgress(offsetPx: Float, thresholdPx: Float): Float {
    if (thresholdPx <= 0f) return 1f
    return (abs(offsetPx) / thresholdPx).coerceIn(0f, 1f)
}

/** How far [offsetPx] has traveled toward the (further) reveal distance, 0 at rest, 1 once past it. */
private fun dragProgress(offsetPx: Float, thresholdPx: Float): Float {
    val revealDistancePx = thresholdPx * REVEAL_DISTANCE_MULTIPLIER
    if (revealDistancePx <= 0f) return 1f
    return (abs(offsetPx) / revealDistancePx).coerceIn(0f, 1f)
}

/**
 * Alpha of the top (dragged) card at [offsetPx] — 1 at rest, fully transparent by the swipe
 * threshold. Reaching 0 right at the threshold (rather than a floor short of it) means the old
 * card is already invisible for the whole commit animation that follows a released swipe, not
 * just faded — masking any one-frame lag in the map underneath catching up to its new role.
 */
internal fun topCardAlpha(offsetPx: Float, thresholdPx: Float): Float =
    1f - thresholdProgress(offsetPx, thresholdPx)

/** How far the peek card has grown toward full size, 0..1 — reaches 1 when the top card bottoms out. */
internal fun revealProgress(offsetPx: Float, thresholdPx: Float): Float =
    dragProgress(offsetPx, thresholdPx)

/** True once a drag has gone far enough to count as a completed swipe. */
internal fun isPastSwipeThreshold(offsetPx: Float, thresholdPx: Float): Boolean =
    abs(offsetPx) > thresholdPx

/** True if the given neighbor is the one the current drag is actively growing into view. */
internal fun isRevealTarget(offsetPx: Float, isPrevious: Boolean, isNext: Boolean): Boolean =
    (offsetPx < 0f && isPrevious) || (offsetPx >= 0f && isNext)

/** Highest z-index (2) for the current card so it's never occluded by a growing neighbor;
 *  the neighbor the drag is actively revealing (1) so it isn't occluded by the idle one's fixed
 *  peek footprint as it grows past it; the idle neighbor (0) last, unseen behind both.
 */
internal fun revealZIndex(isCurrent: Boolean, isRevealTarget: Boolean): Float = when {
    isCurrent -> 2f
    isRevealTarget -> 1f
    else -> 0f
}

/**
 * One favorite or want-to-go place at a time, backed by a peeking card behind it so the deck
 * reads as stackable. Dragging translates and fades the top card while the peek card grows into
 * place; releasing past the threshold commits that motion to completion and pages the deck,
 * releasing short of it springs back to rest. Removing a card's verdict runs the same commit
 * animation (sliding right, since a button tap has no drag direction to inherit) before applying
 * the actual verdict change.
 *
 * Position in [deck] is tracked locally ([localIndex]) rather than round-tripped through the
 * ViewModel: a swipe-driven page needs the very next frame to show the promoted card at rest,
 * and a Room/combine round trip (even a fast one) lands a beat too late, which reads as the
 * peeking card flicking to full size and back. Removing a verdict still goes through the
 * ViewModel since it has to persist, but leaving [localIndex] untouched lets the shrunken deck
 * naturally surface whatever was already peeking through.
 *
 * Previous/current/next cards are each key()'d by POI id rather than pinned to a fixed
 * top/peek call site. A card's SuggestionsMiniMap (and its MapView) travels with its POI across
 * commits, so paging the deck just re-transforms the same already-rendered composables instead
 * of handing a call site new suggestion data to render from scratch — the latter was the source
 * of a post-commit flicker: the (reused) top-slot MapView briefly still showed its previous
 * POI's tiles until its LaunchedEffect-driven render caught up.
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
    val peekOffsetPx = with(density) { PEEK_REST_OFFSET_DP.dp.toPx() }
    val scope = rememberCoroutineScope()

    var localIndex by rememberSaveable { mutableStateOf(0) }
    val offsetX = remember { Animatable(0f) }
    // offsetX's last-committed rest position. offsetX itself is never reset to 0 on commit —
    // doing so would need a second state write racing the localIndex bump below, and a
    // recomposition caught between the two (new index paired with the old, not-yet-reset offset)
    // renders the wrong card at the wrong transform for a frame — e.g. the just-swiped-away card
    // flashing back at full peek size. Bumping localIndex and capturing baseOffset happen back to
    // back with no suspension point between them, so Compose can never observe one without the
    // other.
    var baseOffset by remember { mutableStateOf(0f) }
    var isCommitting by remember { mutableStateOf(false) }

    val effectiveOffset = offsetX.value - baseOffset

    val card = deck[localIndex.mod(deck.size)]
    val nextCard = if (deck.size <= 1) null else deck[(localIndex + 1).mod(deck.size)]
    val previousCard = if (deck.size <= 1) null else deck[(localIndex - 1).mod(deck.size)]
    // One keyed window covering every role (previous/current/next), current last so it draws on
    // top *at rest*. A 2-card deck's "previous" and "next" are the same POI, and distinctBy
    // keeps the first occurrence — so it's deduped to a single neighbor entry that reveals
    // either way. This emission order is what makes nextCard's idle peek sliver show (rather
    // than previousCard's identical one) as the deck's "stackable" affordance at rest — but kept
    // static during an active drag, it also means nextCard's idle peek would sit on top of
    // previousCard while THAT'S the one being dragged into view, since nextCard is emitted after
    // it. isRevealTarget/revealZIndex below fix that per-frame without touching this order (and
    // so without disturbing the key-based state reuse this order was chosen for): the actively
    // revealed neighbor always paints above the idle one, current always above both.
    // All three roles must come from this one loop (one call site) rather than the current card
    // being rendered from a separate key() elsewhere: Compose only preserves a composable's
    // state (and here, its MapView) across recompositions when the same key recurs at the same
    // call site. Splitting current out into its own call site meant a card promoted from
    // neighbor to current was actually torn down and rebuilt from scratch — losing the very
    // continuity this keying was for.
    val window = (listOfNotNull(previousCard, nextCard) + card).distinctBy { it.poi.id }

    fun commit(direction: Int, onCommitted: () -> Unit) {
        if (isCommitting) return
        isCommitting = true
        scope.launch {
            offsetX.animateTo(
                targetValue = baseOffset + direction * thresholdPx * REVEAL_DISTANCE_MULTIPLIER,
                animationSpec = tween(COMMIT_ANIMATION_MILLIS)
            )
            onCommitted()
            baseOffset = offsetX.value
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
            .animateContentSize(tween(COMMIT_ANIMATION_MILLIS))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val current = offsetX.value - baseOffset
                        if (isPastSwipeThreshold(current, thresholdPx)) {
                            val direction = sign(current).toInt()
                            commit(direction) { localIndex += direction }
                        } else {
                            scope.launch { offsetX.animateTo(baseOffset, animationSpec = spring()) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(baseOffset, animationSpec = spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isCommitting) return@detectHorizontalDragGestures
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                )
            }
    ) {
        for (entry in window) {
            key(entry.poi.id) {
                val isCurrent = entry.poi.id == card.poi.id
                // Dragging left reveals the previous card (moving "up" the stack); dragging
                // right, or at rest, reveals the next one (moving "down") — a 2-card deck's
                // single deduped neighbor is both, so it reveals either way.
                val isPrevious = entry.poi.id == previousCard?.poi?.id
                val isNext = entry.poi.id == nextCard?.poi?.id
                val isRevealTarget = isRevealTarget(effectiveOffset, isPrevious, isNext)
                val progress = if (isRevealTarget) revealProgress(effectiveOffset, thresholdPx) else 0f
                // A single SuggestionCard call site regardless of role: role-dependent behavior
                // is expressed as plain values (modifier, callbacks) fed into that one call,
                // never as which composable call executes — an if/else choosing between two
                // separate SuggestionCard(...) call expressions would itself defeat the keying,
                // for the same reason splitting current/neighbor into separate loops did.
                SuggestionCard(
                    suggestion = entry,
                    customName = customNames[entry.poi.id],
                    location = location,
                    onCheckIn = if (isCurrent) ({ onCheckIn(entry) }) else ({}),
                    onRemoveVerdict = if (isCurrent) {
                        { commit(direction = 1) { onRemoveVerdict(entry) } }
                    } else {
                        {}
                    },
                    modifier = if (isCurrent) {
                        Modifier
                            .fillMaxWidth()
                            .zIndex(revealZIndex(isCurrent = true, isRevealTarget = isRevealTarget))
                            .graphicsLayer {
                                translationX = effectiveOffset
                                alpha = topCardAlpha(effectiveOffset, thresholdPx)
                            }
                    } else {
                        Modifier
                            .matchParentSize()
                            .zIndex(revealZIndex(isCurrent = false, isRevealTarget = isRevealTarget))
                            .graphicsLayer {
                                scaleX = lerp(PEEK_REST_SCALE, 1f, progress)
                                scaleY = lerp(PEEK_REST_SCALE, 1f, progress)
                                translationY = lerp(peekOffsetPx, 0f, progress)
                            }
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
                        // it reads as flush with the name's top edge instead of vertically centered.
                        modifier = Modifier.offset(y = -HEART_ICON_TOP_NUDGE_DP.dp)
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
                        OpenStatus.CLOSED -> OpenStatusBadge(
                            text = stringResource(R.string.home_closed_now),
                            color = MaterialTheme.colorScheme.error
                        )
                        OpenStatus.UNKNOWN -> {}
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
