package com.example.freizeit.ui.explore

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.PoiCustomNameDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.dao.setCustomName
import com.example.freizeit.data.dao.setVerdict
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.ui.common.categoryOrderIndex
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon
import com.example.freizeit.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PoiWithDistance(val poi: Poi, val distanceMeters: Double?)

private const val SEARCH_DEBOUNCE_MS = 250L

data class ExploreUiState(
    val pois: List<PoiWithDistance> = emptyList(),
    val categories: List<String> = emptyList(),
    val activeCategories: Set<String> = emptySet(),
    val location: LatLon? = null,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val customNames: Map<String, String> = emptyMap(),
    val favoritesOnly: Boolean = false,
    val wantToGoOnly: Boolean = false,
    val showAllPois: Boolean = false,
    val searchQuery: String = ""
)

/**
 * Pure filter+sort so the semantics are unit-testable. Exactly one filter is ever
 * active, in priority order: a non-blank [searchQuery] matches custom-or-OSM name
 * substrings across all categories; otherwise [verdictIds] (non-null) restricts to
 * whichever single verdict bucket is active — favorites-only or want-to-go-only, the
 * caller decides which set to pass in, never both at once (#31); otherwise [showAll]
 * restricts to pois whose category is in [activeCategories] — a multi-select set (#33),
 * so zero active categories is a valid state that matches nothing; with none of the above
 * active, nothing matches either. With a location, sorts nearest first, otherwise by name
 * (unnamed places last).
 */
fun filterAndSort(
    pois: List<Poi>,
    activeCategories: Set<String>,
    location: LatLon?,
    verdictIds: Set<String>? = null,
    searchQuery: String? = null,
    customNames: Map<String, String> = emptyMap(),
    showAll: Boolean = false
): List<PoiWithDistance> {
    val filtered = pois.filter {
        when {
            !searchQuery.isNullOrBlank() -> {
                val name = customNames[it.id] ?: it.name
                name != null && name.contains(searchQuery, ignoreCase = true)
            }
            verdictIds != null -> it.id in verdictIds
            showAll -> it.category in activeCategories
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

class ExploreViewModel(
    private val appContext: Context,
    poiDao: PoiDao,
    private val verdictDao: VerdictDao,
    private val poiCustomNameDao: PoiCustomNameDao
) : ViewModel() {

    // Only consulted while showAllPois is true — see selectAllPois/toggleCategory. Reset to
    // every known category each time All POIs is turned on (#33).
    private val activeCategories = MutableStateFlow<Set<String>>(emptySet())
    private val location = MutableStateFlow<LatLon?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val wantToGoOnly = MutableStateFlow(false)
    private val showAllPois = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")

    private val _selectedPoi = MutableStateFlow<PoiWithDistance?>(null)
    val selectedPoi: StateFlow<PoiWithDistance?> = _selectedPoi

    private val poisVerdictsAndNames = combine(
        poiDao.observeAll(),
        verdictDao.observeAll(),
        poiCustomNameDao.observeAll()
    ) { pois, verdicts, customNames ->
        Triple(
            pois,
            verdicts.associateBy { it.placeId },
            customNames.associate { it.placeId to it.customName }
        )
    }

    private data class LayerSelection(
        val activeCategories: Set<String>,
        val favoritesOnly: Boolean,
        val wantToGoOnly: Boolean,
        val showAllPois: Boolean
    )

    // Folded into one flow because the outer combine below is already at the stdlib 5-flow
    // arity limit (see #8's note) — activeCategories/favoritesOnly/wantToGoOnly/showAllPois are
    // mutually exclusive anyway (categories are only ever consulted while showAllPois is true,
    // #33), so they travel together.
    private val layerSelection = combine(
        activeCategories, favoritesOnly, wantToGoOnly, showAllPois
    ) { active, favOnly, wantToGo, allPois -> LayerSelection(active, favOnly, wantToGo, allPois) }

    val uiState: StateFlow<ExploreUiState> = combine(
        poisVerdictsAndNames,
        layerSelection,
        location,
        // Debounced so a filter+sort pass (and the map's full overlay rebuild) runs once
        // typing pauses, instead of on every keystroke.
        searchQuery.debounce(SEARCH_DEBOUNCE_MS)
    ) { poisVerdictsNames, layer, loc, query ->
        val (pois, verdictMap, customNames) = poisVerdictsNames
        val (active, favOnly, wantToGo, allPois) = layer
        val categories = pois.map { it.category }.distinct()
            .sortedWith(compareBy({ categoryOrderIndex(it) }, { it }))
        val verdictIds = when {
            favOnly -> verdictMap.values.filter { it.value == Verdict.VALUE_FAVORITE }.map { it.placeId }.toSet()
            wantToGo -> verdictMap.values.filter { it.value == Verdict.VALUE_WANT_TO_GO }.map { it.placeId }.toSet()
            else -> null
        }
        ExploreUiState(
            pois = filterAndSort(pois, active, loc, verdictIds, query.ifBlank { null }, customNames, allPois),
            categories = categories,
            activeCategories = active,
            location = loc,
            verdicts = verdictMap,
            customNames = customNames,
            favoritesOnly = favOnly,
            wantToGoOnly = wantToGo,
            showAllPois = allPois,
            searchQuery = query
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    init {
        refreshLocation()
    }

    /**
     * Independent per-category toggle (#33) — only meaningful while [showAllPois] is on, since
     * the category rows are hidden otherwise. Not mutually exclusive with other categories;
     * zero active categories is a valid state (the map/list simply shows nothing).
     */
    fun toggleCategory(category: String) {
        activeCategories.value = activeCategories.value.let {
            if (category in it) it - category else it + category
        }
    }

    /** Mutually exclusive with the other layer rows and search — see [selectAllPois]. */
    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
        if (favoritesOnly.value) {
            wantToGoOnly.value = false
            showAllPois.value = false
            searchQuery.value = ""
        }
    }

    /** Mutually exclusive with the other layer rows and search — see [selectAllPois]. */
    fun toggleWantToGoOnly() {
        wantToGoOnly.value = !wantToGoOnly.value
        if (wantToGoOnly.value) {
            favoritesOnly.value = false
            showAllPois.value = false
            searchQuery.value = ""
        }
    }

    /**
     * Mutually exclusive with the other layer rows and search. Turning on defaults every known
     * category to active (#33) — re-entering All POIs always resets the per-category selection
     * rather than restoring whatever subset was active last time.
     */
    fun selectAllPois() {
        showAllPois.value = !showAllPois.value
        if (showAllPois.value) {
            favoritesOnly.value = false
            wantToGoOnly.value = false
            searchQuery.value = ""
            activeCategories.value = uiState.value.categories.toSet()
        }
    }

    /** Mutually exclusive with the layer rows — see [selectAllPois]. */
    fun setSearchQuery(query: String) {
        searchQuery.value = query
        if (query.isNotBlank()) {
            favoritesOnly.value = false
            wantToGoOnly.value = false
            showAllPois.value = false
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
    }

    fun refreshLocation() {
        viewModelScope.launch {
            location.value = withContext(Dispatchers.IO) {
                LocationHelper.lastKnownLocation(appContext)
            }
        }
    }

    fun selectPoi(poi: PoiWithDistance?) {
        _selectedPoi.value = poi
    }

    fun setVerdict(poi: Poi, value: String?) {
        viewModelScope.launch(Dispatchers.IO) { verdictDao.setVerdict(poi, value) }
    }

    fun setCustomName(poiId: String, customName: String?) {
        viewModelScope.launch(Dispatchers.IO) { poiCustomNameDao.setCustomName(poiId, customName) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                ExploreViewModel(
                    app,
                    app.container.database.poiDao(),
                    app.container.database.verdictDao(),
                    app.container.database.poiCustomNameDao()
                )
            }
        }
    }
}
