package com.example.freizeit

import android.app.Application
import com.example.freizeit.data.geofence.GeofenceNotifications
import com.example.freizeit.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
        GeofenceNotifications.ensureChannel(this)
        // Open the database off the main thread so schema creation
        // happens at startup rather than on first query.
        applicationScope.launch(Dispatchers.IO) {
            container.database.openHelper.writableDatabase
        }
        // Re-registers Play Services geofences (issue #28) whenever the auto check-in toggle or
        // the favorite list changes, including at process start (geofences don't survive it).
        // Above 100 favorites this replays the last closest-100 selection rather than re-ranking
        // (issue #29) — re-ranking only happens on significant location change, armed/disarmed
        // here alongside the toggle via geofenceLocationMonitor.
        applicationScope.launch(Dispatchers.IO) {
            combine(
                container.settingsRepository.autoCheckInEnabled,
                container.database.poiDao().observeFavorites()
            ) { enabled, favorites -> enabled to favorites }
                .distinctUntilChanged()
                .collect { (enabled, favorites) ->
                    container.geofenceSyncManager.sync(enabled, favorites)
                    if (enabled) {
                        container.geofenceLocationMonitor.start()
                    } else {
                        container.geofenceLocationMonitor.stop()
                    }
                }
        }
    }
}
