package com.example.freizeit.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.dao.VisitDao
import com.example.freizeit.data.entity.Visit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckInHistoryUiState(
    val visits: List<Visit> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    /** Non-zero right after a delete, until the Undo snackbar is dismissed or acted on. */
    val undoableDeleteCount: Int = 0
) {
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

class CheckInHistoryViewModel(private val visitDao: VisitDao) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val undoableDeleteCount = MutableStateFlow(0)
    private var lastDeleted: List<Visit> = emptyList()

    val uiState: StateFlow<CheckInHistoryUiState> = combine(
        visitDao.observeAll(),
        selectedIds,
        undoableDeleteCount
    ) { visits, ids, undoCount ->
        CheckInHistoryUiState(
            visits = visits,
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
                CheckInHistoryViewModel(app.container.database.visitDao())
            }
        }
    }
}
