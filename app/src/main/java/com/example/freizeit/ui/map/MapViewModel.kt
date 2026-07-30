package com.example.freizeit.ui.map

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
import com.example.freizeit.ui.common.categoryDisplayName
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

data class MapUiState(
    val pois: List<PoiWithDistance> = emptyList(),
    val categories: List<String> = emptyList(),
    val activeCategory: String? = null,
    val location: LatLon? = null,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val customNames: Map<String, String> = emptyMap(),
    val favoritesOnly: Boolean = false,
    val wantToGoOnly: Boolean = false,
    val searchQuery: String = ""
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
    private val appContext: Context,
    poiDao: PoiDao,
    private val verdictDao: VerdictDao,
    private val poiCustomNameDao: PoiCustomNameDao
) : ViewModel() {

    // Single-select category chip (replaces #33's multi-select "All POIs" mode) — mutually
    // exclusive with favoritesOnly/wantToGoOnly/searchQuery, see selectCategory.
    private val activeCategory = MutableStateFlow<String?>(null)
    private val location = MutableStateFlow<LatLon?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val wantToGoOnly = MutableStateFlow(false)
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
        location,
        // Debounced so a filter+sort pass (and the map's full overlay rebuild) runs once
        // typing pauses, instead of on every keystroke.
        searchQuery.debounce(SEARCH_DEBOUNCE_MS)
    ) { poisVerdictsNames, layer, loc, query ->
        val (pois, verdictMap, customNames) = poisVerdictsNames
        val (active, favOnly, wantToGo) = layer
        val categories = pois.map { it.category }.distinct().sortedBy { categoryDisplayName(it) }
        val verdictIds = when {
            favOnly -> verdictMap.values.filter { it.value == Verdict.VALUE_FAVORITE }.map { it.placeId }.toSet()
            wantToGo -> verdictMap.values.filter { it.value == Verdict.VALUE_WANT_TO_GO }.map { it.placeId }.toSet()
            else -> null
        }
        MapUiState(
            pois = filterAndSort(pois, active, loc, verdictIds, query.ifBlank { null }, customNames),
            categories = categories,
            activeCategory = active,
            location = loc,
            verdicts = verdictMap,
            customNames = customNames,
            favoritesOnly = favOnly,
            wantToGoOnly = wantToGo,
            searchQuery = query
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
            searchQuery.value = ""
        }
    }

    /** Mutually exclusive with the chip row, the other layer row, and search. */
    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
        if (favoritesOnly.value) {
            wantToGoOnly.value = false
            activeCategory.value = null
            searchQuery.value = ""
        }
    }

    /** Mutually exclusive with the chip row, the other layer row, and search. */
    fun toggleWantToGoOnly() {
        wantToGoOnly.value = !wantToGoOnly.value
        if (wantToGoOnly.value) {
            favoritesOnly.value = false
            activeCategory.value = null
            searchQuery.value = ""
        }
    }

    /** Mutually exclusive with the chip row and the layer rows. */
    fun setSearchQuery(query: String) {
        searchQuery.value = query
        if (query.isNotBlank()) {
            favoritesOnly.value = false
            wantToGoOnly.value = false
            activeCategory.value = null
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
                MapViewModel(
                    app,
                    app.container.database.poiDao(),
                    app.container.database.verdictDao(),
                    app.container.database.poiCustomNameDao()
                )
            }
        }
    }
}
