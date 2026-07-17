package com.example.freizeit.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.data.entity.PendingVisit
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.domain.suggestion.Suggestion
import com.example.freizeit.domain.suggestion.SuggestionContext
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.explore.CategoryDot
import com.example.freizeit.ui.explore.PlaceDetailSheet
import com.example.freizeit.ui.explore.PoiWithDistance
import com.example.freizeit.ui.explore.SuggestionsMiniMap
import com.example.freizeit.ui.explore.displayName
import com.example.freizeit.ui.theme.FavoriteRed
import com.example.freizeit.util.LatLon
import com.example.freizeit.util.LocationHelper
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCard by viewModel.selectedCard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    var topContentHeightPx by remember { mutableIntStateOf(0) }

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
        Column(
            modifier = Modifier.onGloballyPositioned { topContentHeightPx = it.size.height },
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

            OverrideChipsRow(
                timeBudgetMinutes = state.timeBudgetMinutes,
                kidsAlong = state.kidsAlong,
                onTimeBudgetSelect = { viewModel.setTimeBudget(it) },
                onKidsAlongSelect = { viewModel.setKidsAlong(it) }
            )
        }

        if (state.isLoading) {
            CenteredLoading()
        } else if (!state.hasPois) {
            CenteredHint(stringResource(R.string.home_empty))
        } else if (state.cards.isEmpty()) {
            CenteredHint(stringResource(R.string.home_no_suggestions))
        } else {
            val topContentHeight = with(density) { topContentHeightPx.toDp() }
            val carouselMinHeight = 300.dp
            val mapHeight = screenHeightDp - topContentHeight - carouselMinHeight - 16.dp * 2 - (12.dp * 4)

            SuggestionCarouselWithMap(
                suggestions = state.cards,
                customNames = state.customNames,
                location = state.location,
                mapHeight = mapHeight.coerceAtLeast(MIN_MAP_HEIGHT),
                onCardClick = { viewModel.selectCard(it) },
                onGo = { suggestion ->
                    val poi = suggestion.poi
                    viewModel.recordGo(poi, state.customNames[poi.id])
                    val label = Uri.encode(state.customNames[poi.id] ?: poi.name ?: poi.category)
                    val navUri = Uri.parse("geo:${poi.lat},${poi.lon}?q=${poi.lat},${poi.lon}($label)")
                    context.startActivity(Intent(Intent.ACTION_VIEW, navUri))
                },
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = viewModel::reroll
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(
                        text = stringResource(R.string.home_reroll),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }

    selectedCard?.let { card ->
        PlaceDetailSheet(
            item = PoiWithDistance(card.poi, card.distanceMeters),
            verdict = state.verdicts[card.poi.id]?.value,
            onVerdictChange = { viewModel.setVerdict(card.poi, it) },
            customName = state.customNames[card.poi.id],
            onCustomNameChange = { viewModel.setCustomName(card.poi.id, it) },
            onDismiss = { viewModel.selectCard(null) }
        )
    }
}

/** Floor for the map so a first-frame measurement of 0 doesn't collapse it. */
private val MIN_MAP_HEIGHT = 160.dp

/**
 * The mini-map and the swipeable carousel + dots below it share one
 * [androidx.compose.foundation.pager.PagerState] so tapping a map dot scrolls the
 * carousel to match, and swiping the carousel re-highlights the matching map dot.
 * Wraps around: swiping past the last card returns to the first, and vice versa.
 *
 * The highlighted dot/card only changes once a swipe fully settles on a new page
 * ([PagerState.settledPage]), not mid-drag — using the live [PagerState.currentPage]
 * there made the map highlight flicker between suggestions while dragging.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SuggestionCarouselWithMap(
    suggestions: List<Suggestion>,
    customNames: Map<String, String>,
    location: LatLon?,
    mapHeight: Dp,
    onCardClick: (Suggestion) -> Unit,
    onGo: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = suggestions.size
    if (count == 0) return

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val activeIndex = listState.firstVisibleItemIndex.coerceIn(0, count - 1)
    val activePoi = suggestions[activeIndex].poi
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidthDp - 32.dp

    Column(modifier = modifier) {
        SuggestionsMiniMap(
            pois = suggestions.map { it.poi },
            selectedPoiId = activePoi.id,
            location = location,
            onPoiClick = { poi ->
                val targetIndex = suggestions.indexOfFirst { it.poi.id == poi.id }
                if (targetIndex >= 0 && targetIndex != activeIndex) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .clip(RoundedCornerShape(12.dp))
        )

        Text(
            text = stringResource(R.string.home_suggestions_header),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // Disabled so dragging past the first/last card doesn't stretch-bounce before snapping back.
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 0.dp)
            ) {
                items(count) { index ->
                    val suggestion = suggestions[index]
                    SuggestionCard(
                        suggestion = suggestion,
                        customName = customNames[suggestion.poi.id],
                        onClick = { onCardClick(suggestion) },
                        onGo = { onGo(suggestion) },
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
        }

        if (count > 1) {
            CarouselDots(
                count = count,
                activeIndex = activeIndex,
                onDotClick = { index ->
                    coroutineScope.launch {
                        listState.animateScrollToItem(index)
                    }
                },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun CarouselDots(
    count: Int,
    activeIndex: Int,
    onDotClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val active = i == activeIndex
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                    .clickable { onDotClick(i) }
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
 * Two rows of directly-selectable filter chips (issue #11 rework of #7's tap-to-cycle chips):
 * time budget (1 h / 3 h / all day) and who's along (kids along / adults only). Every option is
 * always visible so the active choice — and what else is available — is explicit at a glance,
 * rather than hidden behind repeated taps on a single cycling chip.
 */
@Composable
private fun OverrideChipsRow(
    timeBudgetMinutes: Int,
    kidsAlong: Boolean,
    onTimeBudgetSelect: (Int) -> Unit,
    onKidsAlongSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SegmentedChipGroup(
            options = listOf(
                SuggestionContext.SHORT_TIME_BUDGET_MINUTES to R.string.home_time_budget_short,
                SuggestionContext.DEFAULT_TIME_BUDGET_MINUTES to R.string.home_time_budget_default,
                SuggestionContext.LONG_TIME_BUDGET_MINUTES to R.string.home_time_budget_long
            ),
            selected = timeBudgetMinutes,
            onSelect = onTimeBudgetSelect
        )
        SegmentedChipGroup(
            options = listOf(
                true to R.string.home_who_kids,
                false to R.string.home_who_adults
            ),
            selected = kidsAlong,
            onSelect = onKidsAlongSelect
        )
    }
}

@Composable
private fun <T> SegmentedChipGroup(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, labelRes) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    customName: String?,
    onClick: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val poi = suggestion.poi
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = poi.displayName(customName),
                    style = MaterialTheme.typography.titleLarge
                )
                Button(onClick = onGo) {
                    Text(stringResource(R.string.home_go))
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
            if (suggestion.reasons.isNotEmpty()) {
                ReasonsLine(reasons = suggestion.reasons, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "❤️ favorite" in [reasons] renders as the same filled heart icon used in the POI sheet. */
@Composable
private fun ReasonsLine(reasons: List<String>, style: TextStyle) {
    if (FAVORITE_REASON !in reasons) {
        Text(text = reasons.joinToString(" · "), style = style)
        return
    }
    val joined = reasons.joinToString(" · ") { if (it == FAVORITE_REASON) "favorite" else it }
    val favoriteWordStart = joined.indexOf("favorite")
    val annotated = buildAnnotatedString {
        append(joined.substring(0, favoriteWordStart))
        appendInlineContent(FAVORITE_ICON_ID, "[favorite]")
        append(" ")
        append(joined.substring(favoriteWordStart))
    }
    val inlineContent = mapOf(
        FAVORITE_ICON_ID to InlineTextContent(
            Placeholder(
                width = style.fontSize,
                height = style.fontSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = FavoriteRed)
        }
    )
    Text(text = annotated, style = style, inlineContent = inlineContent)
}

private const val FAVORITE_REASON = "❤️ favorite"
private const val FAVORITE_ICON_ID = "favoriteIcon"

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
