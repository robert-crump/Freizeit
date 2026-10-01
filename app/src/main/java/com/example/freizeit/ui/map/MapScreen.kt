package com.example.freizeit.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freizeit.R
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.buildPoiOverride
import com.example.freizeit.data.entity.isCustomPoiId
import com.example.freizeit.data.entity.toPoi
import com.example.freizeit.domain.geocoding.GeocodeResult
import com.example.freizeit.ui.common.SearchOvalBar
import com.example.freizeit.ui.common.categoryDisplayName
import com.example.freizeit.ui.theme.LocalDarkTheme
import com.example.freizeit.util.LatLon
import com.example.freizeit.util.LocationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    onOpenSearch: (String) -> Unit,
    /** The detail sheet's Check-in button (#64): hands the place to FreizeitApp's app-wide
     *  check-in flow, which calls `onCheckedIn` (closing the sheet) once the visit is saved and
     *  then lands on the check-in history. */
    onSheetCheckIn: (poi: Poi, placeName: String, onCheckedIn: () -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPoi by viewModel.selectedPoi.collectAsStateWithLifecycle()
    val selectedPoiLastVisit by viewModel.selectedPoiLastVisit.collectAsStateWithLifecycle()
    val focusTarget by viewModel.focusTarget.collectAsStateWithLifecycle()
    val focusRequest by viewModel.focusRequest.collectAsStateWithLifecycle()
    val addPoiStep by viewModel.addPoiStep.collectAsStateWithLifecycle()
    val addPoiCenter by viewModel.addPoiCenter.collectAsStateWithLifecycle()
    val editTarget by viewModel.editTarget.collectAsStateWithLifecycle()
    val addressSearchState by viewModel.addressSearchState.collectAsStateWithLifecycle()
    val pendingAddressPrefill by viewModel.pendingAddressPrefill.collectAsStateWithLifecycle()
    val pendingRemovalId by viewModel.pendingRemovalId.collectAsStateWithLifecycle()
    val followingLocation by viewModel.followingLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Delete/hide-with-undo (#47, #73): the marker/sheet vanish the instant requestRemovePlace
    // runs (see poisAndVerdicts' pending-removal filter), but the actual write waits on this
    // Snackbar — timing out or being swiped away commits it, tapping Undo just clears the pending
    // id back on the ViewModel. pendingRemovalName is captured at request time (the form that
    // named it is already gone by the time the Snackbar result comes back).
    val deleteSnackbarHostState = remember { SnackbarHostState() }
    var pendingRemovalName by remember { mutableStateOf("") }
    val deletedTemplate = stringResource(R.string.detail_custom_poi_deleted_snackbar)
    val hiddenTemplate = stringResource(R.string.detail_place_hidden_snackbar)
    val undoLabel = stringResource(R.string.detail_custom_poi_undo)
    LaunchedEffect(pendingRemovalId) {
        val removalId = pendingRemovalId ?: return@LaunchedEffect
        val template = if (isCustomPoiId(removalId)) deletedTemplate else hiddenTemplate
        val result = deleteSnackbarHostState.showSnackbar(
            message = String.format(template, pendingRemovalName),
            actionLabel = undoLabel,
            // Material3 defaults duration to Indefinite whenever actionLabel is non-null — without
            // this, "timing out" (per the comment above) would never actually happen on its own.
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoRemovePlace()
        } else {
            viewModel.commitPendingRemoval()
        }
    }
    // Covers "navigates away" from #47's spec — if the user leaves Map entirely (another bottom
    // nav tab) before the Snackbar above resolves on its own, commit whatever's still pending
    // rather than leaving it in limbo indefinitely. No-op if nothing is pending.
    DisposableEffect(Unit) {
        onDispose { viewModel.commitPendingRemoval() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshLocation()
        viewModel.startContinuousLocation()
    }

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

    // Live location stream (#40) armed only while Map is actually the visible tab: re-entering
    // this tab replays ON_RESUME (Lifecycle.addObserver syncs a fresh observer up to the current
    // state), which is also what fixes "switching to Map doesn't refresh location" — the same
    // mechanism HomeScreen already uses for its own resume refresh (#34).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startContinuousLocation()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopContinuousLocation()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.stopContinuousLocation()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }

    // Measured height of the SearchOval + chip rows column below, so the map's native
    // compass (see PoiMap's compassTopMarginPx) can be pushed down to clear it instead of
    // rendering hidden underneath it — reading the actual laid-out height (rather than a fixed
    // dp guess) keeps this correct if that column's content ever changes (e.g. an empty category
    // row, larger system font scale).
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        // Always mounted (issue #45): the "Add place" FAB needs a live map to pin-drop against
        // even before any POI data exists, so the map can no longer be swapped out entirely for
        // the "no places" text the way it used to be — that text now floats on top instead.
        PoiMap(
            pois = state.pois,
            location = state.location,
            onPoiClick = viewModel::selectPoi,
            recenterRequest = recenterRequest,
            focusTarget = focusTarget,
            focusRequest = focusRequest,
            addPoiActive = addPoiStep == AddPoiStep.PLACING_PIN,
            onCameraIdle = viewModel::updateAddPoiCenter,
            // Zero while placing a pin: the search bar/chip row aren't shown then, so the
            // compass has nothing to hide behind at the top edge.
            compassTopMarginPx = if (addPoiStep == AddPoiStep.PLACING_PIN) {
                0
            } else {
                topOverlayHeightPx + with(density) { COMPASS_TOP_GAP.roundToPx() }
            },
            followLocation = followingLocation,
            onStopFollowing = viewModel::stopFollowingLocation,
            modifier = Modifier.fillMaxSize()
        )

        if (addPoiStep == AddPoiStep.PLACING_PIN) {
            AddPoiPinOverlay(
                onCancel = viewModel::cancelAddPoi,
                onUseLocation = viewModel::confirmAddPoiLocation,
                searchState = addressSearchState,
                onSearchQueryChange = viewModel::updateAddressSearchQuery,
                onSearch = viewModel::searchAddress,
                onSelectResult = viewModel::selectAddressResult,
                useLocationEnabled = addPoiCenter != null
            )
        } else {
            if (state.pois.isEmpty() && state.allPois.isEmpty()) {
                Text(
                    text = stringResource(R.string.map_empty),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { topOverlayHeightPx = it.size.height }
                    // The map runs under the transparent status bar; its controls don't. Measured
                    // above this padding, so the compass also clears the status bar.
                    .statusBarsPadding()
            ) {
                // Tapping the oval body (not the ✕) opens the full-screen SearchOverlay with the
                // committed query preloaded; the overlay draws this same bar in the same spot.
                SearchOvalBar(
                    query = state.committedSearchQuery.orEmpty(),
                    leadingIcon = Icons.Filled.Search,
                    leadingContentDescription = stringResource(R.string.map_search_icon),
                    onClear = viewModel::clearSearch,
                    onBarClick = { onOpenSearch(state.committedSearchQuery.orEmpty()) }
                )
                PoiCategoryChipRow(
                    categories = state.categories,
                    activeCategory = state.activeCategory,
                    allCategories = state.allCategories,
                    onSelectCategory = viewModel::selectCategory,
                    onToggleAll = viewModel::toggleAllCategories
                )
                Spacer(modifier = Modifier.height(CHIP_ROW_GAP))
                VerdictFilterChipRow(
                    favoritesOnly = state.favoritesOnly,
                    wantToGoOnly = state.wantToGoOnly,
                    onToggleFavorites = viewModel::toggleFavoritesOnly,
                    onToggleWantToGo = viewModel::toggleWantToGoOnly
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Locate-me sits lowest (closest to the thumb), "Add place" above it (#59).
                FloatingActionButton(onClick = { viewModel.startAddPoi() }) {
                    Icon(Icons.Filled.AddLocationAlt, contentDescription = stringResource(R.string.map_add_poi_button))
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

        SnackbarHost(hostState = deleteSnackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (addPoiStep == AddPoiStep.FORM) {
        val center = addPoiCenter
        if (center != null) {
            PlaceFormHost(
                target = editTarget,
                center = center,
                prefillAddress = pendingAddressPrefill,
                viewModel = viewModel,
                onRemove = { id, name ->
                    pendingRemovalName = name
                    viewModel.requestRemovePlace(id)
                }
            )
        }
    }

    selectedPoi?.let { selected ->
        // The live entry, so values saved in the edit form a moment ago show up (#73). Falls
        // back to the selection itself until a just-added place reaches the list.
        val poi = state.allPois.firstOrNull { it.id == selected.poi.id } ?: selected.poi
        val item = selected.copy(poi = poi)
        // Computed here (composable scope) rather than inside onCheckIn's click lambda:
        // displayName falls back to a stringResource, which can't be called outside composition.
        val displayName = poi.displayName()
        PlaceDetailSheet(
            item = item,
            verdict = state.verdicts[poi.id]?.value,
            onVerdictChange = { viewModel.setVerdict(poi, it) },
            lastVisit = selectedPoiLastVisit,
            onEdit = { viewModel.startEditPlace(poi) },
            onCheckIn = {
                onSheetCheckIn(poi, displayName) { viewModel.selectPoi(null) }
            },
            onDismiss = { viewModel.selectPoi(null) }
        )
    }
}

/**
 * Hosts [PlaceEditForm] for whichever place [target] is (#73): null adds a new custom place at
 * [center] (seeded from the address search's [prefillAddress], #46), a custom place rewrites its
 * row, an OSM place saves its differences from OSM as an override. Keyed by the place id, so one
 * place's unsaved form state never carries over into another's.
 */
@Composable
private fun PlaceFormHost(
    target: PlaceEditTarget?,
    center: LatLon,
    prefillAddress: GeocodeResult?,
    viewModel: MapViewModel,
    onRemove: (id: String, name: String) -> Unit
) {
    key(target?.id) {
        when (target) {
            null -> PlaceEditForm(
                isNew = true,
                initial = PlaceFormValues(
                    street = prefillAddress?.street.orEmpty(),
                    housenumber = prefillAddress?.housenumber.orEmpty(),
                    postcode = prefillAddress?.postcode.orEmpty(),
                    city = prefillAddress?.city.orEmpty()
                ),
                centerLatLon = center,
                onClose = viewModel::closePlaceForm,
                onSave = { viewModel.saveCustomPoi(it.toCustomPoi(initial = null, center)) },
                findNearbyDuplicate = { category -> viewModel.findNearbyDuplicate(center.lat, center.lon, category) }
            )
            is PlaceEditTarget.Custom -> {
                val name = target.place.toPoi().displayName()
                PlaceEditForm(
                    isNew = false,
                    initial = target.place.toFormValues(),
                    centerLatLon = center,
                    onClose = viewModel::closePlaceForm,
                    onSave = { viewModel.saveCustomPoi(it.toCustomPoi(initial = target.place, center)) },
                    removal = PlaceRemoval.DELETE,
                    placeName = name,
                    onRemove = { onRemove(target.id, name) },
                    findNearbyDuplicate = { category ->
                        viewModel.findNearbyDuplicate(center.lat, center.lon, category, excludeId = target.id)
                    }
                )
            }
            is PlaceEditTarget.Osm -> {
                val name = target.effective.displayName()
                PlaceEditForm(
                    isNew = false,
                    initial = target.effective.toFormValues(),
                    centerLatLon = center,
                    onClose = viewModel::closePlaceForm,
                    onSave = { values ->
                        viewModel.saveOverride(
                            target.id,
                            buildPoiOverride(
                                osm = target.osm,
                                name = values.name,
                                category = values.category ?: target.osm.category,
                                street = values.street,
                                housenumber = values.housenumber,
                                postcode = values.postcode,
                                city = values.city,
                                openingHours = values.openingHours
                            )
                        )
                    },
                    osmValues = target.osm.toFormValues(),
                    removal = PlaceRemoval.HIDE,
                    placeName = name,
                    onRemove = { onRemove(target.id, name) }
                )
            }
        }
    }
}

/** The custom place a form save resolves to; [initial] keeps an edited place's id (#47). */
private fun PlaceFormValues.toCustomPoi(initial: CustomPoi?, center: LatLon): CustomPoi =
    buildCustomPoiCandidate(
        initial = initial,
        category = category ?: initial?.category.orEmpty(),
        lat = center.lat,
        lon = center.lon,
        name = name,
        openingHours = openingHours.ifBlank { null },
        street = street.ifBlank { null },
        housenumber = housenumber.ifBlank { null },
        postcode = postcode.ifBlank { null },
        city = city.ifBlank { null }
    )

/** Visible gap between the chip rows (#77), the same as between the search bar and the first
 *  row (the bar's own bottom padding) and between two chips. */
private val CHIP_ROW_GAP = 8.dp

/** A chip's 48 dp touch target is 8 dp taller than the visible 32 dp chip at top and bottom. */
private val CHIP_TOUCH_TARGET_INSET = 8.dp

/** LazyRow key of the "All" chip; categories never contain a space. */
private const val ALL_CHIP_KEY = "all categories"

/**
 * Lays a chip row out [CHIP_TOUCH_TARGET_INSET] shorter at top and bottom, so neighbours sit
 * against the visible chips while the full 48 dp touch targets stay, overlapping the gaps (#77).
 */
private fun Modifier.trimChipTouchTargetInset(): Modifier = layout { measurable, constraints ->
    val inset = CHIP_TOUCH_TARGET_INSET.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - 2 * inset).coerceAtLeast(0)) {
        placeable.place(0, -inset)
    }
}

/** Gap between the search bar/category chip row and the map's native compass below them. */
private val COMPASS_TOP_GAP = 8.dp

/**
 * Single-select category chip row, floated over the map's top edge, below the search oval — now
 * always visible (no longer hidden while a search dropdown shows; that dropdown no longer lives
 * on this screen at all, see [SearchOverlay]). Starts with "All" (#77), every category in the DB,
 * then the curated [categories]. Chip styling lives in [MapFilterChip], matching Velometrics'
 * chip row.
 */
@Composable
private fun PoiCategoryChipRow(
    categories: List<String>,
    activeCategory: String?,
    allCategories: Boolean,
    onSelectCategory: (String) -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.trimChipTouchTargetInset(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        item(key = ALL_CHIP_KEY) {
            MapFilterChip(
                selected = allCategories,
                onClick = onToggleAll,
                label = stringResource(R.string.map_all_filter),
                icon = Icons.Filled.Apps
            )
        }
        items(categories, key = { it }) { category ->
            MapFilterChip(
                selected = category == activeCategory,
                onClick = { onSelectCategory(category) },
                label = categoryDisplayName(category),
                icon = categoryIcon(category)
            )
        }
    }
}

/**
 * Second chip row under [PoiCategoryChipRow] (#59, replacing the old Layers FAB panel, following
 * Velometrics' map): Favorites / Want to go. Same look as the category chips; the three filters
 * stay mutually exclusive (see MapViewModel.selectCategory/toggleFavoritesOnly/toggleWantToGoOnly).
 */
@Composable
private fun VerdictFilterChipRow(
    favoritesOnly: Boolean,
    wantToGoOnly: Boolean,
    onToggleFavorites: () -> Unit,
    onToggleWantToGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .trimChipTouchTargetInset()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapFilterChip(
            selected = favoritesOnly,
            onClick = onToggleFavorites,
            label = stringResource(R.string.map_favorites_filter),
            icon = Icons.Filled.Favorite
        )
        MapFilterChip(
            selected = wantToGoOnly,
            onClick = onToggleWantToGo,
            label = stringResource(R.string.map_want_to_go_filter),
            icon = Icons.Filled.Bookmark
        )
    }
}

/**
 * Every chip floated over the map shares one color (no per-category coding, matching the map
 * markers' own move away from that — see [markerForegroundColor]/[markerBackgroundColor]):
 * outlined in the foreground color when unselected, filled with it when selected, with icon+label
 * flipping to the background color for contrast — the same relationship the marker circle/icon
 * pair already has. Icon and label sizing come from [FilterChip]'s own defaults.
 */
@Composable
private fun MapFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector
) {
    val darkTheme = LocalDarkTheme.current
    val foreground = markerForegroundColor(darkTheme)
    val background = markerBackgroundColor(darkTheme)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
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
