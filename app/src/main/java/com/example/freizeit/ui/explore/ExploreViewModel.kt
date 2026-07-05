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
import com.example.freizeit.data.entity.Poi
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
    val selectedCategories: Set<String> = emptySet(),
    val location: LatLon? = null
)

/**
 * Pure filter+sort so the semantics are unit-testable: keep POIs whose
 * category is selected; with a location, sort nearest first, otherwise
 * sort by name (unnamed places last).
 */
fun filterAndSort(
    pois: List<Poi>,
    selected: Set<String>,
    location: LatLon?
): List<PoiWithDistance> {
    val filtered = pois.filter { it.category in selected }
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
    poiDao: PoiDao
) : ViewModel() {

    // null = "everything selected", so newly imported categories are visible by default
    private val selectedOverride = MutableStateFlow<Set<String>?>(null)
    private val location = MutableStateFlow<LatLon?>(null)

    private val _selectedPoi = MutableStateFlow<PoiWithDistance?>(null)
    val selectedPoi: StateFlow<PoiWithDistance?> = _selectedPoi

    val uiState: StateFlow<ExploreUiState> = combine(
        poiDao.observeAll(),
        selectedOverride,
        location
    ) { pois, override, loc ->
        val categories = pois.map { it.category }.distinct()
            .sortedWith(compareBy({ categoryOrderIndex(it) }, { it }))
        val selected = override ?: categories.toSet()
        ExploreUiState(
            pois = filterAndSort(pois, selected, loc),
            categories = categories,
            selectedCategories = selected,
            location = loc
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    init {
        refreshLocation()
    }

    fun toggleCategory(category: String) {
        val current = selectedOverride.value ?: uiState.value.categories.toSet()
        selectedOverride.value =
            if (category in current) current - category else current + category
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                ExploreViewModel(app, app.container.database.poiDao())
            }
        }
    }
}
