package com.example.freizeit.ui.explore

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
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
    val lovedOnly: Boolean = false
)

/**
 * Pure filter+sort so the semantics are unit-testable. Exactly one filter is ever
 * active: [lovedIds] (non-null) restricts to loved places across all categories,
 * otherwise [selectedCategory] restricts to that one category; with neither set,
 * nothing matches. With a location, sorts nearest first, otherwise by name
 * (unnamed places last).
 */
fun filterAndSort(
    pois: List<Poi>,
    selectedCategory: String?,
    location: LatLon?,
    lovedIds: Set<String>? = null
): List<PoiWithDistance> {
    val filtered = pois.filter {
        if (lovedIds != null) it.id in lovedIds else it.category == selectedCategory
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
    private val verdictDao: VerdictDao
) : ViewModel() {

    // null = no category chosen yet, so the map/list starts empty until the user taps a chip
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val location = MutableStateFlow<LatLon?>(null)
    private val lovedOnly = MutableStateFlow(false)

    private val _selectedPoi = MutableStateFlow<PoiWithDistance?>(null)
    val selectedPoi: StateFlow<PoiWithDistance?> = _selectedPoi

    private val poisAndVerdicts = combine(poiDao.observeAll(), verdictDao.observeAll()) { pois, verdicts ->
        pois to verdicts.associateBy { it.placeId }
    }

    val uiState: StateFlow<ExploreUiState> = combine(
        poisAndVerdicts,
        selectedCategory,
        location,
        lovedOnly
    ) { (pois, verdictMap), selectedCat, loc, loved ->
        val categories = pois.map { it.category }.distinct()
            .sortedWith(compareBy({ categoryOrderIndex(it) }, { it }))
        val lovedIds = if (loved) {
            verdictMap.values.filter { it.value == Verdict.VALUE_LOVE }.map { it.placeId }.toSet()
        } else {
            null
        }
        ExploreUiState(
            pois = filterAndSort(pois, selectedCat, loc, lovedIds),
            categories = categories,
            selectedCategory = selectedCat,
            location = loc,
            verdicts = verdictMap,
            lovedOnly = loved
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    init {
        refreshLocation()
    }

    /** Category chips are mutually exclusive with each other and with "loved only". */
    fun selectCategory(category: String) {
        selectedCategory.value = if (selectedCategory.value == category) null else category
        if (selectedCategory.value != null) lovedOnly.value = false
    }

    /** Mutually exclusive with category chips — see [selectCategory]. */
    fun toggleLovedOnly() {
        lovedOnly.value = !lovedOnly.value
        if (lovedOnly.value) selectedCategory.value = null
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                ExploreViewModel(app, app.container.database.poiDao(), app.container.database.verdictDao())
            }
        }
    }
}
