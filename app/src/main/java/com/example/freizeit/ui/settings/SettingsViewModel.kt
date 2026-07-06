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
import com.example.freizeit.data.entity.Favorite
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.data.repository.BackupRepository
import com.example.freizeit.data.repository.FavoriteRepository
import com.example.freizeit.data.repository.FavoriteResolveException
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

sealed interface FavoriteStatus {
    data object Idle : FavoriteStatus
    data object Saving : FavoriteStatus
    data object Saved : FavoriteStatus
    data class Error(val message: String) : FavoriteStatus
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
    private val favoriteRepository: FavoriteRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus

    val summary: StateFlow<PoiSummary?> = combine(
        poiRepository.categoryCounts,
        poiRepository.missingCount,
        poiRepository.importInfo
    ) { counts, missing, info ->
        PoiSummary(counts, missing, info)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favorites: StateFlow<List<Favorite>> = favoriteRepository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _favoriteStatus = MutableStateFlow<FavoriteStatus>(FavoriteStatus.Idle)
    val favoriteStatus: StateFlow<FavoriteStatus> = _favoriteStatus

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

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

    fun addFavorite(name: String, address: String) {
        viewModelScope.launch {
            _favoriteStatus.value = FavoriteStatus.Saving
            _favoriteStatus.value = try {
                favoriteRepository.add(name, address)
                FavoriteStatus.Saved
            } catch (e: FavoriteResolveException) {
                FavoriteStatus.Error(e.message ?: "Couldn't resolve that address")
            }
        }
    }

    fun updateFavorite(favorite: Favorite, name: String, address: String) {
        viewModelScope.launch {
            _favoriteStatus.value = FavoriteStatus.Saving
            _favoriteStatus.value = try {
                favoriteRepository.update(favorite, name, address)
                FavoriteStatus.Saved
            } catch (e: FavoriteResolveException) {
                FavoriteStatus.Error(e.message ?: "Couldn't resolve that address")
            }
        }
    }

    fun deleteFavorite(favorite: Favorite) {
        viewModelScope.launch { favoriteRepository.delete(favorite) }
    }

    fun clearFavoriteStatus() {
        _favoriteStatus.value = FavoriteStatus.Idle
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
                    app.container.favoriteRepository,
                    app.container.backupRepository
                )
            }
        }
    }
}
