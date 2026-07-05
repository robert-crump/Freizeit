package com.example.freizeit.ui.explore

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.categories.forEach { category ->
                FilterChip(
                    selected = category in state.selectedCategories,
                    onClick = { viewModel.toggleCategory(category) },
                    label = { Text(categoryDisplayName(category)) },
                    leadingIcon = { CategoryDot(category) }
                )
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
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
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PoiList(
                    pois = state.pois,
                    onPoiClick = viewModel::selectPoi,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    selectedPoi?.let { item ->
        PlaceDetailSheet(item = item, onDismiss = { viewModel.selectPoi(null) })
    }
}

@Composable
private fun PoiList(
    pois: List<PoiWithDistance>,
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
                        text = item.poi.displayName(),
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
