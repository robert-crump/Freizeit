package com.example.freizeit.di

import android.content.Context
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.repository.BackupRepository
import com.example.freizeit.data.repository.FavoriteRepository
import com.example.freizeit.data.repository.PoiRepository
import com.example.freizeit.data.weather.WeatherRepository
import com.example.freizeit.util.AndroidAddressResolver

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

    val favoriteRepository: FavoriteRepository by lazy {
        FavoriteRepository(database.favoriteDao(), AndroidAddressResolver(context))
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(context, database)
    }
}
