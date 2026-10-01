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
import com.example.freizeit.data.repository.ThemeMode
import com.example.freizeit.util.MergeCandidate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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

/** A finished backup export/import, shown once as a snackbar (#74). */
sealed interface BackupResult {
    data class ExportSuccess(val count: Int) : BackupResult
    data class ImportSuccess(val count: Int) : BackupResult
    data class Error(val message: String) : BackupResult
}

/** The Suggestion radius dialog's choices (#74). */
val SUGGESTION_RADIUS_PRESETS_KM = listOf(5, 10, 20, 30, 40, 60, 80, 100)

/** The presets plus [currentKm] if it's a value saved before the presets existed, in order. */
fun suggestionRadiusOptions(currentKm: Int): List<Int> =
    (SUGGESTION_RADIUS_PRESETS_KM + currentKm).distinct().sorted()

/** The export's suggested file name, e.g. `261001-freizeit-backup.json` (#74). */
fun backupFileName(date: LocalDate): String =
    "${date.format(DateTimeFormatter.ofPattern("yyMMdd"))}-freizeit-backup.json"

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

    val notifyFavorites: StateFlow<Boolean> = settingsRepository.notifyFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setNotifyFavorites(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyFavorites(enabled) }
    }

    val notifyWantToGo: StateFlow<Boolean> = settingsRepository.notifyWantToGo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setNotifyWantToGo(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyWantToGo(enabled) }
    }

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    val summary: StateFlow<PoiSummary?> = combine(
        poiRepository.categoryCounts,
        poiRepository.missingCount,
        poiRepository.importInfo
    ) { counts, missing, info ->
        PoiSummary(counts, missing, info)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _backupResults = Channel<BackupResult>(Channel.BUFFERED)
    val backupResults: Flow<BackupResult> = _backupResults.receiveAsFlow()

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
            _backupResults.send(
                try {
                    BackupResult.ExportSuccess(backupRepository.exportTo(uri))
                } catch (e: Exception) {
                    BackupResult.Error("Export failed: ${e.message ?: e.javaClass.simpleName}")
                }
            )
        }
    }

    /** A full replace — Settings asks "Replace all your data?" before picking the file. The
     *  geofences and the theme follow the restored data on their own (both observe it). */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupResults.send(
                try {
                    BackupResult.ImportSuccess(backupRepository.importFrom(uri))
                } catch (e: BackupParseException) {
                    BackupResult.Error(e.message ?: "Invalid backup file")
                } catch (e: Exception) {
                    BackupResult.Error("Import failed: ${e.message ?: e.javaClass.simpleName}")
                }
            )
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
