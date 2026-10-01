package com.example.freizeit.ui.checkin

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
import com.example.freizeit.data.dao.checkIn
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.repository.LocationRepository
import com.example.freizeit.data.repository.observeAllPlaces
import com.example.freizeit.util.GeoDistance
import com.example.freizeit.util.LatLon
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

/** Favorites within this range get top billing over everything else, and are the only ones
 *  listed under "Favorites nearby" (#78). */
const val CHECKIN_FAVORITE_RADIUS_METERS = 500.0

private const val SEARCH_DEBOUNCE_MS = 250L

/** Below this many characters, matches are too broad to be a useful search result — mirrors
 *  Map's SearchOverlay.SEARCH_MIN_LENGTH. Below it, the search screen shows favoritesNearby
 *  quick-picks instead. */
private const val SEARCH_MIN_LENGTH = 2

/** Search shows only the nearest matches at first, expandable via [CheckInUiState.hasMoreSearchResults]. */
const val CHECKIN_SEARCH_RESULTS_LIMIT = 20

data class CheckInCandidate(val poi: Poi, val distanceMeters: Double, val isFavorite: Boolean)

/**
 * Pure so the ranking is unit-testable. Favorites within [CHECKIN_FAVORITE_RADIUS_METERS] come
 * first (sorted by distance), then every other candidate (also by distance) — including
 * farther-out favorites, which don't get top billing but still show up in the ordinary
 * distance-sorted list. Unbounded by distance: a place on the other side of town is still a
 * valid check-in (e.g. checking in at home after forgetting to at the place you actually visited).
 */
fun rankNearbyForCheckIn(
    pois: List<Poi>,
    verdicts: Map<String, Verdict>,
    location: LatLon
): List<CheckInCandidate> {
    val candidates = pois.map { poi ->
        val distance = GeoDistance.metersBetween(location.lat, location.lon, poi.lat, poi.lon)
        CheckInCandidate(poi, distance, verdicts[poi.id]?.value == Verdict.VALUE_FAVORITE)
    }
    val (topBilled, rest) = candidates.partition {
        it.isFavorite && it.distanceMeters <= CHECKIN_FAVORITE_RADIUS_METERS
    }
    return topBilled.sortedBy { it.distanceMeters } + rest.sortedBy { it.distanceMeters }
}

/** The "Favorites nearby" quick picks: favorites within [CHECKIN_FAVORITE_RADIUS_METERS],
 *  nearest first (#78). */
fun favoritesNearby(ranked: List<CheckInCandidate>): List<CheckInCandidate> =
    ranked.filter { it.isFavorite && it.distanceMeters <= CHECKIN_FAVORITE_RADIUS_METERS }
        .sortedBy { it.distanceMeters }

/** Candidates whose effective name (override, else OSM — already applied to [CheckInCandidate.poi]
 *  by `observeAllPlaces`, #73) contains [query]. */
fun matchCheckInSearch(ranked: List<CheckInCandidate>, query: String): List<CheckInCandidate> =
    ranked.filter { it.poi.name?.contains(query, ignoreCase = true) == true }

data class CheckInUiState(
    /** Favorites within [CHECKIN_FAVORITE_RADIUS_METERS], nearest first — the quick-pick list
     *  shown before a search is typed. */
    val favoritesNearby: List<CheckInCandidate> = emptyList(),
    val searchQuery: String = "",
    /**
     * Name matches, distance-unbounded, capped to [CHECKIN_SEARCH_RESULTS_LIMIT] nearest unless
     * [hasMoreSearchResults] has been consumed via [CheckInViewModel.showMoreSearchResults].
     */
    val searchResults: List<CheckInCandidate> = emptyList(),
    /** True when matches beyond the [CHECKIN_SEARCH_RESULTS_LIMIT] cap are being withheld. */
    val hasMoreSearchResults: Boolean = false,
    val hasLocation: Boolean = false
) {
    val isSearching: Boolean get() = searchQuery.trim().length >= SEARCH_MIN_LENGTH
}

private data class SearchState(val query: String, val showAll: Boolean)

class CheckInViewModel(
    private val locationRepository: LocationRepository,
    poiDao: PoiDao,
    customPoiDao: CustomPoiDao,
    poiOverrideDao: PoiOverrideDao,
    verdictDao: VerdictDao,
    private val visitDao: VisitDao
) : ViewModel() {

    /** A one-time snapshot of [LocationRepository.location], taken only inside [refreshLocation]
     *  (construction + resume, mirrors HomeViewModel's identical snapshot-not-collect reasoning
     *  for #40/#34). */
    private val location = MutableStateFlow<LatLon?>(null)
    private val searchQuery = MutableStateFlow("")
    private val showAllSearchResults = MutableStateFlow(false)

    // OSM and custom places with the user's edits applied (#48, #73), so favoritesNearby and
    // search match the same effective names the Map shows.
    private val allPois = observeAllPlaces(poiDao, customPoiDao, poiOverrideDao)

    val uiState: StateFlow<CheckInUiState> = combine(
        allPois,
        verdictDao.observeAll(),
        location,
        combine(
            // Debounced so the nearby-ranking pass runs once typing pauses, instead of on
            // every keystroke (mirrors MapViewModel's identical fix).
            searchQuery.debounce(SEARCH_DEBOUNCE_MS),
            showAllSearchResults,
            ::SearchState
        )
    ) { pois, verdicts, loc, search ->
        val nearby = loc?.let { rankNearbyForCheckIn(pois, verdicts.associateBy { it.placeId }, it) }
            ?: emptyList()
        val trimmedQuery = search.query.trim()
        val matches = if (trimmedQuery.length < SEARCH_MIN_LENGTH) {
            emptyList()
        } else {
            matchCheckInSearch(nearby, trimmedQuery)
        }
        CheckInUiState(
            favoritesNearby = favoritesNearby(nearby),
            searchQuery = search.query,
            searchResults = if (search.showAll) matches else matches.take(CHECKIN_SEARCH_RESULTS_LIMIT),
            hasMoreSearchResults = !search.showAll && matches.size > CHECKIN_SEARCH_RESULTS_LIMIT,
            hasLocation = loc != null
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    init {
        refreshLocation()
    }

    fun refreshLocation() {
        viewModelScope.launch {
            locationRepository.refreshOnce()
            location.value = locationRepository.location.value
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        showAllSearchResults.value = false
    }

    fun clearSearch() {
        searchQuery.value = ""
        showAllSearchResults.value = false
    }

    fun showMoreSearchResults() {
        showAllSearchResults.value = true
    }

    /** Returns the new visit's id, so the caller can offer Undo. */
    suspend fun checkIn(poi: Poi, visitedAt: Long): Long {
        return withContext(Dispatchers.IO) { visitDao.checkIn(poi, visitedAt = visitedAt) }
    }

    suspend fun undoCheckIn(visitId: Long) {
        withContext(Dispatchers.IO) { visitDao.deleteByIds(listOf(visitId)) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                CheckInViewModel(
                    app.container.locationRepository,
                    app.container.database.poiDao(),
                    app.container.database.customPoiDao(),
                    app.container.database.poiOverrideDao(),
                    app.container.database.verdictDao(),
                    app.container.database.visitDao()
                )
            }
        }
    }
}
