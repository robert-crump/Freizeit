package com.example.freizeit.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.geofence.GeofenceLocationMonitor
import com.example.freizeit.data.geofence.GeofenceSyncManager
import com.example.freizeit.data.repository.BackupRepository
import com.example.freizeit.data.repository.GeofenceStateRepository
import com.example.freizeit.data.repository.LocationRepository
import com.example.freizeit.data.repository.PoiRepository
import com.example.freizeit.data.repository.SettingsRepository
import com.example.freizeit.data.weather.WeatherRepository
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manual dependency container. New dependencies (repositories, services)
 * are added here as lazy properties instead of using a DI framework.
 */
class AppContainer(private val context: Context) {

    val database: FreizeitDatabase by lazy {
        FreizeitDatabase.build(context)
    }

    val poiRepository: PoiRepository by lazy {
        PoiRepository(context, database)
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(context)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(context, database)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context.settingsDataStore)
    }

    /** Shared last-known/live location (#40) — see [LocationRepository]'s own doc for why the
     *  three UI ViewModels no longer each read location independently. */
    val locationRepository: LocationRepository by lazy {
        LocationRepository(context)
    }

    val geofenceStateRepository: GeofenceStateRepository by lazy {
        GeofenceStateRepository(context.settingsDataStore)
    }

    val geofenceSyncManager: GeofenceSyncManager by lazy {
        GeofenceSyncManager(context, geofenceStateRepository, database.poiDao())
    }

    /** Drives [GeofenceSyncManager.rerank] on significant location change (issue #29). */
    val geofenceLocationMonitor: GeofenceLocationMonitor by lazy {
        GeofenceLocationMonitor(context) { location ->
            val enabled = settingsRepository.autoCheckInEnabled.first()
            val favorites = database.poiDao().observeFavorites().first()
            geofenceSyncManager.rerank(enabled, favorites, location)
        }
    }
}
