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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CheckInHistoryViewModel(visitDao: VisitDao) : ViewModel() {

    val visits: StateFlow<List<Visit>> = visitDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                CheckInHistoryViewModel(app.container.database.visitDao())
            }
        }
    }
}
