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
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.data.entity.PoiOverride
import com.example.freizeit.data.entity.Visit
import com.example.freizeit.data.entity.toPoi
import com.example.freizeit.data.entity.withOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The current effective place (override → custom place → OSM, #73) behind each of [visitedIds],
 * so a history row shows the place's name as it is now. Ids whose place no longer exists are left
 * out; their rows fall back to the visit's snapshot name. A hidden place still counts as
 * existing here: its visits stay in the history under its current name.
 */
fun historyPlaces(
    visitedIds: Set<String>,
    pois: List<Poi>,
    customPois: List<CustomPoi>,
    overrides: List<PoiOverride>
): Map<String, Poi> {
    val overridesById = overrides.associateBy { it.placeId }
    return (pois.asSequence() + customPois.asSequence().map { it.toPoi() })
        .filter { it.id in visitedIds }
        .associate { it.id to it.withOverride(overridesById[it.id]) }
}

data class CheckInHistoryUiState(
    val visits: List<Visit> = emptyList(),
    /** See [historyPlaces]. */
    val places: Map<String, Poi> = emptyMap(),
    val selectedIds: Set<Long> = emptySet(),
    /** Non-zero right after a delete, until the Undo snackbar is dismissed or acted on. */
    val undoableDeleteCount: Int = 0
) {
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

class CheckInHistoryViewModel(
    private val visitDao: VisitDao,
    poiDao: PoiDao,
    customPoiDao: CustomPoiDao,
    poiOverrideDao: PoiOverrideDao
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val undoableDeleteCount = MutableStateFlow(0)
    private var lastDeleted: List<Visit> = emptyList()

    private val visitsAndPlaces = combine(
        visitDao.observeAll(),
        poiDao.observeAll(),
        customPoiDao.observeAll(),
        poiOverrideDao.observeAll()
    ) { visits, pois, customPois, overrides ->
        visits to historyPlaces(visits.mapTo(HashSet()) { it.placeId }, pois, customPois, overrides)
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<CheckInHistoryUiState> = combine(
        visitsAndPlaces,
        selectedIds,
        undoableDeleteCount
    ) { (visits, places), ids, undoCount ->
        CheckInHistoryUiState(
            visits = visits,
            places = places,
            selectedIds = ids.intersect(visits.map { it.id }.toSet()),
            undoableDeleteCount = undoCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInHistoryUiState())

    fun startSelecting(id: Long) {
        selectedIds.value = setOf(id)
    }

    fun toggleSelected(id: Long) {
        selectedIds.update { current -> if (id in current) current - id else current + id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        val toDelete = uiState.value.visits.filter { it.id in ids }
        selectedIds.value = emptySet()
        lastDeleted = toDelete
        undoableDeleteCount.value = toDelete.size
        viewModelScope.launch(Dispatchers.IO) {
            visitDao.deleteByIds(ids.toList())
        }
    }

    fun undoDelete() {
        val toRestore = lastDeleted
        if (toRestore.isEmpty()) return
        lastDeleted = emptyList()
        undoableDeleteCount.value = 0
        viewModelScope.launch(Dispatchers.IO) {
            visitDao.insertAll(toRestore)
        }
    }

    fun dismissUndo() {
        lastDeleted = emptyList()
        undoableDeleteCount.value = 0
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                CheckInHistoryViewModel(
                    app.container.database.visitDao(),
                    app.container.database.poiDao(),
                    app.container.database.customPoiDao(),
                    app.container.database.poiOverrideDao()
                )
            }
        }
    }
}
