package com.example.freizeit.ui.checkin

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
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.dao.checkIn
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
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

/** Favorites within this range get top billing over everything else. */
const val CHECKIN_FAVORITE_RADIUS_METERS = 200.0

/** Nothing farther than this is worth walking to for a check-in. */
const val CHECKIN_MAX_RADIUS_METERS = 500.0

private const val SEARCH_DEBOUNCE_MS = 250L

data class CheckInCandidate(val poi: Poi, val distanceMeters: Double, val isFavorite: Boolean)

/**
 * Pure so the ranking is unit-testable. Favorites within [CHECKIN_FAVORITE_RADIUS_METERS] come
 * first (sorted by distance), then every other candidate within [CHECKIN_MAX_RADIUS_METERS]
 * (also by distance) — including farther-out favorites, which don't get top billing but still
 * show up in the ordinary distance-sorted list.
 */
fun rankNearbyForCheckIn(
    pois: List<Poi>,
    verdicts: Map<String, Verdict>,
    location: LatLon
): List<CheckInCandidate> {
    val candidates = pois.mapNotNull { poi ->
        val distance = GeoDistance.metersBetween(location.lat, location.lon, poi.lat, poi.lon)
        if (distance > CHECKIN_MAX_RADIUS_METERS) return@mapNotNull null
        CheckInCandidate(poi, distance, verdicts[poi.id]?.value == Verdict.VALUE_FAVORITE)
    }
    val (topBilled, rest) = candidates.partition {
        it.isFavorite && it.distanceMeters <= CHECKIN_FAVORITE_RADIUS_METERS
    }
    return topBilled.sortedBy { it.distanceMeters } + rest.sortedBy { it.distanceMeters }
}

data class CheckInUiState(
    /** Favorites within [CHECKIN_FAVORITE_RADIUS_METERS], shown by default. */
    val favoritesNearby: List<CheckInCandidate> = emptyList(),
    val searchQuery: String = "",
    /** Matches (favorite or not) within [CHECKIN_MAX_RADIUS_METERS], populated only while searching. */
    val searchResults: List<CheckInCandidate> = emptyList(),
    val hasLocation: Boolean = false,
    /** Name of the place last checked into this session, shown as a brief confirmation. */
    val lastCheckedInName: String? = null
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()
}

class CheckInViewModel(
    private val appContext: Context,
    poiDao: PoiDao,
    verdictDao: VerdictDao,
    private val visitDao: VisitDao
) : ViewModel() {

    private val location = MutableStateFlow<LatLon?>(null)
    private val lastCheckedInName = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<CheckInUiState> = combine(
        poiDao.observeAll(),
        verdictDao.observeAll(),
        location,
        lastCheckedInName,
        // Debounced so the nearby-ranking pass runs once typing pauses, instead of on
        // every keystroke (mirrors MapViewModel's identical fix).
        searchQuery.debounce(SEARCH_DEBOUNCE_MS)
    ) { pois, verdicts, loc, lastName, query ->
        val nearby = loc?.let { rankNearbyForCheckIn(pois, verdicts.associateBy { it.placeId }, it) }
            ?: emptyList()
        val trimmedQuery = query.trim()
        CheckInUiState(
            favoritesNearby = nearby.filter {
                it.isFavorite && it.distanceMeters <= CHECKIN_FAVORITE_RADIUS_METERS
            },
            searchQuery = query,
            searchResults = if (trimmedQuery.isEmpty()) {
                emptyList()
            } else {
                nearby.filter { it.poi.name?.contains(trimmedQuery, ignoreCase = true) == true }
            },
            hasLocation = loc != null,
            lastCheckedInName = lastName
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    init {
        refreshLocation()
    }

    fun refreshLocation() {
        viewModelScope.launch {
            location.value = withContext(Dispatchers.IO) {
                LocationHelper.lastKnownLocation(appContext)
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearch() {
        searchQuery.value = ""
    }

    /** Returns the new visit's id, so the caller can offer Undo. */
    suspend fun checkIn(poi: Poi, visitedAt: Long): Long {
        val id = withContext(Dispatchers.IO) { visitDao.checkIn(poi, visitedAt = visitedAt) }
        lastCheckedInName.value = poi.name
        return id
    }

    suspend fun undoCheckIn(visitId: Long) {
        withContext(Dispatchers.IO) { visitDao.deleteByIds(listOf(visitId)) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                CheckInViewModel(
                    app,
                    app.container.database.poiDao(),
                    app.container.database.verdictDao(),
                    app.container.database.visitDao()
                )
            }
        }
    }
}
