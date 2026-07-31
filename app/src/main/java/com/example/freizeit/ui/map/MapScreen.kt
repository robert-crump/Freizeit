package com.example.freizeit.ui.map

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freizeit.R
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.theme.WantToGoBlue
import com.example.freizeit.util.LocationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    onOpenSearch: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPoi by viewModel.selectedPoi.collectAsStateWithLifecycle()
    val selectedPoiLastVisit by viewModel.selectedPoiLastVisit.collectAsStateWithLifecycle()
    val focusTarget by viewModel.focusTarget.collectAsStateWithLifecycle()
    val focusRequest by viewModel.focusRequest.collectAsStateWithLifecycle()
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

    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }
    var showLayersPanel by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showLayersPanel) { showLayersPanel = false }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.pois.isEmpty() && state.categories.isEmpty()) {
            Text(
                text = stringResource(R.string.map_empty),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            PoiMap(
                pois = state.pois,
                location = state.location,
                onPoiClick = viewModel::selectPoi,
                customNames = state.customNames,
                recenterRequest = recenterRequest,
                focusTarget = focusTarget,
                focusRequest = focusRequest,
                modifier = Modifier.fillMaxSize()
            )
            Column(modifier = Modifier.align(Alignment.TopCenter)) {
                SearchOval(
                    committedQuery = state.committedSearchQuery,
                    onOvalClick = { onOpenSearch(state.committedSearchQuery ?: "") },
                    onClear = viewModel::clearSearch
                )
                PoiCategoryChipRow(
                    categories = state.categories,
                    activeCategory = state.activeCategory,
                    onSelectCategory = viewModel::selectCategory
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(onClick = { showLayersPanel = true }) {
                    Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.map_layers_button))
                }
                FloatingActionButton(
                    onClick = {
                        viewModel.refreshLocation()
                        recenterRequest++
                    }
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_locate_me))
                }
            }
        }

        if (showLayersPanel) {
            LayersPanel(
                favoritesOnly = state.favoritesOnly,
                wantToGoOnly = state.wantToGoOnly,
                onSelectFavorites = { viewModel.toggleFavoritesOnly() },
                onSelectWantToGo = { viewModel.toggleWantToGoOnly() },
                onDismiss = { showLayersPanel = false }
            )
        }
    }

    selectedPoi?.let { item ->
        PlaceDetailSheet(
            item = item,
            verdict = state.verdicts[item.poi.id]?.value,
            onVerdictChange = { viewModel.setVerdict(item.poi, it) },
            customName = state.customNames[item.poi.id],
            onCustomNameChange = { viewModel.setCustomName(item.poi.id, it) },
            lastVisit = selectedPoiLastVisit,
            onDismiss = { viewModel.selectPoi(null) }
        )
    }
}

/**
 * Floats inside the map's own [Box] (top-aligned), same convention as [PoiCategoryChipRow] below
 * it. Placeholder "Search here" when no search is committed; once one is (via the overlay's
 * keyboard Search action or a row tap), shows the query text + a trailing clear X instead.
 * Tapping the oval body (not the X) reopens the full-screen overlay with that query preloaded.
 */
@Composable
private fun SearchOval(
    committedQuery: String?,
    onOvalClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onOvalClick),
        shape = RoundedCornerShape(percent = 50),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.map_search_icon))
            Text(
                text = committedQuery ?: stringResource(R.string.map_search_oval_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = if (committedQuery != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
            if (committedQuery != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_search_clear))
                }
            }
        }
    }
}

/**
 * Scrim + centered [Card] modal (mirrors Velometrics' layers FAB shell). Favorites/Want to go
 * are mutually exclusive toggle switches — only one is ever active on the map at a time, and
 * also mutually exclusive with the category chip row above the map (see [PoiCategoryChipRow]),
 * which replaced this panel's old "All POIs" row and per-category list. The panel itself stays
 * open across toggles so both rows can be tried in turn; only the scrim or the close button
 * dismisses it.
 */
@Composable
private fun LayersPanel(
    favoritesOnly: Boolean,
    wantToGoOnly: Boolean,
    onSelectFavorites: () -> Unit,
    onSelectWantToGo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            )
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.map_layers_title), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_layers_close))
                    }
                }
                LayerRow(
                    label = stringResource(R.string.map_favorites_filter),
                    selected = favoritesOnly,
                    onClick = onSelectFavorites,
                    leadingIcon = { Text("❤️") }
                )
                LayerRow(
                    label = stringResource(R.string.map_want_to_go_filter),
                    selected = wantToGoOnly,
                    onClick = onSelectWantToGo,
                    leadingIcon = {
                        Icon(Icons.Filled.Bookmark, contentDescription = null, tint = WantToGoBlue)
                    }
                )
            }
        }
    }
}

@Composable
private fun LayerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        leadingIcon()
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = selected, onCheckedChange = { onClick() })
    }
}

/**
 * Single-select category chip row, floated over the map's top edge, below the search oval — now
 * always visible (no longer hidden while a search dropdown shows; that dropdown no longer lives
 * on this screen at all, see [SearchOverlay]). Every chip shares one color (no per-category
 * coding, matching the map markers' own move away from that — see [markerForegroundColor]/
 * [markerBackgroundColor]): outlined in the foreground color when unselected, filled with it when
 * selected, with icon+label flipping to the background color for contrast — the same relationship
 * the marker circle/icon pair already has. Icon and label sizing come from [FilterChip]'s own
 * defaults (no custom size overrides), matching Velometrics' chip row.
 */
@Composable
private fun PoiCategoryChipRow(
    categories: List<String>,
    activeCategory: String?,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = isSystemInDarkTheme()
    val foreground = markerForegroundColor(darkTheme)
    val background = markerBackgroundColor(darkTheme)
    LazyRow(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        items(categories, key = { it }) { category ->
            val selected = category == activeCategory
            FilterChip(
                selected = selected,
                onClick = { onSelectCategory(category) },
                label = { Text(categoryDisplayName(category)) },
                leadingIcon = {
                    Icon(categoryIcon(category), contentDescription = null)
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = foreground,
                    iconColor = foreground,
                    selectedContainerColor = foreground,
                    selectedLabelColor = background,
                    selectedLeadingIconColor = background
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = foreground,
                    selectedBorderColor = foreground
                )
            )
        }
    }
}
