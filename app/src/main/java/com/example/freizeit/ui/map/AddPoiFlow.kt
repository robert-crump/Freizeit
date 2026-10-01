package com.example.freizeit.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.newCustomPoiId
import com.example.freizeit.domain.geocoding.GeocodeResult

/**
 * Builds the [CustomPoi] a form Save resolves to. Pulled out as a pure function (mirroring
 * [MapViewModel]'s filterAndSort/excludePendingRemoval convention) so #47's edit-save path — a
 * non-null [initial] reuses its `id` so the result upserts in place instead of
 * [newCustomPoiId] minting a second, separate place — is unit-testable without a Compose harness.
 */
fun buildCustomPoiCandidate(
    initial: CustomPoi?,
    category: String,
    lat: Double,
    lon: Double,
    name: String,
    openingHours: String?,
    street: String?,
    housenumber: String?,
    postcode: String?,
    city: String?
): CustomPoi = CustomPoi(
    id = initial?.id ?: newCustomPoiId(),
    category = category,
    lat = lat,
    lon = lon,
    name = name,
    openingHours = openingHours,
    street = street,
    housenumber = housenumber,
    postcode = postcode,
    city = city
)

/**
 * Fixed center crosshair drawn over [PoiMap] while [AddPoiStep.PLACING_PIN] is active — the map
 * itself pans underneath it (issue #45's "drop a pin" step); [MapViewModel.addPoiCenter] tracks
 * wherever it's currently pointing via [PoiMap]'s onCameraIdle callback.
 *
 * The address search bar (issue #46) lives here rather than on [PlaceEditForm]: selecting a result
 * re-centers this same crosshair (via [onSelectResult], which drives [MapViewModel.focusOn] under
 * the hood) rather than needing a second, separate "place the pin" mechanism.
 */
@Composable
fun AddPoiPinOverlay(
    onCancel: () -> Unit,
    onUseLocation: () -> Unit,
    searchState: AddressSearchState,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (GeocodeResult) -> Unit,
    useLocationEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)
    // The map underneath runs under the transparent status bar while this overlay starts below
    // it, so the map's center sits half a status bar higher than this overlay's.
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.add_poi_close))
            }
            Text(
                text = stringResource(R.string.add_poi_placing_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        AddressSearchBar(
            state = searchState,
            onQueryChange = onSearchQueryChange,
            onSearch = onSearch,
            onSelectResult = onSelectResult,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AddLocationAlt,
                contentDescription = stringResource(R.string.add_poi_pin_description),
                // Offsets the icon's visual center up slightly so its pin-tip (not its square
                // bounding box) points at the map's actual center underneath it; the extra
                // status-bar height lifts it by the half status bar the map's center is higher.
                modifier = Modifier.padding(bottom = 24.dp + statusBarHeight).size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Button(
            onClick = onUseLocation,
            enabled = useLocationEnabled,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.add_poi_use_location))
        }
    }
}

/**
 * Explicit-submit address search (issue #46) — no live-typeahead, respecting Nominatim's 1 req/sec
 * usage policy without needing debounce logic. The "© OpenStreetMap contributors" attribution is
 * always shown beneath the field per that same policy, since this is the app's only Nominatim
 * usage and there's no existing OSM attribution elsewhere to piggyback on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSearchBar(
    state: AddressSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (GeocodeResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.add_poi_address_search_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onSearch, enabled = state.query.isNotBlank() && !state.isSearching) {
                Text(stringResource(R.string.add_poi_address_search_button))
            }
        }
        Text(
            text = stringResource(R.string.add_poi_osm_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            state.isSearching -> CircularProgressIndicator(
                modifier = Modifier.padding(8.dp).size(20.dp),
                strokeWidth = 2.dp
            )
            state.error -> Text(
                text = stringResource(R.string.add_poi_address_search_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            state.searched && state.results.isEmpty() -> Text(
                text = stringResource(R.string.add_poi_address_search_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.results.isNotEmpty() -> Card {
                Column {
                    state.results.forEach { result ->
                        Text(
                            text = result.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectResult(result) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
