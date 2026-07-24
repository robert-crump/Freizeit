package com.example.freizeit.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.repository.BackupRepository
import com.example.freizeit.data.repository.PoiRepository
import com.example.freizeit.data.repository.SettingsRepository
import com.example.freizeit.data.weather.WeatherRepository

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
}
