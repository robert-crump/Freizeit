package com.example.freizeit.ui.checkin

import android.Manifest
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freizeit.R
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.explore.CategoryDot
import com.example.freizeit.ui.explore.displayName
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LocationHelper

@Composable
fun CheckInScreen(
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory)
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

    Column(modifier = modifier.fillMaxSize()) {
        state.lastCheckedInName?.let { name ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = stringResource(R.string.checkin_checked_in, name),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                !state.hasLocation -> CenteredHint(stringResource(R.string.checkin_empty_no_location))
                state.nearby.isEmpty() -> CenteredHint(stringResource(R.string.checkin_empty_none_nearby))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.nearby, key = { it.poi.id }) { candidate ->
                        CheckInRow(candidate = candidate, onClick = { viewModel.checkIn(candidate.poi) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CheckInRow(
    candidate: CheckInCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val poi = candidate.poi
    val subtitle = listOfNotNull(
        GeoDistance.format(candidate.distanceMeters),
        categoryDisplayName(poi.category)
    ).joinToString(" | ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryDot(poi.category)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = poi.displayName(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (candidate.isFavorite) {
            Text("❤️")
        }
    }
}
