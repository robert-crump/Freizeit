package com.example.freizeit.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.newCustomPoiId
import com.example.freizeit.domain.geocoding.GeocodeResult
import com.example.freizeit.ui.common.CATEGORY_ORDER
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon

/**
 * Builds the [CustomPoi] a form Save resolves to. Pulled out as a pure function (mirroring
 * [MapViewModel]'s filterAndSort/excludePendingDelete convention) so #47's edit-save path — a
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
 * The address search bar (issue #46) lives here rather than on [AddPoiForm]: selecting a result
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
    Column(modifier = modifier.fillMaxSize()) {
        // No statusBarsPadding here: this overlay sits inside MapScreen's Box, which is already
        // inset by Scaffold's own top padding (passed down as innerPadding) — adding it again
        // doubled the top gap, sitting this row visibly lower than SearchOval right below it in
        // the normal (non-pin-placing) state, which relies on that same single inset.
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
                // bounding box) points at the map's actual center underneath it.
                modifier = Modifier.padding(bottom = 24.dp).size(48.dp),
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

/**
 * Name/category/address form for a [CustomPoi], seeded at [centerLatLon] (wherever the pin
 * landed). [findNearbyDuplicate] backs the pre-save proximity warning (issue #45's "there's
 * already a X ~Ym away" check) — confirming it saves anyway, dismissing it returns to the form
 * unchanged.
 *
 * [initial] is null for the normal add flow (a fresh [newCustomPoiId] is minted on save) or a
 * custom POI's current values when reopened via #47's Edit action — the form fields seed from it
 * instead of starting blank, and Save reuses its `id` so the result upserts in place rather than
 * creating a second place; [centerLatLon] stays fixed at the original location either way, since
 * editing doesn't re-enter pin-placement.
 *
 * [prefillAddress] is issue #46's address-search result, if the pin-placement step's search bar
 * was used to get here — seeds street/housenumber/postcode/city (still editable afterward) but is
 * ignored whenever [initial] is non-null, since #47's edit flow never re-enters pin-placement/
 * search and its own values should always win.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPoiForm(
    centerLatLon: LatLon,
    findNearbyDuplicate: (Double, Double, String) -> Poi?,
    onDismiss: () -> Unit,
    onSave: (CustomPoi) -> Unit,
    initial: CustomPoi? = null,
    prefillAddress: GeocodeResult? = null,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(initial?.category) }
    var street by rememberSaveable { mutableStateOf(initial?.street ?: prefillAddress?.street.orEmpty()) }
    var housenumber by rememberSaveable {
        mutableStateOf(initial?.housenumber ?: prefillAddress?.housenumber.orEmpty())
    }
    var postcode by rememberSaveable { mutableStateOf(initial?.postcode ?: prefillAddress?.postcode.orEmpty()) }
    var city by rememberSaveable { mutableStateOf(initial?.city ?: prefillAddress?.city.orEmpty()) }
    var openingHours by rememberSaveable { mutableStateOf(initial?.openingHours.orEmpty()) }
    var pendingDuplicate by remember { mutableStateOf<Poi?>(null) }

    val trimmedName = name.trim()
    val canSave = trimmedName.isNotBlank() && category != null

    fun buildCandidate(selectedCategory: String) = buildCustomPoiCandidate(
        initial = initial,
        category = selectedCategory,
        lat = centerLatLon.lat,
        lon = centerLatLon.lon,
        name = trimmedName,
        openingHours = openingHours.trim().ifBlank { null },
        street = street.trim().ifBlank { null },
        housenumber = housenumber.trim().ifBlank { null },
        postcode = postcode.trim().ifBlank { null },
        city = city.trim().ifBlank { null }
    )

    fun attemptSave() {
        val selectedCategory = category ?: return
        if (trimmedName.isBlank()) return
        val duplicate = findNearbyDuplicate(centerLatLon.lat, centerLatLon.lon, selectedCategory)
        if (duplicate != null) {
            pendingDuplicate = duplicate
        } else {
            onSave(buildCandidate(selectedCategory))
        }
    }

    BackHandler(onBack = onDismiss)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // Same double-inset issue as AddPoiPinOverlay above: this form is hosted inside the same
        // already-Scaffold-inset MapScreen content, so its own statusBarsPadding stacked a second
        // top gap on top of that.
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.add_poi_close))
                }
                Text(
                    text = stringResource(if (initial != null) R.string.add_poi_edit_title else R.string.add_poi_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_poi_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.add_poi_category_label), style = MaterialTheme.typography.labelLarge)
                    CategoryChipRow(selected = category, onSelect = { category = it })
                }
                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it },
                    label = { Text(stringResource(R.string.add_poi_street_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = housenumber,
                    onValueChange = { housenumber = it },
                    label = { Text(stringResource(R.string.add_poi_housenumber_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = postcode,
                    onValueChange = { postcode = it },
                    label = { Text(stringResource(R.string.add_poi_postcode_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(stringResource(R.string.add_poi_city_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingHours,
                    onValueChange = { openingHours = it },
                    label = { Text(stringResource(R.string.add_poi_opening_hours_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.add_poi_cancel))
                }
                Button(onClick = ::attemptSave, enabled = canSave) {
                    Text(stringResource(R.string.add_poi_save))
                }
            }
        }
    }

    pendingDuplicate?.let { duplicate ->
        val distance = GeoDistance.metersBetween(centerLatLon.lat, centerLatLon.lon, duplicate.lat, duplicate.lon)
        AlertDialog(
            onDismissRequest = { pendingDuplicate = null },
            text = {
                Text(
                    stringResource(
                        R.string.add_poi_proximity_warning,
                        categoryDisplayName(duplicate.category),
                        GeoDistance.format(distance)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val selectedCategory = category
                    pendingDuplicate = null
                    if (selectedCategory != null) onSave(buildCandidate(selectedCategory))
                }) {
                    Text(stringResource(R.string.add_poi_proximity_add_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDuplicate = null }) {
                    Text(stringResource(R.string.add_poi_cancel))
                }
            }
        )
    }
}

/** Single-select category chip row for the add-POI form — every [CATEGORY_ORDER] entry (not just
 *  [com.example.freizeit.ui.common.PRIMARY_MAP_CATEGORIES]'s curated map-filter subset), since
 *  here the user is choosing among all of them, not filtering an already-populated map. Default
 *  [FilterChip] colors, deliberately not the marker-themed foreground/background pair
 *  [PoiCategoryChipRow] uses — this is a plain form control, not a map overlay. */
@Composable
private fun CategoryChipRow(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(CATEGORY_ORDER, key = { it }) { cat ->
            FilterChip(
                selected = cat == selected,
                onClick = { onSelect(cat) },
                label = { Text(categoryDisplayName(cat)) },
                leadingIcon = { Icon(categoryIcon(cat), contentDescription = null) }
            )
        }
    }
}
