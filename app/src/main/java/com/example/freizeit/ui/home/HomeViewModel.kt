package com.example.freizeit.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.PendingVisitDao
import com.example.freizeit.data.dao.PoiCustomNameDao
import com.example.freizeit.data.dao.PoiDao
import com.example.freizeit.data.dao.VerdictDao
import com.example.freizeit.data.dao.setVerdict
import com.example.freizeit.data.entity.PendingVisit
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.weather.WeatherRepository
import com.example.freizeit.domain.suggestion.Suggestion
import com.example.freizeit.domain.suggestion.SuggestionContext
import com.example.freizeit.domain.suggestion.SuggestionEngine
import com.example.freizeit.domain.weather.WeatherSnapshot
import com.example.freizeit.util.LatLon
import com.example.freizeit.util.LocationHelper
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

/** How long after a "Go" tap the next-open banner is allowed to appear. */
private const val VISIT_BANNER_THRESHOLD_MILLIS = 2 * 60 * 60 * 1000L

/** Pure so the ≥2h gating is unit-testable without touching the ViewModel. */
fun PendingVisit.isReadyForBanner(nowMillis: Long): Boolean =
    nowMillis - wentAt >= VISIT_BANNER_THRESHOLD_MILLIS

data class HomeUiState(
    /** The full ranked swipe deck: every favorite that survives the weather/hours filters. */
    val deck: List<Suggestion> = emptyList(),
    val currentIndex: Int = 0,
    val weather: WeatherSnapshot? = null,
    val location: LatLon? = null,
    /** False only once the POI table is confirmed empty — drives the import hint. */
    val hasPois: Boolean = true,
    /** True if any place has ever been favorited, regardless of today's filters. */
    val hasFavorites: Boolean = false,
    /** Set only once the visit is at least 2h old, per [isReadyForBanner]. */
    val pendingVisit: PendingVisit? = null,
    val verdicts: Map<String, Verdict> = emptyMap(),
    val customNames: Map<String, String> = emptyMap(),
    /** True until the first Room/weather emission lands — drives the loading spinner. */
    val isLoading: Boolean = true
) {
    /** The card on top of the deck right now, looping back to the start past the end. */
    val currentCard: Suggestion?
        get() = if (deck.isEmpty()) null else deck[currentIndex.mod(deck.size)]

    /** The card peeking behind [currentCard], null when there's nothing else to peek at. */
    val nextCard: Suggestion?
        get() = if (deck.size <= 1) null else deck[(currentIndex + 1).mod(deck.size)]
}

class HomeViewModel(
    private val appContext: Context,
    poiDao: PoiDao,
    private val verdictDao: VerdictDao,
    private val pendingVisitDao: PendingVisitDao,
    private val weatherRepository: WeatherRepository,
    poiCustomNameDao: PoiCustomNameDao
) : ViewModel() {

    private val location = MutableStateFlow<LatLon?>(null)

    /**
     * Position in the ranked deck. Swiping advances it; unfavoriting the current card shrinks
     * the deck out from under the same index, which naturally surfaces whatever was next —
     * no separate "auto-advance" step needed.
     */
    private val currentIndex = MutableStateFlow(0)

    /** Favorited pois + whether the (city-wide) poi table has anything at all, plus small side tables. */
    private data class PoiSlice(
        val favoritePois: List<Poi>,
        val hasPois: Boolean,
        val verdicts: Map<String, Verdict>,
        val customNames: Map<String, String>
    )

    private val poisVerdictsAndNames = combine(
        poiDao.observeFavorites(),
        poiDao.observeCount(),
        verdictDao.observeAll(),
        poiCustomNameDao.observeAll()
    ) { favoritePois, poiCount, verdicts, customNames ->
        PoiSlice(
            favoritePois = favoritePois,
            hasPois = poiCount > 0,
            verdicts = verdicts.associateBy { it.placeId },
            customNames = customNames.associate { it.placeId to it.customName }
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        poisVerdictsAndNames,
        weatherRepository.snapshot,
        location,
        currentIndex,
        pendingVisitDao.observe()
    ) { slice, weather, loc, index, pendingVisit ->
        val context = SuggestionContext(
            now = LocalDateTime.now(),
            location = loc,
            weather = weather,
            verdicts = slice.verdicts
        )
        HomeUiState(
            deck = SuggestionEngine.rankAll(slice.favoritePois, context),
            currentIndex = index,
            weather = weather,
            location = loc,
            hasPois = slice.hasPois,
            hasFavorites = slice.favoritePois.isNotEmpty(),
            pendingVisit = pendingVisit?.takeIf { it.isReadyForBanner(System.currentTimeMillis()) },
            verdicts = slice.verdicts,
            customNames = slice.customNames,
            isLoading = false
        )
    }
        .flowOn(Dispatchers.Default)
        // Home's ViewModel outlives tab switches (bottom-nav saveState/restoreState), so stay
        // subscribed instead of dropping Room collection 5s after Home loses its last observer —
        // that cold-restart was the visible 0.5-1s lag when returning from Explore.
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch { weatherRepository.loadCache() }
        refreshLocation()
    }

    /** Swiping either direction advances to the next card; the deck loops past the end. */
    fun advance() {
        currentIndex.value += 1
    }

    fun refreshLocation() {
        viewModelScope.launch {
            val loc = withContext(Dispatchers.IO) {
                LocationHelper.lastKnownLocation(appContext)
            }
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

    /** Tapping "Go" on a card records intent; the banner appears next open, 2h+ later. */
    fun recordGo(poi: Poi, customName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingVisitDao.set(
                PendingVisit(
                    placeId = poi.id,
                    snapshotName = customName ?: poi.name,
                    snapshotCategory = poi.category,
                    snapshotLat = poi.lat,
                    snapshotLon = poi.lon,
                    wentAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** [value] null means "didn't go" — dismisses the banner without recording a verdict. */
    fun resolveVisit(value: String?) {
        val visit = uiState.value.pendingVisit ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (value != null) {
                verdictDao.upsert(
                    Verdict(
                        placeId = visit.placeId,
                        value = value,
                        verdictedAt = System.currentTimeMillis(),
                        snapshotName = visit.snapshotName,
                        snapshotLat = visit.snapshotLat,
                        snapshotLon = visit.snapshotLon,
                        snapshotCategory = visit.snapshotCategory
                    )
                )
            }
            pendingVisitDao.clear()
        }
    }

    companion object {
        // Cologne area, center of the extract coverage — same fallback as the Explore map.
        private const val FALLBACK_LAT = 50.94
        private const val FALLBACK_LON = 6.96

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                HomeViewModel(
                    app,
                    app.container.database.poiDao(),
                    app.container.database.verdictDao(),
                    app.container.database.pendingVisitDao(),
                    app.container.weatherRepository,
                    app.container.database.poiCustomNameDao()
                )
            }
        }
    }
}
