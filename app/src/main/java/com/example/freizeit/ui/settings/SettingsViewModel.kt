package com.example.freizeit.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.freizeit.FreizeitApplication
import com.example.freizeit.data.BackupParseException
import com.example.freizeit.data.PoiParseException
import com.example.freizeit.data.dao.CategoryCount
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.data.repository.BackupRepository
import com.example.freizeit.data.repository.PoiRepository
import com.example.freizeit.data.repository.SettingsRepository
import com.example.freizeit.util.MergeCandidate
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

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    data class ExportSuccess(val count: Int) : BackupStatus
    data class ImportSuccess(val count: Int) : BackupStatus
    data class Error(val message: String) : BackupStatus
}

class SettingsViewModel(
    private val poiRepository: PoiRepository,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus

    /** #49: custom POIs the most recent import looks like a duplicate of, queued one at a time —
     *  Settings shows a confirm/dismiss prompt for [List.first] and [confirmMerge]/[dismissMerge]
     *  both pop it off the front, so an import with several candidates walks through them in
     *  sequence rather than needing a stacked-dialog UI. */
    private val _mergeCandidates = MutableStateFlow<List<MergeCandidate>>(emptyList())
    val mergeCandidates: StateFlow<List<MergeCandidate>> = _mergeCandidates

    val suggestionRadiusKm: StateFlow<Int> = settingsRepository.suggestionRadiusKm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_RADIUS_KM)

    fun setSuggestionRadiusKm(radiusKm: Int) {
        viewModelScope.launch { settingsRepository.setSuggestionRadiusKm(radiusKm) }
    }

    val autoCheckInEnabled: StateFlow<Boolean> = settingsRepository.autoCheckInEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutoCheckInEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckInEnabled(enabled) }
    }

    val summary: StateFlow<PoiSummary?> = combine(
        poiRepository.categoryCounts,
        poiRepository.missingCount,
        poiRepository.importInfo
    ) { counts, missing, info ->
        PoiSummary(counts, missing, info)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    fun importPoiFile(uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Importing
            _importStatus.value = try {
                val result = poiRepository.importFrom(uri)
                _mergeCandidates.value = result.mergeCandidates
                ImportStatus.Success(result.count)
            } catch (e: PoiParseException) {
                ImportStatus.Error(e.message ?: "Invalid POI file")
            } catch (e: Exception) {
                ImportStatus.Error("Import failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Re-keys [candidate]'s Visit/Verdict rows onto the matched OSM place and drops the
     *  `custom_poi` row (see [PoiRepository.mergeCustomPoiInto]), then pops it off the queue.
     *  Per the issue's own spec there's no undo here — the confirm step itself is the safety
     *  net, unlike #47's delete-then-Snackbar-undo for an outright delete. */
    fun confirmMerge(candidate: MergeCandidate) {
        viewModelScope.launch {
            poiRepository.mergeCustomPoiInto(candidate.customPoi.id, candidate.poi.id)
            _mergeCandidates.value = _mergeCandidates.value - candidate
        }
    }

    /** Leaves both rows exactly as they are — [candidate] simply isn't a real duplicate. */
    fun dismissMerge(candidate: MergeCandidate) {
        _mergeCandidates.value = _mergeCandidates.value - candidate
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.Working
            _backupStatus.value = try {
                BackupStatus.ExportSuccess(backupRepository.exportTo(uri))
            } catch (e: Exception) {
                BackupStatus.Error("Export failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.Working
            _backupStatus.value = try {
                BackupStatus.ImportSuccess(backupRepository.importFrom(uri))
            } catch (e: BackupParseException) {
                BackupStatus.Error(e.message ?: "Invalid backup file")
            } catch (e: Exception) {
                BackupStatus.Error("Import failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FreizeitApplication
                SettingsViewModel(
                    app.container.poiRepository,
                    app.container.backupRepository,
                    app.container.settingsRepository
                )
            }
        }
    }
}
