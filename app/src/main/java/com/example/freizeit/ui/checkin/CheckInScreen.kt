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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCheckIn by remember { mutableStateOf<CheckInCandidate?>(null) }

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CheckInSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                onClear = viewModel::clearSearch
            )

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
                    !state.hasLocation -> CenteredHint(
                        if (state.isSearching) {
                            stringResource(R.string.checkin_search_waiting_location)
                        } else {
                            stringResource(R.string.checkin_empty_no_location)
                        }
                    )
                    state.isSearching -> {
                        if (state.searchResults.isEmpty()) {
                            CenteredHint(stringResource(R.string.checkin_search_empty))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.searchResults, key = { it.poi.id }) { candidate ->
                                    CheckInRow(candidate = candidate, onClick = { pendingCheckIn = candidate })
                                }
                            }
                        }
                    }
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.checkin_favorites_nearby_header),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.favoritesNearby.isEmpty()) {
                            CenteredHint(stringResource(R.string.checkin_favorites_nearby_empty))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.favoritesNearby, key = { it.poi.id }) { candidate ->
                                    CheckInRow(candidate = candidate, onClick = { pendingCheckIn = candidate })
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onHistoryClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.History, contentDescription = stringResource(R.string.checkin_history_button))
        }
    }

    pendingCheckIn?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingCheckIn = null },
            title = {
                Text(stringResource(R.string.checkin_confirm_message, candidate.poi.displayName()))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.checkIn(candidate.poi)
                    pendingCheckIn = null
                }) {
                    Text(stringResource(R.string.checkin_confirm_checkin))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCheckIn = null }) {
                    Text(stringResource(R.string.checkin_confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun CheckInSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.checkin_search_icon))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.checkin_search_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.checkin_search_clear))
                    }
                }
            } else null
        )
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
