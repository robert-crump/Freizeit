package com.example.freizeit.ui.explore

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LocationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPoi by viewModel.selectedPoi.collectAsStateWithLifecycle()
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

    var viewIndex by rememberSaveable { mutableIntStateOf(0) }
    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchActive) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.explore_search_placeholder)) },
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        searchActive = false
                        viewModel.clearSearch()
                    }
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.explore_search_close))
                }
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    listOf(
                        stringResource(R.string.explore_view_map),
                        stringResource(R.string.explore_view_list)
                    ).forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = viewIndex == index,
                            onClick = { viewIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                        ) {
                            Text(label)
                        }
                    }
                }
                IconButton(onClick = { searchActive = true }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.explore_search_icon))
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.pois.isEmpty() && state.categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.explore_empty),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (viewIndex == 0) {
                PoiMap(
                    pois = state.pois,
                    location = state.location,
                    onPoiClick = viewModel::selectPoi,
                    customNames = state.customNames,
                    recenterRequest = recenterRequest,
                    modifier = Modifier.fillMaxSize()
                )
                PoiCategoryChipRow(
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    onSelect = viewModel::selectCategory,
                    favoritesOnly = state.favoritesOnly,
                    onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                FloatingActionButton(
                    onClick = {
                        viewModel.refreshLocation()
                        recenterRequest++
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.explore_locate_me))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PoiCategoryChipRow(
                        categories = state.categories,
                        selectedCategory = state.selectedCategory,
                        onSelect = viewModel::selectCategory,
                        favoritesOnly = state.favoritesOnly,
                        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly
                    )
                    PoiList(
                        pois = state.pois,
                        customNames = state.customNames,
                        onPoiClick = viewModel::selectPoi,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    selectedPoi?.let { item ->
        PlaceDetailSheet(
            item = item,
            verdict = state.verdicts[item.poi.id]?.value,
            onVerdictChange = { viewModel.setVerdict(item.poi, it) },
            customName = state.customNames[item.poi.id],
            onCustomNameChange = { viewModel.setCustomName(item.poi.id, it) },
            onDismiss = { viewModel.selectPoi(null) },
            location = state.location
        )
    }
}

@Composable
private fun PoiCategoryChipRow(
    categories: List<String>,
    selectedCategory: String?,
    onSelect: (String) -> Unit,
    favoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        item {
            FilterChip(
                selected = favoritesOnly,
                onClick = onToggleFavoritesOnly,
                label = { Text(stringResource(R.string.explore_favorites_filter)) },
                leadingIcon = { Text("❤️") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onSelect(category) },
                label = { Text(categoryDisplayName(category)) },
                leadingIcon = { CategoryDot(category) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Composable
private fun PoiList(
    pois: List<PoiWithDistance>,
    customNames: Map<String, String>,
    onPoiClick: (PoiWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(pois, key = { it.poi.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPoiClick(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryDot(item.poi.category)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.poi.displayName(customNames[item.poi.id]),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = categoryDisplayName(item.poi.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.distanceMeters?.let {
                    Text(
                        text = GeoDistance.format(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
