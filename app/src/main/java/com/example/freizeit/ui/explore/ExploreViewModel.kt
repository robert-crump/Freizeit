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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PoiWithDistance(val poi: Poi, val distanceMeters: Double?)

data class ExploreUiState(
    val pois: List<PoiWithDistance> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val location: LatLon? = null,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val customNames: Map<String, String> = emptyMap(),
    val favoritesOnly: Boolean = false,
    val searchQuery: String = ""
)

/**
 * Pure filter+sort so the semantics are unit-testable. Exactly one filter is ever
 * active, in priority order: a non-blank [searchQuery] matches custom-or-OSM name
 * substrings across all categories; otherwise [favoriteIds] (non-null) restricts to
 * favorited places; otherwise [selectedCategory] restricts to that one category; with
 * none set, nothing matches. With a location, sorts nearest first, otherwise by name
 * (unnamed places last).
 */
fun filterAndSort(
    pois: List<Poi>,
    selectedCategory: String?,
    location: LatLon?,
    favoriteIds: Set<String>? = null,
    searchQuery: String? = null,
    customNames: Map<String, String> = emptyMap()
): List<PoiWithDistance> {
    val filtered = pois.filter {
        when {
            !searchQuery.isNullOrBlank() -> {
                val name = customNames[it.id] ?: it.name
                name != null && name.contains(searchQuery, ignoreCase = true)
            }
            favoriteIds != null -> it.id in favoriteIds
            else -> it.category == selectedCategory
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

    // null = no category chosen yet, so the map/list starts empty until the user taps a chip
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val location = MutableStateFlow<LatLon?>(null)
    private val favoritesOnly = MutableStateFlow(false)
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

    val uiState: StateFlow<ExploreUiState> = combine(
        poisVerdictsAndNames,
        selectedCategory,
        location,
        favoritesOnly,
        searchQuery
    ) { poisVerdictsNames, selectedCat, loc, favOnly, query ->
        val (pois, verdictMap, customNames) = poisVerdictsNames
        val categories = pois.map { it.category }.distinct()
            .sortedWith(compareBy({ categoryOrderIndex(it) }, { it }))
        val favoriteIds = if (favOnly) {
            verdictMap.values.filter { it.value == Verdict.VALUE_FAVORITE }.map { it.placeId }.toSet()
        } else {
            null
        }
        ExploreUiState(
            pois = filterAndSort(pois, selectedCat, loc, favoriteIds, query.ifBlank { null }, customNames),
            categories = categories,
            selectedCategory = selectedCat,
            location = loc,
            verdicts = verdictMap,
            customNames = customNames,
            favoritesOnly = favOnly,
            searchQuery = query
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    init {
        refreshLocation()
    }

    /** Category chips are mutually exclusive with "favorites only" and search. */
    fun selectCategory(category: String) {
        selectedCategory.value = if (selectedCategory.value == category) null else category
        if (selectedCategory.value != null) {
            favoritesOnly.value = false
            searchQuery.value = ""
        }
    }

    /** Mutually exclusive with category chips and search — see [selectCategory]. */
    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
        if (favoritesOnly.value) {
            selectedCategory.value = null
            searchQuery.value = ""
        }
    }

    /** Mutually exclusive with category chips and "favorites only" — see [selectCategory]. */
    fun setSearchQuery(query: String) {
        searchQuery.value = query
        if (query.isNotBlank()) {
            selectedCategory.value = null
            favoritesOnly.value = false
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
