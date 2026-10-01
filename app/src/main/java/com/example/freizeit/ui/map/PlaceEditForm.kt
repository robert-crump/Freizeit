package com.example.freizeit.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import com.example.freizeit.ui.common.CATEGORY_ORDER
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon

/** The edit form's fields as typed (#73). A null [category] means none picked yet (Add place). */
data class PlaceFormValues(
    val name: String = "",
    val category: String? = null,
    val street: String = "",
    val housenumber: String = "",
    val postcode: String = "",
    val city: String = "",
    val openingHours: String = ""
) {
    /** Whitespace-trimmed, so "changed?" ignores stray spaces. */
    fun normalized(): PlaceFormValues = PlaceFormValues(
        name = name.trim(),
        category = category,
        street = street.trim(),
        housenumber = housenumber.trim(),
        postcode = postcode.trim(),
        city = city.trim(),
        openingHours = openingHours.trim()
    )
}

fun Poi.toFormValues(): PlaceFormValues = PlaceFormValues(
    name = name.orEmpty(),
    category = category,
    street = street.orEmpty(),
    housenumber = housenumber.orEmpty(),
    postcode = postcode.orEmpty(),
    city = city.orEmpty(),
    openingHours = openingHours.orEmpty()
)

fun CustomPoi.toFormValues(): PlaceFormValues = PlaceFormValues(
    name = name,
    category = category,
    street = street.orEmpty(),
    housenumber = housenumber.orEmpty(),
    postcode = postcode.orEmpty(),
    city = city.orEmpty(),
    openingHours = openingHours.orEmpty()
)

/**
 * Whether the form's ✓ is enabled (#73): something differs from [initial] and the values are
 * valid. A custom place ([requiresName]) needs a name and a category; an OSM place is always
 * valid, since a blank field just falls back to the OSM value.
 */
fun canSavePlaceForm(current: PlaceFormValues, initial: PlaceFormValues, requiresName: Boolean): Boolean {
    val normalized = current.normalized()
    val valid = !requiresName || (normalized.name.isNotEmpty() && normalized.category != null)
    return valid && normalized != initial.normalized()
}

/** What the form's bottom action does to an existing place (#73). */
enum class PlaceRemoval { DELETE, HIDE }

/**
 * Full-screen form shared by Add place and Edit place (#73): ✕ / title / ✓ header, the fields,
 * and for an existing place a red Delete (custom) / Hide (OSM) action at the bottom. Location
 * isn't editable; [centerLatLon] is where the place is (or where the pin landed).
 *
 * [osmValues] is non-null for an OSM place: blank fields are then allowed (they fall back to
 * OSM), and "Reset to OpenStreetMap" fills the form with these values whenever it differs from
 * them. ✕ (and system Back) asks "Discard changes?" only when something was edited.
 *
 * [findNearbyDuplicate] backs the add-place proximity warning (issue #45's "there's already a X
 * ~Ym away" check) — confirming it saves anyway, dismissing it returns to the form unchanged.
 * Null skips the check (OSM places, which come with their own location).
 */
@Composable
fun PlaceEditForm(
    isNew: Boolean,
    initial: PlaceFormValues,
    centerLatLon: LatLon,
    onClose: () -> Unit,
    onSave: (PlaceFormValues) -> Unit,
    modifier: Modifier = Modifier,
    osmValues: PlaceFormValues? = null,
    removal: PlaceRemoval? = null,
    /** The place's current display name, for the delete/hide confirmation. */
    placeName: String = "",
    onRemove: () -> Unit = {},
    findNearbyDuplicate: ((category: String) -> Poi?)? = null
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var street by rememberSaveable { mutableStateOf(initial.street) }
    var housenumber by rememberSaveable { mutableStateOf(initial.housenumber) }
    var postcode by rememberSaveable { mutableStateOf(initial.postcode) }
    var city by rememberSaveable { mutableStateOf(initial.city) }
    var openingHours by rememberSaveable { mutableStateOf(initial.openingHours) }
    var pendingDuplicate by remember { mutableStateOf<Poi?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    val current = PlaceFormValues(name, category, street, housenumber, postcode, city, openingHours)
    val isDirty = current.normalized() != initial.normalized()
    val canSave = canSavePlaceForm(current, initial, requiresName = osmValues == null)

    fun fill(values: PlaceFormValues) {
        name = values.name
        category = values.category
        street = values.street
        housenumber = values.housenumber
        postcode = values.postcode
        city = values.city
        openingHours = values.openingHours
    }

    fun attemptSave() {
        if (!canSave) return
        val duplicate = current.category?.let { findNearbyDuplicate?.invoke(it) }
        if (duplicate != null) pendingDuplicate = duplicate else onSave(current.normalized())
    }

    fun attemptClose() {
        if (isDirty) showDiscardDialog = true else onClose()
    }

    BackHandler(onBack = ::attemptClose)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // No statusBarsPadding: this form is hosted inside MapScreen's content, which the
        // Scaffold has already inset.
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = ::attemptClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.add_poi_close))
                }
                Text(
                    text = stringResource(if (isNew) R.string.add_poi_title else R.string.add_poi_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = ::attemptSave, enabled = canSave) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.add_poi_save))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormField(
                    value = name,
                    onValueChange = { name = it },
                    label = R.string.add_poi_name_label,
                    capitalization = KeyboardCapitalization.Words
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.add_poi_category_label), style = MaterialTheme.typography.labelLarge)
                    CategoryChipRow(selected = category, onSelect = { category = it })
                }
                FormField(value = street, onValueChange = { street = it }, label = R.string.add_poi_street_label)
                FormField(
                    value = housenumber,
                    onValueChange = { housenumber = it },
                    label = R.string.add_poi_housenumber_label
                )
                FormField(value = postcode, onValueChange = { postcode = it }, label = R.string.add_poi_postcode_label)
                FormField(value = city, onValueChange = { city = it }, label = R.string.add_poi_city_label)
                FormField(
                    value = openingHours,
                    onValueChange = { openingHours = it },
                    label = R.string.add_poi_opening_hours_label
                )
                if (osmValues != null && current.normalized() != osmValues.normalized()) {
                    TextButton(onClick = { fill(osmValues) }) {
                        Text(stringResource(R.string.place_edit_reset_to_osm))
                    }
                }
                removal?.let {
                    TextButton(onClick = { showRemoveDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                if (it == PlaceRemoval.DELETE) R.string.place_edit_delete else R.string.place_edit_hide
                            ),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.place_edit_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onClose()
                }) {
                    Text(stringResource(R.string.place_edit_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.place_edit_keep_editing))
                }
            }
        )
    }

    if (showRemoveDialog && removal != null) {
        val isDelete = removal == PlaceRemoval.DELETE
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = {
                Text(
                    stringResource(
                        if (isDelete) R.string.place_edit_delete_confirm else R.string.place_edit_hide_confirm,
                        placeName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemove()
                }) {
                    Text(
                        stringResource(if (isDelete) R.string.place_edit_delete_action else R.string.place_edit_hide_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.add_poi_cancel))
                }
            }
        )
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
                    pendingDuplicate = null
                    onSave(current.normalized())
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

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Single-select category chip row for the edit form — every [CATEGORY_ORDER] entry (not just
 *  [com.example.freizeit.ui.common.PRIMARY_MAP_CATEGORIES]'s curated map-filter subset), since
 *  here the user is choosing among all of them, not filtering an already-populated map. Default
 *  [FilterChip] colors, deliberately not the marker-themed foreground/background pair the map's
 *  chip row uses — this is a plain form control, not a map overlay. */
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
