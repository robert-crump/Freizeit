package com.example.freizeit.ui.checkin

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freizeit.R
import com.example.freizeit.ui.map.categoryIcon
import com.example.freizeit.ui.map.displayName
import com.example.freizeit.ui.map.markerBackgroundColor
import com.example.freizeit.ui.map.markerForegroundColor
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LocationHelper

private val ROW_HEIGHT = 72.dp
private val ROW_ICON_SIZE = 40.dp
private val ROW_HORIZONTAL_PADDING = 16.dp
private val ROW_LEADING_WIDTH = 48.dp
private val ROW_CONTENT_SPACING = 12.dp
private val ROW_DIVIDER_INSET = ROW_HORIZONTAL_PADDING + ROW_LEADING_WIDTH + ROW_CONTENT_SPACING

/**
 * Full-screen search, pushed as a route from [CheckInScreen]'s "+" FAB — modeled on Map's
 * `SearchOverlay` shell (back arrow + autofocused field), but reuses [CheckInViewModel]'s own
 * debounced ranking/search (not an undebounced in-memory filter) since it already existed here.
 * Below 2 characters shows [CheckInUiState.favoritesNearby] as quick picks instead of a blank list.
 */
@Composable
fun CheckInSearchScreen(
    viewModel: CheckInViewModel,
    onBack: () -> Unit,
    onCandidateSelected: (CheckInCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var searchText by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // No "committed" search concept here (unlike Map's oval) — leaving the screen always clears
    // the in-progress query rather than leaving it live for the next time search is opened.
    fun handleBack() {
        viewModel.clearSearch()
        onBack()
    }

    BackHandler(onBack = ::handleBack)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = ::handleBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.checkin_history_back)
                    )
                }
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        viewModel.setSearchQuery(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.checkin_search_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    trailingIcon = if (searchText.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                searchText = ""
                                viewModel.clearSearch()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.checkin_search_clear))
                            }
                        }
                    } else null
                )
            }

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
                            itemsIndexed(state.searchResults, key = { _, candidate -> candidate.poi.id }) { index, candidate ->
                                CheckInResultRow(candidate = candidate, onClick = { onCandidateSelected(candidate) })
                                if (index != state.searchResults.lastIndex || state.hasMoreSearchResults) {
                                    HorizontalDivider(modifier = Modifier.padding(start = ROW_DIVIDER_INSET))
                                }
                            }
                            if (state.hasMoreSearchResults) {
                                item {
                                    Text(
                                        text = stringResource(R.string.checkin_search_more_results),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = viewModel::showMoreSearchResults)
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
                            itemsIndexed(state.favoritesNearby, key = { _, candidate -> candidate.poi.id }) { index, candidate ->
                                CheckInResultRow(candidate = candidate, onClick = { onCandidateSelected(candidate) })
                                if (index != state.favoritesNearby.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = ROW_DIVIDER_INSET))
                                }
                            }
                        }
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
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Mirrors Map's `SearchResultRow` visual style (icon-in-circle, distance below, city subtitle)
 *  instead of the old category-subtitle/heart-badge `CheckInRow`, dropped for consistency (#39). */
@Composable
private fun CheckInResultRow(
    candidate: CheckInCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = isSystemInDarkTheme()
    val background = markerBackgroundColor(darkTheme)
    val foreground = markerForegroundColor(darkTheme)
    val poi = candidate.poi

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
                    imageVector = categoryIcon(poi.category),
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(ROW_ICON_SIZE * 0.6f)
                )
            }
            Text(
                text = GeoDistance.format(candidate.distanceMeters),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = poi.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            poi.city?.let { city ->
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
