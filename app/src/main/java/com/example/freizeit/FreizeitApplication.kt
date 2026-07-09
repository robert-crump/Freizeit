package com.example.freizeit

import android.app.Application
import com.example.freizeit.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class FreizeitApplication : Application() {

    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)
        // Open the database off the main thread so schema creation
        // happens at startup rather than on first query.
        applicationScope.launch(Dispatchers.IO) {
            container.database.openHelper.writableDatabase
        }
    }
}
