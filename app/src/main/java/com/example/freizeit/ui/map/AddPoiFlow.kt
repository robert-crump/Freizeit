package com.example.freizeit.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.freizeit.R
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.newCustomPoiId
import com.example.freizeit.ui.common.CATEGORY_ORDER
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon

/**
 * Fixed center crosshair drawn over [PoiMap] while [AddPoiStep.PLACING_PIN] is active — the map
 * itself pans underneath it (issue #45's "drop a pin" step); [MapViewModel.addPoiCenter] tracks
 * wherever it's currently pointing via [PoiMap]'s onCameraIdle callback.
 */
@Composable
fun AddPoiPinOverlay(
    onCancel: () -> Unit,
    onUseLocation: () -> Unit,
    useLocationEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(4.dp),
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
 * Name/category/address form for a new [CustomPoi], seeded at [centerLatLon] (wherever the pin
 * landed). [findNearbyDuplicate] backs the pre-save proximity warning (issue #45's "there's
 * already a X ~Ym away" check) — confirming it saves anyway, dismissing it returns to the form
 * unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPoiForm(
    centerLatLon: LatLon,
    findNearbyDuplicate: (Double, Double, String) -> Poi?,
    onDismiss: () -> Unit,
    onSave: (CustomPoi) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var street by rememberSaveable { mutableStateOf("") }
    var housenumber by rememberSaveable { mutableStateOf("") }
    var postcode by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var openingHours by rememberSaveable { mutableStateOf("") }
    var pendingDuplicate by remember { mutableStateOf<Poi?>(null) }

    val trimmedName = name.trim()
    val canSave = trimmedName.isNotBlank() && category != null

    fun buildCandidate(selectedCategory: String) = CustomPoi(
        id = newCustomPoiId(),
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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.add_poi_close))
                }
                Text(stringResource(R.string.add_poi_title), style = MaterialTheme.typography.titleLarge)
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
