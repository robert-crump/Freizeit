package com.example.freizeit.di

import android.content.Context
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.repository.PoiRepository

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
}
