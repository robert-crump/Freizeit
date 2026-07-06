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
    val selectedCategories: Set<String> = emptySet(),
    val location: LatLon? = null,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val lovedOnly: Boolean = false
)

/**
 * Pure filter+sort so the semantics are unit-testable: keep POIs whose
 * category is selected (and, when [lovedIds] is non-null, whose id is in it);
 * with a location, sort nearest first, otherwise sort by name (unnamed places last).
 */
fun filterAndSort(
    pois: List<Poi>,
    selected: Set<String>,
    location: LatLon?,
    lovedIds: Set<String>? = null
): List<PoiWithDistance> {
    val filtered = pois.filter { it.category in selected && (lovedIds == null || it.id in lovedIds) }
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

    // null = "everything selected", so newly imported categories are visible by default
    private val selectedOverride = MutableStateFlow<Set<String>?>(null)
    private val location = MutableStateFlow<LatLon?>(null)
    private val lovedOnly = MutableStateFlow(false)

    private val _selectedPoi = MutableStateFlow<PoiWithDistance?>(null)
    val selectedPoi: StateFlow<PoiWithDistance?> = _selectedPoi

    private val poisAndVerdicts = combine(poiDao.observeAll(), verdictDao.observeAll()) { pois, verdicts ->
        pois to verdicts.associateBy { it.placeId }
    }

    val uiState: StateFlow<ExploreUiState> = combine(
        poisAndVerdicts,
        selectedOverride,
        location,
        lovedOnly
    ) { (pois, verdictMap), override, loc, loved ->
        val categories = pois.map { it.category }.distinct()
            .sortedWith(compareBy({ categoryOrderIndex(it) }, { it }))
        val selected = override ?: categories.toSet()
        val lovedIds = if (loved) {
            verdictMap.values.filter { it.value == Verdict.VALUE_LOVE }.map { it.placeId }.toSet()
        } else {
            null
        }
        ExploreUiState(
            pois = filterAndSort(pois, selected, loc, lovedIds),
            categories = categories,
            selectedCategories = selected,
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

    fun toggleCategory(category: String) {
        val current = selectedOverride.value ?: uiState.value.categories.toSet()
        selectedOverride.value =
            if (category in current) current - category else current + category
    }

    fun toggleLovedOnly() {
        lovedOnly.value = !lovedOnly.value
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
