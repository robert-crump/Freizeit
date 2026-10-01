package com.example.freizeit.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.CustomPoiDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.PoiOverrideDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.dao.hide
import com.example.freizeit.data.dao.lastVisitLabel
import com.example.freizeit.data.dao.setOverride
import com.example.freizeit.data.dao.setVerdict
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.PoiOverride
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.entity.applyOverrides
import com.example.freizeit.data.entity.isCustomPoiId
import com.example.freizeit.data.entity.toPoi
import com.example.freizeit.data.geocoding.NominatimClient
import com.example.freizeit.data.repository.LocationRepository
import com.example.freizeit.data.repository.findPoiById
import com.example.freizeit.data.repository.findRawPoiById
import com.example.freizeit.domain.geocoding.GeocodeResult
import com.example.freizeit.ui.common.PRIMARY_MAP_CATEGORIES
import com.example.freizeit.util.CustomPoiProximity
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PoiWithDistance(val poi: Poi, val distanceMeters: Double?)

/** Steps of the add-custom-POI flow (issue #45), driven by the Map screen's "+" FAB.
 *  [PLACING_PIN]: the map itself is shown with a fixed center crosshair; panning updates
 *  [MapViewModel.addPoiCenter]. [FORM]: the place edit form, seeded from wherever the pin landed
 *  — also used, without a pin step, to edit an existing place (#73, see [PlaceEditTarget]). */
enum class AddPoiStep { NONE, PLACING_PIN, FORM }

/** The existing place the edit form (#73) is open for; null while adding a new one. */
sealed interface PlaceEditTarget {
    val id: String

    /** A user-added place: saving rewrites its `custom_poi` row. */
    data class Custom(val place: CustomPoi) : PlaceEditTarget {
        override val id: String get() = place.id
    }

    /** An OSM place: saving stores the differences from [osm] (its raw, un-overridden values)
     *  as a `poi_override`; [effective] is what the form starts from. */
    data class Osm(val osm: Poi, val effective: Poi) : PlaceEditTarget {
        override val id: String get() = osm.id
    }
}

/** State for issue #46's address search field, shown alongside pin-drop during
 *  [AddPoiStep.PLACING_PIN]. [searched] distinguishes "never submitted yet" from "submitted and
 *  got zero results" so the empty-state message only shows after an actual search. [error] is
 *  network/parse failure (see [NominatimClient.search]'s null-on-failure contract) — a separate
 *  flag from an empty [results] list, which is a genuine no-match search. */
data class AddressSearchState(
    val query: String = "",
    val results: List<GeocodeResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: Boolean = false,
    val searched: Boolean = false
)

data class MapUiState(
    val pois: List<PoiWithDistance> = emptyList(),
    val allPois: List<Poi> = emptyList(),
    val categories: List<String> = emptyList(),
    val activeCategory: String? = null,
    val allCategories: Boolean = false,
    val location: LatLon? = null,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val favoritesOnly: Boolean = false,
    val wantToGoOnly: Boolean = false,
    val committedSearchQuery: String? = null
)

/**
 * The Map's filter state: the single-select category chip (or "All", #77), the Favorites / Want
 * to go rows and the committed search. At most one is ever active — each setter below that turns one on starts
 * from a blank [MapFilters], turning one off only clears that one. Pulled out as a pure value so
 * the exclusivity rules and [MapViewModel.openPlace]'s reset (#63) are unit-testable.
 */
data class MapFilters(
    val activeCategory: String? = null,
    val allCategories: Boolean = false,
    val favoritesOnly: Boolean = false,
    val wantToGoOnly: Boolean = false,
    val searchQuery: String? = null
) {
    /** Tapping the already-active category clears it (shows nothing), a different one switches. */
    fun toggleCategory(category: String): MapFilters =
        if (activeCategory == category) copy(activeCategory = null) else MapFilters(activeCategory = category)

    /** The "All" chip (#77): every category at once, in the same single-select group. */
    fun toggleAllCategories(): MapFilters =
        if (allCategories) copy(allCategories = false) else MapFilters(allCategories = true)

    fun toggleFavoritesOnly(): MapFilters =
        if (favoritesOnly) copy(favoritesOnly = false) else MapFilters(favoritesOnly = true)

    fun toggleWantToGoOnly(): MapFilters =
        if (wantToGoOnly) copy(wantToGoOnly = false) else MapFilters(wantToGoOnly = true)

    /** A blank query only clears the search, leaving any other (already inactive) filter as is. */
    fun commitSearch(query: String): MapFilters {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) copy(searchQuery = null) else MapFilters(searchQuery = trimmed)
    }

    fun clearSearch(): MapFilters = copy(searchQuery = null)

    /** Whether [poi] (with verdict [verdictValue], null for none) passes these filters — the same
     *  rules as [filterAndSort]. */
    fun shows(poi: Poi, verdictValue: String?): Boolean = when {
        !searchQuery.isNullOrBlank() -> poi.name?.let { matchesSearch(it, searchQuery) } == true
        favoritesOnly -> verdictValue == Verdict.VALUE_FAVORITE
        wantToGoOnly -> verdictValue == Verdict.VALUE_WANT_TO_GO
        allCategories -> true
        activeCategory != null -> poi.category == activeCategory
        else -> false
    }

    /** The filters once a detail sheet opens for [poi] (#76 follow-up): unchanged if its marker
     *  already shows, otherwise the "All" chip, so the marker under the sheet is visible. */
    fun revealing(poi: Poi, verdictValue: String?): MapFilters =
        if (shows(poi, verdictValue)) this else MapFilters(allCategories = true)
}

/** Start index of every "word" in [text] — a run of letters/digits preceded by either the
 *  string start or a non-letter/digit character. Used by [matchesSearch] to anchor prefix
 *  matches at word boundaries instead of matching anywhere inside a word. */
private fun wordStartIndices(text: String): List<Int> =
    text.indices.filter { i ->
        text[i].isLetterOrDigit() && (i == 0 || !text[i - 1].isLetterOrDigit())
    }

/**
 * True if [query] is a prefix (case-insensitive) of [name] starting at some word boundary —
 * e.g. "Len" or "Caf" both match "Leni's Café" (first and second word), "hidden gem" matches
 * "Our Hidden Gem" (anchored at "Hidden"), but "len" does NOT match "Bottleneck" even though it
 * contains the substring "len" mid-word. A plain `.contains()` would wrongly match the latter.
 */
/** Categories offered in the map's chip row: [PRIMARY_MAP_CATEGORIES] restricted to whichever
 * of those actually have a POI nearby, in that fixed curated order — not every category present
 * in [pois], and not alphabetical. Search (see [matchesSearch]) still covers every category;
 * this only trims what's offered as a one-tap filter. */
fun visibleCategories(pois: List<Poi>): List<String> {
    val present = pois.mapTo(HashSet()) { it.category }
    return PRIMARY_MAP_CATEGORIES.filter { it in present }
}

/** Drops [pendingRemovalId] (if any) from [pois] — pulled out as a pure function, mirroring
 *  [filterAndSort]/[visibleCategories] above, so the "the marker disappears immediately, before
 *  the place is actually deleted/hidden" half of the optimistic remove-with-undo (#47, #73) is
 *  unit-testable without a Room/ViewModel round trip. The rows survive in the DB until
 *  [MapViewModel.commitPendingRemoval] runs; this is what hides the place from every downstream
 *  consumer (map markers, search, the merged POI list) in the meantime. */
fun excludePendingRemoval(pois: List<Poi>, pendingRemovalId: String?): List<Poi> =
    if (pendingRemovalId == null) pois else pois.filterNot { it.id == pendingRemovalId }

private fun matchesSearch(name: String, query: String): Boolean {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return false
    return wordStartIndices(name).any { start ->
        name.length - start >= trimmedQuery.length &&
            name.regionMatches(start, trimmedQuery, 0, trimmedQuery.length, ignoreCase = true)
    }
}

/**
 * Pure filter+sort so the semantics are unit-testable. [pois] carry their effective values
 * (overrides applied, #73), so search and the category chip see the user's edits. Exactly one
 * filter is ever active, in priority order: a non-blank [searchQuery] matches names via
 * [matchesSearch] (word-boundary prefix, not a plain substring) across all categories;
 * otherwise [verdictIds] (non-null) restricts to whichever single verdict bucket is active —
 * favorites-only or want-to-go-only, the caller decides which set to pass in, never both at
 * once (#31); otherwise [allCategories] (the "All" chip, #77) keeps every place; otherwise a
 * non-null [activeCategory] restricts to pois of that one category —
 * the chip row is single-select (replaces #33's multi-select "All POIs" mode); with none of
 * the above active, nothing matches. With a location, sorts nearest first, otherwise by name
 * (unnamed places last).
 */
fun filterAndSort(
    pois: List<Poi>,
    activeCategory: String?,
    location: LatLon?,
    verdictIds: Set<String>? = null,
    searchQuery: String? = null,
    allCategories: Boolean = false
): List<PoiWithDistance> {
    val filtered = pois.filter {
        when {
            !searchQuery.isNullOrBlank() -> {
                val name = it.name
                name != null && matchesSearch(name, searchQuery)
            }
            verdictIds != null -> it.id in verdictIds
            allCategories -> true
            activeCategory != null -> it.category == activeCategory
            else -> false
        }
    }
    return if (location != null) {
        filtered
            .map {
                PoiWithDistance(
                    it,
                    GeoDistance.metersBetween(location.lat, location.lon, it.lat, it.lon)
                )
            }
            .sortedBy { it.distanceMeters }
    } else {
        filtered
            .sortedWith(compareBy(nullsLast()) { it.name?.lowercase() })
            .map { PoiWithDistance(it, null) }
    }
}

/** A fix at least this accurate ends follow-GPS (#77). */
const val FINE_FIX_ACCURACY_METERS = 50f

/** Whether centering on [location] ends follow-GPS (#77); a fix without an accuracy counts as
 *  coarse, so the map keeps following until a fix proves it's fine. */
fun isFineFix(location: LatLon): Boolean =
    location.accuracyMeters?.let { it <= FINE_FIX_ACCURACY_METERS } ?: false

class MapViewModel(
    private val locationRepository: LocationRepository,
    private val poiDao: PoiDao,
    private val verdictDao: VerdictDao,
    private val poiOverrideDao: PoiOverrideDao,
    private val visitDao: VisitDao,
    private val customPoiDao: CustomPoiDao
) : ViewModel() {

    // Category chip, Favorites / Want to go and committed search — mutually exclusive, see
    // MapFilters.
    private val filters = MutableStateFlow(MapFilters())

    private val _selectedPoi = MutableStateFlow<PoiWithDistance?>(null)
    val selectedPoi: StateFlow<PoiWithDistance?> = _selectedPoi

    /** "Last visit" label for whichever POI [selectedPoi] currently holds — recomputed on
     *  every [selectPoi] call rather than kept live, since the sheet is a one-shot snapshot
     *  view, not something that needs to update while sitting open. */
    private val _selectedPoiLastVisit = MutableStateFlow<String?>(null)
    val selectedPoiLastVisit: StateFlow<String?> = _selectedPoiLastVisit

    // Shared with the full-screen search overlay (hoisted alongside this ViewModel in
    // FreizeitApp) so a row tap there can drive the Map screen's camera jump.
    private val _focusTarget = MutableStateFlow<LatLon?>(null)
    val focusTarget: StateFlow<LatLon?> = _focusTarget
    private val _focusRequest = MutableStateFlow(0)
    val focusRequest: StateFlow<Int> = _focusRequest

    // Follow GPS (#77): true from launch until the camera has centered on a fine fix (see
    // isFineFix) or the user/app moved it elsewhere — see stopFollowingLocation's callers.
    private val _followingLocation = MutableStateFlow(true)
    val followingLocation: StateFlow<Boolean> = _followingLocation

    // Add-custom-POI flow (#45): NONE until the "+" FAB is tapped, PLACING_PIN while the map's
    // center crosshair tracks the pan, FORM once the location is confirmed.
    private val _addPoiStep = MutableStateFlow(AddPoiStep.NONE)
    val addPoiStep: StateFlow<AddPoiStep> = _addPoiStep
    private val _addPoiCenter = MutableStateFlow<LatLon?>(null)
    val addPoiCenter: StateFlow<LatLon?> = _addPoiCenter

    // Address search (#46), live only during PLACING_PIN. Selecting a result both re-centers the
    // pin (via the existing focusOn/updateAddPoiCenter camera-idle path — no separate "move the
    // crosshair" mechanism needed) and stashes its structured address in pendingAddressPrefill for
    // PlaceEditForm to seed street/housenumber/postcode/city from once the flow reaches FORM.
    private val _addressSearchState = MutableStateFlow(AddressSearchState())
    val addressSearchState: StateFlow<AddressSearchState> = _addressSearchState
    private val _pendingAddressPrefill = MutableStateFlow<GeocodeResult?>(null)
    val pendingAddressPrefill: StateFlow<GeocodeResult?> = _pendingAddressPrefill

    // The existing place the FORM step is editing (#47, #73); null while adding a new one. Set by
    // startEditPlace, cleared alongside addPoiStep/addPoiCenter by cancelAddPoi.
    private val _editTarget = MutableStateFlow<PlaceEditTarget?>(null)
    val editTarget: StateFlow<PlaceEditTarget?> = _editTarget

    // Optimistic remove-with-undo (#47, #73): set by requestRemovePlace, filtered out of
    // poisAndVerdicts below (via excludePendingRemoval) so the marker vanishes immediately —
    // nothing is deleted or hidden until commitPendingRemoval actually runs, so undoRemovePlace
    // needs only clear this back to null, nothing to restore. At most one pending removal at a
    // time; a second request commits the first.
    private val _pendingRemovalId = MutableStateFlow<String?>(null)
    val pendingRemovalId: StateFlow<String?> = _pendingRemovalId

    private data class PoisAndVerdicts(
        val pois: List<Poi>,
        val verdicts: Map<String, Verdict>
    )

    // custom_poi rows are merged in here (projected to Poi via toPoi()) and every place carries
    // its effective values (overrides applied, hidden ones dropped), so every downstream
    // consumer — filterAndSort, PoiMap, the category chip row, search — sees one POI list and
    // needs no separate custom-vs-OSM or override branch (#45, #73).
    private val poisAndVerdicts = combine(
        poiDao.observeAll(),
        customPoiDao.observeAll(),
        verdictDao.observeAll(),
        poiOverrideDao.observeAll(),
        _pendingRemovalId
    ) { pois, customPois, verdicts, overrides, pendingRemovalId ->
        val all = pois + customPois.map { it.toPoi() }
        PoisAndVerdicts(
            pois = applyOverrides(excludePendingRemoval(all, pendingRemovalId), overrides.associateBy { it.placeId }),
            verdicts = verdicts.associateBy { it.placeId }
        )
    }

    val uiState: StateFlow<MapUiState> = combine(
        poisAndVerdicts,
        filters,
        locationRepository.location
    ) { poisAndVerdicts, filter, loc ->
        val (pois, verdictMap) = poisAndVerdicts
        val (active, all, favOnly, wantToGo, query) = filter
        val categories = visibleCategories(pois)
        val verdictIds = when {
            favOnly -> verdictMap.values.filter { it.value == Verdict.VALUE_FAVORITE }.map { it.placeId }.toSet()
            wantToGo -> verdictMap.values.filter { it.value == Verdict.VALUE_WANT_TO_GO }.map { it.placeId }.toSet()
            else -> null
        }
        MapUiState(
            pois = filterAndSort(pois, active, loc, verdictIds, query, allCategories = all),
            allPois = pois,
            categories = categories,
            activeCategory = active,
            allCategories = all,
            location = loc,
            verdicts = verdictMap,
            favoritesOnly = favOnly,
            wantToGoOnly = wantToGo,
            committedSearchQuery = query
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        refreshLocation()
    }

    fun selectCategory(category: String) {
        filters.value = filters.value.toggleCategory(category)
    }

    fun toggleAllCategories() {
        filters.value = filters.value.toggleAllCategories()
    }

    fun toggleFavoritesOnly() {
        filters.value = filters.value.toggleFavoritesOnly()
    }

    fun toggleWantToGoOnly() {
        filters.value = filters.value.toggleWantToGoOnly()
    }

    /** Commits a search overlay query to the map. Called both by the overlay's keyboard Search
     *  action and by tapping one of its rows (which also focuses/selects that POI). */
    fun commitSearch(query: String) {
        filters.value = filters.value.commitSearch(query)
    }

    fun clearSearch() {
        filters.value = filters.value.clearSearch()
    }

    /**
     * "Open place on Map" (#63), shared by the check-in history and MainActivity's
     * [com.example.freizeit.ui.MainActivity.EXTRA_OPEN_ON_MAP_POI_ID] (for the widget, #66):
     * turns the "All" chip on (via [selectPoi]) so the marker is guaranteed to show, cancels a half-done add-place flow
     * that would cover the map, jumps the camera to the place and opens its detail sheet. Works
     * for OSM and custom places alike (via [findPoiById]). Returns false, changing nothing, if
     * the place no longer exists or is hidden — including one whose removal is still pending Undo.
     */
    suspend fun openPlace(poiId: String): Boolean {
        val poi = withContext(Dispatchers.IO) { findPoiById(poiDao, customPoiDao, poiOverrideDao, poiId) }
        if (poi == null || poi.id == _pendingRemovalId.value) return false
        filters.value = MapFilters(allCategories = true)
        cancelAddPoi()
        val loc = locationRepository.location.value
        val distanceMeters = loc?.let { GeoDistance.metersBetween(it.lat, it.lon, poi.lat, poi.lon) }
        focusOn(LatLon(poi.lat, poi.lon))
        selectPoi(PoiWithDistance(poi, distanceMeters))
        return true
    }

    /** Jumps the map camera to [latLon] — bumps [focusRequest] so [PoiMap]'s LaunchedEffect
     *  re-fires even if the same POI is focused twice in a row. */
    fun focusOn(latLon: LatLon) {
        stopFollowingLocation()
        _focusTarget.value = latLon
        _focusRequest.value += 1
    }

    fun refreshLocation() {
        viewModelScope.launch { locationRepository.refreshOnce() }
    }

    /** Arms/disarms the live location stream (#40) — only while the Map screen is actually
     *  on-screen, gated by [MapScreen]'s own composable lifecycle, since this is the one surface
     *  where a live position matters enough to justify an active GPS stream. */
    fun startContinuousLocation() = locationRepository.startContinuous()

    fun stopContinuousLocation() = locationRepository.stopContinuous()

    /** Ends follow-GPS for this launch: the camera reached a fine fix, the user panned/zoomed,
     *  or a place was opened or the pin drop started (#77). */
    fun stopFollowingLocation() {
        _followingLocation.value = false
    }

    /** Opens (non-null) or closes the detail sheet. Opening one for a place the current filters
     *  hide (a just-added place, say) switches to the "All" chip so its marker shows. */
    fun selectPoi(poi: PoiWithDistance?) {
        if (poi != null) {
            stopFollowingLocation()
            filters.value = filters.value.revealing(poi.poi, uiState.value.verdicts[poi.poi.id]?.value)
        }
        _selectedPoi.value = poi
        _selectedPoiLastVisit.value = null
        if (poi != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val label = visitDao.lastVisitLabel(poi.poi.id)
                if (_selectedPoi.value?.poi?.id == poi.poi.id) {
                    _selectedPoiLastVisit.value = label
                }
            }
        }
    }

    fun setVerdict(poi: Poi, value: String?) {
        viewModelScope.launch(Dispatchers.IO) { verdictDao.setVerdict(poi, value) }
    }

    /** Opens the add-POI flow's pin-drop step (the "+" FAB). */
    fun startAddPoi() {
        stopFollowingLocation()
        _addPoiStep.value = AddPoiStep.PLACING_PIN
        _addPoiCenter.value = uiState.value.location
    }

    /** [PoiMap]'s camera-idle callback while [addPoiStep] is [AddPoiStep.PLACING_PIN] — ignored
     *  once the flow has moved past pin-placement, so a camera settle firing after the form is
     *  already showing can't retroactively move the pin. */
    fun updateAddPoiCenter(latLon: LatLon) {
        if (_addPoiStep.value == AddPoiStep.PLACING_PIN) {
            _addPoiCenter.value = latLon
        }
    }

    /** "Use this location" — advances from pin-placement to the form. No-op if the camera never
     *  reported a center (shouldn't happen once the map has loaded, but guards a race on entry). */
    fun confirmAddPoiLocation() {
        if (_addPoiCenter.value != null) {
            _addPoiStep.value = AddPoiStep.FORM
        }
    }

    fun cancelAddPoi() {
        _addPoiStep.value = AddPoiStep.NONE
        _addPoiCenter.value = null
        _editTarget.value = null
        _addressSearchState.value = AddressSearchState()
        _pendingAddressPrefill.value = null
    }

    /** The form's ✕ (#73): an edit returns to the map with the place's sheet re-opened, an add
     *  just cancels the flow. */
    fun closePlaceForm() {
        val editedId = _editTarget.value?.id
        cancelAddPoi()
        if (editedId != null) reopenSheet(editedId)
    }

    /** Text field state only — no network call, so typing never trips Nominatim's 1 req/sec
     *  policy. Also clears any previous results/error, since they belong to the old query. */
    fun updateAddressSearchQuery(query: String) {
        _addressSearchState.value = AddressSearchState(query = query)
    }

    /** Explicit-submit search (the "Search" button/keyboard action) — the only way this ever hits
     *  the network, per issue #46's spec. Guards against a stale response landing after the user
     *  has already changed the query (mirrors [selectPoi]'s own late-response guard). */
    fun searchAddress() {
        val query = _addressSearchState.value.query.trim()
        if (query.isBlank()) return
        _addressSearchState.value = _addressSearchState.value.copy(isSearching = true, error = false)
        viewModelScope.launch(Dispatchers.IO) {
            val results = NominatimClient.search(query)
            if (_addressSearchState.value.query.trim() != query) return@launch
            _addressSearchState.value = AddressSearchState(
                query = query,
                results = results.orEmpty(),
                error = results == null,
                searched = true
            )
        }
    }

    /** A search result was tapped: re-centers the pin there (reusing the generic camera-jump path
     *  — [updateAddPoiCenter] picks up the resulting camera-idle callback same as a manual pan)
     *  and stashes the structured address for the FORM step to prefill from. Clears the search
     *  UI itself, same as closing a completed search. */
    fun selectAddressResult(result: GeocodeResult) {
        _pendingAddressPrefill.value = result
        _addressSearchState.value = AddressSearchState()
        focusOn(LatLon(result.lat, result.lon))
    }

    /** The nearest existing same-category place within [CustomPoiProximity]'s threshold, if any —
     *  the form calls this on Save to decide whether to show the "add anyway?" warning.
     *  [excludeId] skips a custom POI being edited in place (#47) — otherwise it would always
     *  match itself at distance zero. */
    fun findNearbyDuplicate(lat: Double, lon: Double, category: String, excludeId: String? = null): Poi? =
        CustomPoiProximity.findNearbyMatch(lat, lon, category, uiState.value.allPois, excludeId = excludeId)

    /** The form's ✓ for a custom place, new or edited: saves it and returns to the map with its
     *  sheet open (#73). */
    fun saveCustomPoi(customPoi: CustomPoi) {
        viewModelScope.launch(Dispatchers.IO) { customPoiDao.upsert(customPoi) }
        cancelAddPoi()
        showSheet(customPoi.toPoi())
    }

    /** The form's ✓ for an OSM place (#73): stores [override] (null deletes the row, so every
     *  field follows OSM again) and returns to the map with the place's sheet re-opened. */
    fun saveOverride(placeId: String, override: PoiOverride?) {
        viewModelScope.launch(Dispatchers.IO) { poiOverrideDao.setOverride(placeId, override) }
        cancelAddPoi()
        reopenSheet(placeId)
    }

    /** The sheet's pencil (#73): opens the edit form for [poi] (its effective values), OSM or
     *  custom. Skips pin-placement and keeps the place's coordinates: location isn't editable.
     *  Reads the stored row first, since an OSM place's form diffs against its raw OSM values. */
    fun startEditPlace(poi: Poi) {
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) {
                if (isCustomPoiId(poi.id)) {
                    customPoiDao.getById(poi.id)?.let { PlaceEditTarget.Custom(it) }
                } else {
                    findRawPoiById(poiDao, customPoiDao, poi.id)?.let { PlaceEditTarget.Osm(it, poi) }
                }
            } ?: return@launch
            _editTarget.value = target
            _addPoiCenter.value = LatLon(poi.lat, poi.lon)
            _addPoiStep.value = AddPoiStep.FORM
            selectPoi(null)
        }
    }

    private fun showSheet(poi: Poi) {
        val loc = uiState.value.location
        selectPoi(PoiWithDistance(poi, loc?.let { GeoDistance.metersBetween(it.lat, it.lon, poi.lat, poi.lon) }))
    }

    /** Re-opens [placeId]'s sheet. [MapScreen] renders the sheet from the live POI list, so values
     *  saved a moment ago show up once their write lands. */
    private fun reopenSheet(placeId: String) {
        uiState.value.allPois.firstOrNull { it.id == placeId }?.let(::showSheet)
    }

    /** Optimistic delete (custom place, #47) or hide (OSM place, #73): [id] disappears from the
     *  map/search immediately (see [excludePendingRemoval] above) but nothing is written until
     *  [commitPendingRemoval] actually runs — either the Snackbar timing out/being dismissed, or
     *  [MapScreen] committing on dispose if the user navigates away first. A second request
     *  while one is already pending commits the first rather than dropping it silently. Also
     *  closes the edit form the removal was confirmed in. */
    fun requestRemovePlace(id: String) {
        if (_pendingRemovalId.value != null) commitPendingRemoval()
        _pendingRemovalId.value = id
        cancelAddPoi()
        selectPoi(null)
    }

    /** Reverses [requestRemovePlace] — since nothing was written yet, this is just clearing the
     *  pending id, which makes [poisAndVerdicts] show the place again. */
    fun undoRemovePlace() {
        _pendingRemovalId.value = null
    }

    /** Actually removes whatever [requestRemovePlace] left pending. A custom place goes together
     *  with its Verdict and every Visit logged against it, re-keying nothing (unlike #49's
     *  reimport-time merge). An OSM place is hidden and loses its Verdict, but its Visits stay in
     *  the history (#73). No-op if nothing is pending (safe to call unconditionally from
     *  [MapScreen]'s onDispose). Clears the pending id only after the writes land, so a DAO Flow
     *  emission racing this can't briefly show the place again with nothing pending. */
    fun commitPendingRemoval() {
        val id = _pendingRemovalId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            verdictDao.delete(id)
            if (isCustomPoiId(id)) {
                visitDao.deleteByPlaceId(id)
                customPoiDao.delete(id)
            } else {
                poiOverrideDao.hide(id)
            }
            if (_pendingRemovalId.value == id) _pendingRemovalId.value = null
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                MapViewModel(
                    app.container.locationRepository,
                    app.container.database.poiDao(),
                    app.container.database.verdictDao(),
                    app.container.database.poiOverrideDao(),
                    app.container.database.visitDao(),
                    app.container.database.customPoiDao()
                )
            }
        }
    }
}
