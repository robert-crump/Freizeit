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
    val cards: List<Suggestion> = emptyList(),
    val weather: WeatherSnapshot? = null,
    val location: LatLon? = null,
    /** False only once the POI table is confirmed empty — drives the import hint. */
    val hasPois: Boolean = true,
    /** Set only once the visit is at least 2h old, per [isReadyForBanner]. */
    val pendingVisit: PendingVisit? = null,
    val verdicts: Map<String, Verdict> = emptyMap()
)

class HomeViewModel(
    private val appContext: Context,
    poiDao: PoiDao,
    private val verdictDao: VerdictDao,
    private val pendingVisitDao: PendingVisitDao,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val location = MutableStateFlow<LatLon?>(null)

    /** Card ids already shown this session; reroll excludes them until the pool runs dry. */
    private val rerolledIds = MutableStateFlow<Set<String>>(emptySet())

    private val _selectedCard = MutableStateFlow<Suggestion?>(null)
    val selectedCard: StateFlow<Suggestion?> = _selectedCard

    private val poisAndVerdicts = combine(poiDao.observeAll(), verdictDao.observeAll()) { pois, verdicts ->
        pois to verdicts.associateBy { it.placeId }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        poisAndVerdicts,
        weatherRepository.snapshot,
        location,
        rerolledIds,
        pendingVisitDao.observe()
    ) { (pois, verdictMap), weather, loc, excluded, pendingVisit ->
        val context = SuggestionContext(
            now = LocalDateTime.now(),
            location = loc,
            weather = weather,
            verdicts = verdictMap
        )
        var cards = SuggestionEngine.suggest(pois, context, excludeIds = excluded)
        if (cards.size < 3 && excluded.isNotEmpty()) {
            // Reroll pool exhausted: wrap around to a fresh session.
            cards = SuggestionEngine.suggest(pois, context)
        }
        HomeUiState(
            cards = cards,
            weather = weather,
            location = loc,
            hasPois = pois.isNotEmpty(),
            pendingVisit = pendingVisit?.takeIf { it.isReadyForBanner(System.currentTimeMillis()) },
            verdicts = verdictMap
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { weatherRepository.loadCache() }
        refreshLocation()
    }

    /** Replaces all three cards with next-best, no repeats within the session. */
    fun reroll() {
        val current = uiState.value.cards.map { it.poi.id }
        if (current.isEmpty()) return
        val excluded = rerolledIds.value
        rerolledIds.value = if (excluded.isNotEmpty() && uiState.value.cards.size < 3) {
            current.toSet() // pool was exhausted and wrapped; start the exclusions over
        } else {
            excluded + current
        }
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

    fun selectCard(card: Suggestion?) {
        _selectedCard.value = card
    }

    fun setVerdict(poi: Poi, value: String?) {
        viewModelScope.launch(Dispatchers.IO) { verdictDao.setVerdict(poi, value) }
    }

    /** Tapping "Go" on a card records intent; the banner appears next open, 2h+ later. */
    fun recordGo(poi: Poi) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingVisitDao.set(
                PendingVisit(
                    placeId = poi.id,
                    snapshotName = poi.name,
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
                    app.container.weatherRepository
                )
            }
        }
    }
}
