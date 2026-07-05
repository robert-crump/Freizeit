package com.example.freizeit.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.PoiParseException
import com.example.freizeit.data.dao.CategoryCount
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.data.repository.PoiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PoiSummary(
    val categoryCounts: List<CategoryCount>,
    val missingCount: Int,
    val importInfo: ImportInfo?
)

sealed interface ImportStatus {
    data object Idle : ImportStatus
    data object Importing : ImportStatus
    data class Success(val count: Int) : ImportStatus
    data class Error(val message: String) : ImportStatus
}

class SettingsViewModel(private val poiRepository: PoiRepository) : ViewModel() {

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus

    val summary: StateFlow<PoiSummary?> = combine(
        poiRepository.categoryCounts,
        poiRepository.missingCount,
        poiRepository.importInfo
    ) { counts, missing, info ->
        PoiSummary(counts, missing, info)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun importPoiFile(uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Importing
            _importStatus.value = try {
                ImportStatus.Success(poiRepository.importFrom(uri))
            } catch (e: PoiParseException) {
                ImportStatus.Error(e.message ?: "Invalid POI file")
            } catch (e: Exception) {
                ImportStatus.Error("Import failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                SettingsViewModel(app.container.poiRepository)
            }
        }
    }
}
