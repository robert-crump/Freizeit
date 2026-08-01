package com.example.freizeit.ui.home

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
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.dao.checkIn
import com.example.freizeit.data.dao.setVerdict
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.repository.LocationRepository
import com.example.freizeit.data.repository.SettingsRepository
import com.example.freizeit.data.weather.WeatherRepository
import com.example.freizeit.domain.suggestion.Suggestion
import com.example.freizeit.domain.suggestion.SuggestionContext
import com.example.freizeit.domain.suggestion.SuggestionEngine
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.util.LatLon
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    /** The full ranked swipe deck: every favorite or want-to-go place within radius, merged
     *  and ranked on equal footing (#31), closed/rainy ones flagged with a warning rather
     *  than dropped. Position within it is tracked locally by the swipe deck UI, not here —
     *  see SwipeableSuggestionCard's localIndex — so that paging through it never waits on
     *  a Room/combine round trip. */
    val deck: List<Suggestion> = emptyList(),
    val weather: WeatherSnapshot? = null,
    val location: LatLon? = null,
    /** False only once the POI table is confirmed empty — drives the import hint. */
    val hasPois: Boolean = true,
    /** True if any place has ever been favorited or want-to-go'd, regardless of today's filters. */
    val hasVerdictedPlaces: Boolean = false,
    /** False only when there ARE favorites/want-to-go places but none within [radiusKm] — see
     *  issue #21. Always true while location is unknown, since distance can't be judged
     *  against a guess. */
    val hasVerdictedPlacesWithinRadius: Boolean = true,
    /** The configured suggestion radius, for the "no suggestions within X km" hint. */
    val radiusKm: Int = SettingsRepository.DEFAULT_RADIUS_KM,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val customNames: Map<String, String> = emptyMap(),
    /** True until the first Room/weather emission lands — drives the loading spinner. */
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val locationRepository: LocationRepository,
    poiDao: PoiDao,
    private val verdictDao: VerdictDao,
    private val weatherRepository: WeatherRepository,
    poiCustomNameDao: PoiCustomNameDao,
    private val visitDao: VisitDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** A one-time snapshot of [LocationRepository.location], taken only inside [refreshLocation]
     *  (construction + every resume, see HomeScreen's ON_RESUME observer) — deliberately NOT a
     *  continuous collection of the shared flow, so re-filtering/re-ranking the deck and
     *  re-centering the mini-map only ever happens at a resume boundary. Since Map's continuous
     *  stream (#40) writes into that same shared flow while Map is open, collecting it live here
     *  would let a Map-only location update reshuffle Home's deck mid-swipe — exactly what #34
     *  ruled out. */
    private val location = MutableStateFlow<LatLon?>(null)

    /** Favorited + want-to-go pois + whether the (city-wide) poi table has anything at all,
     *  plus small side tables. */
    private data class PoiSlice(
        val candidatePois: List<Poi>,
        val hasPois: Boolean,
        val verdicts: Map<String, Verdict>,
        val customNames: Map<String, String>,
        val visits: Map<String, List<Long>>
    )

    private val poisVerdictsAndNames = combine(
        poiDao.observeByVerdictValues(listOf(Verdict.VALUE_FAVORITE, Verdict.VALUE_WANT_TO_GO)),
        poiDao.observeCount(),
        verdictDao.observeAll(),
        poiCustomNameDao.observeAll(),
        visitDao.observeAll()
    ) { candidatePois, poiCount, verdicts, customNames, visits ->
        PoiSlice(
            candidatePois = candidatePois,
            hasPois = poiCount > 0,
            verdicts = verdicts.associateBy { it.placeId },
            customNames = customNames.associate { it.placeId to it.customName },
            visits = visits.groupBy({ it.placeId }, { it.visitedAt })
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        poisVerdictsAndNames,
        weatherRepository.snapshot,
        location,
        settingsRepository.suggestionRadiusKm
    ) { slice, weather, loc, radiusKm ->
        val context = SuggestionContext(
            now = LocalDateTime.now(),
            location = loc,
            weather = weather,
            verdicts = slice.verdicts,
            visits = slice.visits
        )
        val candidatesInRange = SuggestionEngine.withinRadius(slice.candidatePois, loc, radiusKm * 1000.0)
        HomeUiState(
            deck = SuggestionEngine.rankAll(candidatesInRange, context),
            weather = weather,
            location = loc,
            hasPois = slice.hasPois,
            hasVerdictedPlaces = slice.candidatePois.isNotEmpty(),
            hasVerdictedPlacesWithinRadius = slice.candidatePois.isEmpty() || candidatesInRange.isNotEmpty(),
            radiusKm = radiusKm,
            verdicts = slice.verdicts,
            customNames = slice.customNames,
            isLoading = false
        )
    }
        .flowOn(Dispatchers.Default)
        // Home's ViewModel outlives tab switches (bottom-nav saveState/restoreState), so stay
        // subscribed instead of dropping Room collection 5s after Home loses its last observer —
        // that cold-restart was the visible 0.5-1s lag when returning from Map.
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch { weatherRepository.loadCache() }
        refreshLocation()
    }

    fun refreshLocation() {
        viewModelScope.launch {
            locationRepository.refreshOnce()
            val loc = locationRepository.location.value
            location.value = loc
            weatherRepository.refresh(
                lat = loc?.lat ?: FALLBACK_LAT,
                lon = loc?.lon ?: FALLBACK_LON
            )
        }
    }

    fun setVerdict(poi: Poi, value: String?) {
        viewModelScope.launch(Dispatchers.IO) { verdictDao.setVerdict(poi, value) }
    }

    /** Tapping "Check-in" on a card records a visit at the picked date/time, same as the
     *  Check-in tab. Returns the new visit's id, so the caller can offer Undo. */
    suspend fun checkIn(poi: Poi, visitedAt: Long): Long =
        withContext(Dispatchers.IO) { visitDao.checkIn(poi, visitedAt = visitedAt) }

    suspend fun undoCheckIn(visitId: Long) {
        withContext(Dispatchers.IO) { visitDao.deleteByIds(listOf(visitId)) }
    }

    companion object {
        // Aachen, center of the POI extraction bbox — same fallback as the Map screen's map
        // (issue #22: the old constant was labeled "Cologne" and pointed there, ~60km
        // outside the actual extraction bbox set by tools/poi_extraction, Aachen +/-20km).
        private const val FALLBACK_LAT = 50.7753
        private const val FALLBACK_LON = 6.0839

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                HomeViewModel(
                    app.container.locationRepository,
                    app.container.database.poiDao(),
                    app.container.database.verdictDao(),
                    app.container.weatherRepository,
                    app.container.database.poiCustomNameDao(),
                    app.container.database.visitDao(),
                    app.container.settingsRepository
                )
            }
        }
    }
}
