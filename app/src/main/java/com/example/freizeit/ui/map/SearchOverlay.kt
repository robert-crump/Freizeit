package com.example.freizeit.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freizeit.R
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon

/** Below this many characters, matches are too broad to be a useful jump-to list. */
private const val SEARCH_MIN_LENGTH = 2

private val ROW_HEIGHT = 72.dp
private val ROW_ICON_SIZE = 40.dp
private val ROW_HORIZONTAL_PADDING = 16.dp
private val ROW_LEADING_WIDTH = 48.dp
private val ROW_CONTENT_SPACING = 12.dp

/** Where the divider (and the trailing text column) starts — past the leading icon/distance
 *  column, so dividers are inset to the text column rather than running full-bleed under the icon. */
private val ROW_DIVIDER_INSET = ROW_HORIZONTAL_PADDING + ROW_LEADING_WIDTH + ROW_CONTENT_SPACING

/**
 * Full-screen search surface hoisted above the [androidx.compose.material3.Scaffold] in
 * `FreizeitApp` (sibling to the NavHost, not a NavHost/Dialog route) — shares [viewModel] with
 * the Map screen so a row tap here can drive its camera jump and detail sheet directly (#37).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlay(
    viewModel: MapViewModel,
    initialQuery: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Back (system or the on-screen arrow) discards the typed text and leaves whatever search
    // was already committed untouched — neither path below ever calls commitSearch/clearSearch.
    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Cheap in-memory filter over all POIs (ignores whatever layer filter was active before
    // opening search) — no debounce, since the map isn't redrawing off this list.
    val results = remember(query, state.allPois, state.location, state.customNames) {
        if (query.trim().length >= SEARCH_MIN_LENGTH) {
            filterAndSort(
                pois = state.allPois,
                activeCategory = null,
                location = state.location,
                verdictIds = null,
                searchQuery = query,
                customNames = state.customNames
            )
        } else {
            emptyList()
        }
    }

    fun commit(committedQuery: String) {
        keyboardController?.hide()
        viewModel.commitSearch(committedQuery)
        onDismiss()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.map_search_back))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { commit(query) }),
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_search_clear))
                            }
                        }
                    } else null
                )
            }

            when {
                query.trim().length < SEARCH_MIN_LENGTH -> Unit // blank until 2+ characters
                results.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.map_search_no_results),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(results, key = { _, item -> item.poi.id }) { index, item ->
                            SearchResultRow(
                                item = item,
                                customNames = state.customNames,
                                onClick = {
                                    viewModel.focusOn(LatLon(item.poi.lat, item.poi.lon))
                                    viewModel.selectPoi(item)
                                    commit(query)
                                }
                            )
                            if (index != results.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(start = ROW_DIVIDER_INSET))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Leading: category icon in the icon-in-circle style shared with the map markers (see
 * [markerBackgroundColor]/[markerForegroundColor]/[categoryIcon]), distance below it — omitted
 * (not a placeholder) when [PoiWithDistance.distanceMeters] is null. Trailing: bold POI name,
 * city below it. Fixed [ROW_HEIGHT].
 */
@Composable
private fun SearchResultRow(
    item: PoiWithDistance,
    customNames: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = isSystemInDarkTheme()
    val background = markerBackgroundColor(darkTheme)
    val foreground = markerForegroundColor(darkTheme)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = ROW_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_CONTENT_SPACING)
    ) {
        Column(
            modifier = Modifier.width(ROW_LEADING_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(ROW_ICON_SIZE)
                    .clip(CircleShape)
                    .background(background)
                    .border(1.5.dp, foreground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(item.poi.category),
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(ROW_ICON_SIZE * 0.6f)
                )
            }
            item.distanceMeters?.let { distance ->
                Text(
                    text = GeoDistance.format(distance),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.poi.displayName(customNames[item.poi.id]),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.poi.city?.let { city ->
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
