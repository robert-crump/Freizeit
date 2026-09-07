package com.example.freizeit.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.CustomPoiDao
import com.example.freizeit.data.dao.PoiCustomNameDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.dao.lastVisitLabel
import com.example.freizeit.data.dao.setCustomName
import com.example.freizeit.data.dao.setVerdict
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.entity.toCustomPoi
import com.example.freizeit.data.entity.toPoi
import com.example.freizeit.data.geocoding.NominatimClient
import com.example.freizeit.data.repository.LocationRepository
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

data class PoiWithDistance(val poi: Poi, val distanceMeters: Double?)

/** Steps of the add-custom-POI flow (issue #45), driven by the Map screen's "+" FAB.
 *  [PLACING_PIN]: the map itself is shown with a fixed center crosshair; panning updates
 *  [MapViewModel.addPoiCenter]. [FORM]: the name/category/address form, seeded from wherever the
 *  pin landed. */
enum class AddPoiStep { NONE, PLACING_PIN, FORM }

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
    val location: LatLon? = null,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val customNames: Map<String, String> = emptyMap(),
    val favoritesOnly: Boolean = false,
    val wantToGoOnly: Boolean = false,
    val committedSearchQuery: String? = null
)

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

/** Drops [pendingDeleteId] (if any) from [customPois] — pulled out as a pure function, mirroring
 *  [filterAndSort]/[visibleCategories] above, so the "the marker disappears immediately, before
 *  the underlying row is actually deleted" half of #47's optimistic-delete-with-undo is
 *  unit-testable without a Room/ViewModel round trip. The `custom_poi` row (and its Verdict/Visit)
 *  survive in the DB — and so in [CustomPoiDao.observeAll]'s emissions — until
 *  [MapViewModel.commitPendingDelete] runs; this is what hides it from every downstream consumer
 *  (map markers, search, the merged POI list) in the meantime. */
fun excludePendingDelete(customPois: List<CustomPoi>, pendingDeleteId: String?): List<CustomPoi> =
    if (pendingDeleteId == null) customPois else customPois.filterNot { it.id == pendingDeleteId }

private fun matchesSearch(name: String, query: String): Boolean {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return false
    return wordStartIndices(name).any { start ->
        name.length - start >= trimmedQuery.length &&
            name.regionMatches(start, trimmedQuery, 0, trimmedQuery.length, ignoreCase = true)
    }
}

/**
 * Pure filter+sort so the semantics are unit-testable. Exactly one filter is ever
 * active, in priority order: a non-blank [searchQuery] matches custom-or-OSM names via
 * [matchesSearch] (word-boundary prefix, not a plain substring) across all categories;
 * otherwise [verdictIds] (non-null) restricts to whichever single verdict bucket is active —
 * favorites-only or want-to-go-only, the caller decides which set to pass in, never both at
 * once (#31); otherwise a non-null [activeCategory] restricts to pois of that one category —
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
    customNames: Map<String, String> = emptyMap()
): List<PoiWithDistance> {
    val filtered = pois.filter {
        when {
            !searchQuery.isNullOrBlank() -> {
                val name = customNames[it.id] ?: it.name
                name != null && matchesSearch(name, searchQuery)
            }
            verdictIds != null -> it.id in verdictIds
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

class MapViewModel(
    private val locationRepository: LocationRepository,
    poiDao: PoiDao,
    private val verdictDao: VerdictDao,
    private val poiCustomNameDao: PoiCustomNameDao,
    private val visitDao: VisitDao,
    private val customPoiDao: CustomPoiDao
) : ViewModel() {

    // Single-select category chip (replaces #33's multi-select "All POIs" mode) — mutually
    // exclusive with favoritesOnly/wantToGoOnly/searchQuery, see selectCategory.
    private val activeCategory = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val wantToGoOnly = MutableStateFlow(false)
    private val committedSearchQuery = MutableStateFlow<String?>(null)

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

    // Add-custom-POI flow (#45): NONE until the "+" FAB is tapped, PLACING_PIN while the map's
    // center crosshair tracks the pan, FORM once the location is confirmed.
    private val _addPoiStep = MutableStateFlow(AddPoiStep.NONE)
    val addPoiStep: StateFlow<AddPoiStep> = _addPoiStep
    private val _addPoiCenter = MutableStateFlow<LatLon?>(null)
    val addPoiCenter: StateFlow<LatLon?> = _addPoiCenter

    // Address search (#46), live only during PLACING_PIN. Selecting a result both re-centers the
    // pin (via the existing focusOn/updateAddPoiCenter camera-idle path — no separate "move the
    // crosshair" mechanism needed) and stashes its structured address in pendingAddressPrefill for
    // AddPoiForm to seed street/housenumber/postcode/city from once the flow reaches FORM.
    private val _addressSearchState = MutableStateFlow(AddressSearchState())
    val addressSearchState: StateFlow<AddressSearchState> = _addressSearchState
    private val _pendingAddressPrefill = MutableStateFlow<GeocodeResult?>(null)
    val pendingAddressPrefill: StateFlow<GeocodeResult?> = _pendingAddressPrefill

    // Edit-in-place (#47): non-null routes the FORM step's save through an update (same id,
    // preserved via buildCandidate's initial?.id fallback) rather than newCustomPoiId(). Set by
    // startEditCustomPoi, cleared alongside addPoiStep/addPoiCenter by cancelAddPoi/saveCustomPoi.
    private val _editingCustomPoi = MutableStateFlow<CustomPoi?>(null)
    val editingCustomPoi: StateFlow<CustomPoi?> = _editingCustomPoi

    // Optimistic delete-with-undo (#47): set by requestDeleteCustomPoi, filtered out of
    // poisVerdictsAndNames below (via excludePendingDelete) so the marker vanishes immediately —
    // the underlying custom_poi/Verdict/Visit rows are untouched until commitPendingDelete
    // actually runs, so undoDeleteCustomPoi needs only clear this back to null, nothing to
    // restore. At most one pending delete at a time; a second request commits the first.
    private val _pendingDeleteCustomPoiId = MutableStateFlow<String?>(null)
    val pendingDeleteCustomPoiId: StateFlow<String?> = _pendingDeleteCustomPoiId

    private data class PoisVerdictsNames(
        val pois: List<Poi>,
        val verdicts: Map<String, Verdict>,
        val customNames: Map<String, String>
    )

    // custom_poi rows are merged in here (projected to Poi via toPoi()) so every downstream
    // consumer — filterAndSort, PoiMap, the category chip row, search — sees one POI list and
    // needs no separate custom-vs-OSM branch (#45).
    private val poisVerdictsAndNames = combine(
        poiDao.observeAll(),
        customPoiDao.observeAll(),
        verdictDao.observeAll(),
        poiCustomNameDao.observeAll(),
        _pendingDeleteCustomPoiId
    ) { pois, customPois, verdicts, customNames, pendingDeleteId ->
        PoisVerdictsNames(
            pois = pois + excludePendingDelete(customPois, pendingDeleteId).map { it.toPoi() },
            verdicts = verdicts.associateBy { it.placeId },
            customNames = customNames.associate { it.placeId to it.customName }
        )
    }

    private data class LayerSelection(
        val activeCategory: String?,
        val favoritesOnly: Boolean,
        val wantToGoOnly: Boolean
    )

    // Folded into one flow because the outer combine below is already at the stdlib 5-flow
    // arity limit (see #8's note) — activeCategory/favoritesOnly/wantToGoOnly are mutually
    // exclusive anyway, so they travel together.
    private val layerSelection = combine(
        activeCategory, favoritesOnly, wantToGoOnly
    ) { active, favOnly, wantToGo -> LayerSelection(active, favOnly, wantToGo) }

    val uiState: StateFlow<MapUiState> = combine(
        poisVerdictsAndNames,
        layerSelection,
        locationRepository.location,
        committedSearchQuery
    ) { poisVerdictsNames, layer, loc, query ->
        val (pois, verdictMap, customNames) = poisVerdictsNames
        val (active, favOnly, wantToGo) = layer
        val categories = visibleCategories(pois)
        val verdictIds = when {
            favOnly -> verdictMap.values.filter { it.value == Verdict.VALUE_FAVORITE }.map { it.placeId }.toSet()
            wantToGo -> verdictMap.values.filter { it.value == Verdict.VALUE_WANT_TO_GO }.map { it.placeId }.toSet()
            else -> null
        }
        MapUiState(
            pois = filterAndSort(pois, active, loc, verdictIds, query, customNames),
            allPois = pois,
            categories = categories,
            activeCategory = active,
            location = loc,
            verdicts = verdictMap,
            customNames = customNames,
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

    /**
     * Single-select chip toggle: tapping the already-active category clears the filter
     * (shows nothing), tapping a different one switches to it. Mutually exclusive with
     * Favorites/Want to go and search.
     */
    fun selectCategory(category: String) {
        activeCategory.value = if (activeCategory.value == category) null else category
        if (activeCategory.value != null) {
            favoritesOnly.value = false
            wantToGoOnly.value = false
            committedSearchQuery.value = null
        }
    }

    /** Mutually exclusive with the chip row, the other layer row, and search. */
    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
        if (favoritesOnly.value) {
            wantToGoOnly.value = false
            activeCategory.value = null
            committedSearchQuery.value = null
        }
    }

    /** Mutually exclusive with the chip row, the other layer row, and search. */
    fun toggleWantToGoOnly() {
        wantToGoOnly.value = !wantToGoOnly.value
        if (wantToGoOnly.value) {
            favoritesOnly.value = false
            activeCategory.value = null
            committedSearchQuery.value = null
        }
    }

    /** Commits a search overlay query to the map, mutually exclusive with the chip row and the
     *  layer rows. Called both by the overlay's keyboard Search action and by tapping one of
     *  its rows (which also focuses/selects that POI). */
    fun commitSearch(query: String) {
        val trimmed = query.trim()
        committedSearchQuery.value = trimmed.ifBlank { null }
        if (trimmed.isNotBlank()) {
            favoritesOnly.value = false
            wantToGoOnly.value = false
            activeCategory.value = null
        }
    }

    fun clearSearch() {
        committedSearchQuery.value = null
    }

    /** Jumps the map camera to [latLon] — bumps [focusRequest] so [PoiMap]'s LaunchedEffect
     *  re-fires even if the same POI is focused twice in a row. */
    fun focusOn(latLon: LatLon) {
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

    fun selectPoi(poi: PoiWithDistance?) {
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

    fun setCustomName(poiId: String, customName: String?) {
        viewModelScope.launch(Dispatchers.IO) { poiCustomNameDao.setCustomName(poiId, customName) }
    }

    /** Opens the add-POI flow's pin-drop step (the "+" FAB). */
    fun startAddPoi() {
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
        _editingCustomPoi.value = null
        _addressSearchState.value = AddressSearchState()
        _pendingAddressPrefill.value = null
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

    fun saveCustomPoi(customPoi: CustomPoi) {
        viewModelScope.launch(Dispatchers.IO) { customPoiDao.upsert(customPoi) }
        _addPoiStep.value = AddPoiStep.NONE
        _addPoiCenter.value = null
        _editingCustomPoi.value = null
        _pendingAddressPrefill.value = null
    }

    /** Opens the add-POI form pre-filled with [poi]'s current values (issue #47's Edit action,
     *  only ever offered for a `custom_poi`-backed [Poi] — see [isCustomPoiId]). Skips
     *  pin-placement and reuses the place's existing coordinates unchanged: this flow edits the
     *  form fields, not the location. */
    fun startEditCustomPoi(poi: Poi) {
        _editingCustomPoi.value = poi.toCustomPoi()
        _addPoiCenter.value = LatLon(poi.lat, poi.lon)
        _addPoiStep.value = AddPoiStep.FORM
        selectPoi(null)
    }

    /** Optimistic delete (issue #47): [id] disappears from the map/search immediately (see
     *  [excludePendingDelete] above) but its `custom_poi`/Verdict/Visit rows aren't touched until
     *  [commitPendingDelete] actually runs — either the Snackbar timing out/being dismissed, or
     *  [MapScreen] committing on dispose if the user navigates away first. A second delete request
     *  while one is already pending commits the first rather than dropping it silently. */
    fun requestDeleteCustomPoi(id: String) {
        if (_pendingDeleteCustomPoiId.value != null) commitPendingDelete()
        _pendingDeleteCustomPoiId.value = id
        selectPoi(null)
    }

    /** Reverses [requestDeleteCustomPoi] — since nothing was actually deleted yet, this is just
     *  clearing the pending id, which makes [poisVerdictsAndNames] show the place again. */
    fun undoDeleteCustomPoi() {
        _pendingDeleteCustomPoiId.value = null
    }

    /** Actually deletes whatever [requestDeleteCustomPoi] left pending, re-keying nothing (unlike
     *  #49's reimport-time merge) — the place, its Verdict, and every Visit logged against it are
     *  all gone together. No-op if nothing is pending (safe to call unconditionally from
     *  [MapScreen]'s onDispose). Clears the pending id only after the deletes land, so a
     *  DAO Flow emission racing this can't briefly show the place again with nothing pending. */
    fun commitPendingDelete() {
        val id = _pendingDeleteCustomPoiId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            verdictDao.delete(id)
            visitDao.deleteByPlaceId(id)
            customPoiDao.delete(id)
            if (_pendingDeleteCustomPoiId.value == id) _pendingDeleteCustomPoiId.value = null
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
                    app.container.database.poiCustomNameDao(),
                    app.container.database.visitDao(),
                    app.container.database.customPoiDao()
                )
            }
        }
    }
}
